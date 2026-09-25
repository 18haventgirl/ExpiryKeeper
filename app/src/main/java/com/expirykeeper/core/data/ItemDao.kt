package com.expirykeeper.core.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE deletedAt IS NULL")
    suspend fun getAll(): List<Item>

    /** 含墓碑行的全量读取：备份恢复 / 同步合并的存在性判定必须看见墓碑，否则本地已删项会被复活 */
    @Query("SELECT * FROM items")
    suspend fun getAllIncludingTombstones(): List<Item>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getById(id: String): Item?

    @Upsert
    suspend fun upsert(item: Item)

    @Upsert
    suspend fun upsertAll(items: List<Item>)

    @Query("DELETE FROM items")
    suspend fun deleteAll()

    /**
     * 备份恢复：整库换成备份那一刻的状态。清空 + 写入必须同事务，
     * 中途崩溃不能留下"半份清单"（红线①：宁可整个操作失败也不能处于中间态）。
     */
    @Transaction
    suspend fun replaceWith(items: List<Item>) {
        deleteAll()
        upsertAll(items)
    }

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
