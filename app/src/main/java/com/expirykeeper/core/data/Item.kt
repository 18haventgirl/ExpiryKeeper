package com.expirykeeper.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ReminderKind { EXPIRY, CONSUMABLE, RECURRING }

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
    val deletedAt: Long? = null,
)
