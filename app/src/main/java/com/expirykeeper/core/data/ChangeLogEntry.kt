package com.expirykeeper.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 增量同步单元（M3 使用；M1 先把每次写落下来） */
@Entity(tableName = "change_log")
data class ChangeLogEntry(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val itemId: String,
    val op: String, // upsert | delete
    val updatedAt: Long,
    val deviceId: String,
)
