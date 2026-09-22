package com.expirykeeper.core.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE deletedAt IS NULL")
    suspend fun getAll(): List<Item>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getById(id: String): Item?

    @Upsert
    suspend fun upsert(item: Item)

    @Query("UPDATE items SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE items SET quantity = :qty, updatedAt = :now WHERE id = :id")
    suspend fun setQuantity(id: String, qty: Double, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM items WHERE id = :id")
    fun observeById(id: String): Flow<Item?>

    @Query("UPDATE items SET expireAtEpochDay = :epochDay, updatedAt = :now, lastModifiedBy = :deviceId WHERE id = :id")
    suspend fun setExpire(id: String, epochDay: Long, now: Long, deviceId: String)

    @Query("UPDATE items SET handledAtEpochDay = :day, handledStatus = :status, updatedAt = :now, lastModifiedBy = :deviceId WHERE id = :id")
    suspend fun setHandled(id: String, status: String, day: Long, now: Long, deviceId: String)

    @Query("UPDATE items SET snoozedUntilEpochDay = :until, handledAtEpochDay = NULL, handledStatus = NULL, updatedAt = :now, lastModifiedBy = :deviceId WHERE id = :id")
    suspend fun setSnoozedUntil(id: String, until: Long, now: Long, deviceId: String)
}
