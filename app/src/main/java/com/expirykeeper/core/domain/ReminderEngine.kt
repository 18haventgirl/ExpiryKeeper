package com.expirykeeper.core.domain

import com.expirykeeper.core.data.Item
import com.expirykeeper.core.data.ReminderKind
import java.time.LocalDate

enum class DueStatus { DUE_SOON, DUE_TODAY, OVERDUE, LOW_STOCK, RENEWAL_SOON, RENEWAL_TODAY }

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

    fun computeOne(item: Item, today: LocalDate): Reminder? = when (item.reminderKind) {
        ReminderKind.EXPIRY -> expiry(item, today)
        ReminderKind.CONSUMABLE -> consumable(item)
        ReminderKind.RECURRING -> recurring(item, today)
    }

    private fun expiry(item: Item, today: LocalDate): Reminder? {
        val expire = item.expireAtEpochDay ?: return null
        val daysLeft = expire - today.toEpochDay()
        val status = when {
            daysLeft < 0 -> DueStatus.OVERDUE
            daysLeft == 0L -> DueStatus.DUE_TODAY
            item.reminderOffsetsDays.any { off -> daysLeft == off.toLong() } -> DueStatus.DUE_SOON
            else -> return null
        }
        return Reminder(item, status, daysLeft.coerceAtLeast(0), (-daysLeft).coerceAtLeast(0), notifId(item.id, status, today))
    }

    private fun consumable(item: Item): Reminder? {
        val qty = item.quantity ?: return null
        val threshold = item.lowStockThreshold ?: return null
        if (qty > threshold) return null
        return Reminder(item, DueStatus.LOW_STOCK, null, 0, notifId(item.id, DueStatus.LOW_STOCK, null))
    }

    private fun recurring(item: Item, today: LocalDate): Reminder? {
        val nextDue = item.nextDueAtEpochDay ?: return null
        val daysLeft = nextDue - today.toEpochDay()
        val status = when {
            daysLeft == 0L -> DueStatus.RENEWAL_TODAY
            daysLeft in 1..7 && item.reminderOffsetsDays.any { off -> daysLeft == off.toLong() } -> DueStatus.RENEWAL_SOON
            else -> return null
        }
        return Reminder(item, status, daysLeft, 0, notifId(item.id, status, today))
    }

    /** 今日 + 未来 withinDays 内的到期摘要（"今日"屏排序用） */
    fun upcoming(items: List<Item>, today: LocalDate, withinDays: Long = 14): List<Pair<Item, Long>> =
        items.filter { it.deletedAt == null }
            .mapNotNull { item ->
                val epoch = when (item.reminderKind) {
                    ReminderKind.EXPIRY -> item.expireAtEpochDay
                    ReminderKind.RECURRING -> item.nextDueAtEpochDay
                    ReminderKind.CONSUMABLE -> null
                } ?: return@mapNotNull null
                item to (epoch - today.toEpochDay())
            }
            .filter { it.second <= withinDays }
            .sortedBy { it.second }

    private fun notifId(itemId: String, status: DueStatus, date: LocalDate?): Int =
        "$itemId|${status.name}|${date?.toEpochDay() ?: "static"}".hashCode() and 0x7FFFFFFF
}
