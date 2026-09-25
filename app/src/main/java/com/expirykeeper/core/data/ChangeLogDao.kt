package com.expirykeeper.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ChangeLogDao {
    @Insert
    suspend fun insert(entry: ChangeLogEntry)

    @Insert
    suspend fun insertAll(entries: List<ChangeLogEntry>)

    @Query("SELECT * FROM change_log WHERE seq > :since ORDER BY seq")
    suspend fun since(since: Long): List<ChangeLogEntry>

    @Query("SELECT MAX(seq) FROM change_log")
    suspend fun maxSeq(): Long?
}
