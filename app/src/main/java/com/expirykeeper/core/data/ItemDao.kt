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
}
