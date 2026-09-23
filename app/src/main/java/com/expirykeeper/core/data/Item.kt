package com.expirykeeper.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ReminderKind { EXPIRY, CONSUMABLE, RECURRING }

/** 清单排序方式（纯枚举，无域逻辑；UI 侧负责中文标签与分组渲染） */
enum class ItemSort { EXPIRE_ASC, NAME, CREATED_DESC, CATEGORY }

@Entity(tableName = "items")
data class Item(
    @PrimaryKey val id: String,
    val name: String,
    val categoryId: String,
    val location: String? = null,
    val note: String? = null,
    val barcode: String? = null,
    val reminderKind: ReminderKind = ReminderKind.EXPIRY,
    /** epoch day，EXPIRY 用 */
    val expireAtEpochDay: Long? = null,
    /** 提前 N 天提醒，升序，0 = 当天 */
    val reminderOffsetsDays: List<Int> = listOf(3, 0),
    val quantity: Double? = null,
    val unit: String? = null,
    val lowStockThreshold: Double? = null,
    val nextDueAtEpochDay: Long? = null,
    val recurrenceDays: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastModifiedBy: String? = null,
    /** 物品级 emoji 图标；null 则回退品类预设 */
    val emoji: String? = null,
    /** 开瓶/开封日期 epochDay；与 shelfLifeDays 联合推导到期日 */
    val openedAtEpochDay: Long? = null,
    val shelfLifeDays: Int? = null,
    /** 今日已处理（不再提醒）的日期与状态名，次日自动失效 */
    val handledAtEpochDay: Long? = null,
    val handledStatus: String? = null,
    /** 延后提醒截止日：today <= 该值时引擎静默 */
    val snoozedUntilEpochDay: Long? = null,
    val deletedAt: Long? = null,
)

@Entity(tableName = "events")
data class ItemEvent(
    @PrimaryKey(autoGenerate = true) val id: Long? = null,
    val itemId: String,
    val kind: String,
    val epochDay: Long,
    val createdAt: Long = System.currentTimeMillis(),
)
