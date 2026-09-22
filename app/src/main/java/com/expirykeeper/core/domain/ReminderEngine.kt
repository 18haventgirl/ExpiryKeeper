package com.expirykeeper.core.domain

import com.expirykeeper.core.data.Item
import com.expirykeeper.core.data.ReminderKind
import java.time.LocalDate

enum class DueStatus { DUE_SOON, DUE_TODAY, OVERDUE, LOW_STOCK, RENEWAL_SOON, RENEWAL_TODAY }

/** 到期状态的中文标签：列表 / 详情 / 通知共用（Task 12 前置：自 UI 私有扩展上收） */
val DueStatus.labelZh: String
    get() = when (this) {
        DueStatus.OVERDUE -> "逾期"
        DueStatus.DUE_TODAY -> "今天到期"
        DueStatus.DUE_SOON -> "即将到期"
        DueStatus.LOW_STOCK -> "库存低"
        DueStatus.RENEWAL_SOON -> "即将续费"
        DueStatus.RENEWAL_TODAY -> "今天续费"
    }

data class Reminder(
    val item: Item,
    val status: DueStatus,
    /** 距到期天数（OVERDUE 时为负数已转绝对值另计）；LOW_STOCK 为 null */
    val daysLeft: Long?,
    val overdueDays: Long,
    val notificationId: Int,
)

/** 纯函数规则引擎：不依赖 Android，输入物品快照 + 今天，输出今天应发的提醒 */
object ReminderEngine {

    fun computeForDate(items: List<Item>, today: LocalDate): List<Reminder> =
        items.filter { it.deletedAt == null }.mapNotNull { computeOne(it, today) }

    fun computeOne(item: Item, today: LocalDate): Reminder? {
        item.snoozedUntilEpochDay?.let { if (today.toEpochDay() <= it) return null }
        val reminder = when (item.reminderKind) {
            ReminderKind.EXPIRY -> expiry(item, today)
            ReminderKind.CONSUMABLE -> consumable(item)
            ReminderKind.RECURRING -> recurring(item, today)
        }
        if (reminder != null && item.handledAtEpochDay == today.toEpochDay() &&
            item.handledStatus == reminder.status.name) return null
        return reminder
    }

    /** 到期日 = 直接填写的过期日；否则由 开封日(或录入日)+保质期天数 推导 */
    fun effectiveExpireDay(item: Item): Long? = item.expireAtEpochDay
        ?: item.shelfLifeDays?.let { (item.openedAtEpochDay ?: (item.createdAt / 86_400_000L)) + it }

    /** 物品级 emoji 优先，回退品类图标；UI 与通知共用 */
    fun displayIcon(item: Item, categoryEmoji: String): String = item.emoji ?: categoryEmoji

    private fun expiry(item: Item, today: LocalDate): Reminder? {
        val expire = effectiveExpireDay(item) ?: return null
        val daysLeft = expire - today.toEpochDay()
        val status = when {
            daysLeft < 0 -> DueStatus.OVERDUE
            daysLeft == 0L -> DueStatus.DUE_TODAY
            item.reminderOffsetsDays.any { off -> daysLeft == off.toLong() } -> DueStatus.DUE_SOON
            else -> return null
        }
        return Reminder(item, status, daysLeft.coerceAtLeast(0), (-daysLeft).coerceAtLeast(0), notifIdFor(item.id, status, today))
    }

    private fun consumable(item: Item): Reminder? {
        val qty = item.quantity ?: return null
        val threshold = item.lowStockThreshold ?: return null
        if (qty > threshold) return null
        return Reminder(item, DueStatus.LOW_STOCK, null, 0, notifIdFor(item.id, DueStatus.LOW_STOCK, null))
    }

    private fun recurring(item: Item, today: LocalDate): Reminder? {
        val nextDue = item.nextDueAtEpochDay ?: return null
        val daysLeft = nextDue - today.toEpochDay()
        val status = when {
            daysLeft == 0L -> DueStatus.RENEWAL_TODAY
            daysLeft in 1..7 && item.reminderOffsetsDays.any { off -> daysLeft == off.toLong() } -> DueStatus.RENEWAL_SOON
            else -> return null
        }
        return Reminder(item, status, daysLeft, 0, notifIdFor(item.id, status, today))
    }

    /** 今日 + 未来 withinDays 内的到期摘要（"今日"屏排序用） */
    fun upcoming(items: List<Item>, today: LocalDate, withinDays: Long = 14): List<Pair<Item, Long>> =
        items.filter { it.deletedAt == null }
            .mapNotNull { item ->
                val epoch = when (item.reminderKind) {
                    // Task 4 carry-over：EXPIRY 走派生到期日（开封+保质期），不能只读 raw 字段
                    ReminderKind.EXPIRY -> effectiveExpireDay(item)
                    ReminderKind.RECURRING -> item.nextDueAtEpochDay
                    ReminderKind.CONSUMABLE -> null
                } ?: return@mapNotNull null
                item to (epoch - today.toEpochDay())
            }
            .filter { it.second <= withinDays }
            .sortedBy { it.second }

    /** 通知 id 纯函数：UI 取消既有通知与引擎发通知共用同一算法，LOW_STOCK 传 date=null */
    fun notifIdFor(itemId: String, status: DueStatus, date: LocalDate?): Int =
        "$itemId|${status.name}|${date?.toEpochDay() ?: "static"}".hashCode() and 0x7FFFFFFF
}
