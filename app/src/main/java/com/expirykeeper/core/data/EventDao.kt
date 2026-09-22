package com.expirykeeper.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface EventDao {
    @Insert suspend fun insert(e: ItemEvent)

    @Query("SELECT * FROM events WHERE epochDay >= :fromEpochDay ORDER BY createdAt DESC")
    suspend fun since(fromEpochDay: Long): List<ItemEvent>

    @Query("SELECT COUNT(*) FROM events WHERE kind = 'roll' AND epochDay >= :fromEpochDay")
    suspend fun rollCountSince(fromEpochDay: Long): Int
}
