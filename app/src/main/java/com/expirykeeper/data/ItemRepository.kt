package com.expirykeeper.data

import kotlinx.coroutines.flow.Flow

class ItemRepository(
    private val itemDao: ItemDao,
    private val changeLogDao: ChangeLogDao,
    private val deviceId: String,
) {
    fun observeAll(): Flow<List<Item>> = itemDao.observeAll()

    suspend fun getAll(): List<Item> = itemDao.getAll()

    suspend fun getById(id: String): Item? = itemDao.getById(id)

    suspend fun save(item: Item) {
        val now = System.currentTimeMillis()
        val toWrite = item.copy(updatedAt = now, lastModifiedBy = deviceId)
        itemDao.upsert(toWrite)
        changeLogDao.insert(ChangeLogEntry(itemId = item.id, op = "upsert", updatedAt = now, deviceId = deviceId))
    }

    suspend fun softDelete(id: String) {
        val now = System.currentTimeMillis()
        itemDao.softDelete(id, now)
        changeLogDao.insert(ChangeLogEntry(itemId = id, op = "delete", updatedAt = now, deviceId = deviceId))
    }

    suspend fun consumeOne(id: String) {
        val item = itemDao.getById(id) ?: return
        val qty = (item.quantity ?: 1.0) - 1.0
        itemDao.setQuantity(id, qty.coerceAtLeast(0.0))
        changeLogDao.insert(ChangeLogEntry(itemId = id, op = "upsert", updatedAt = System.currentTimeMillis(), deviceId = deviceId))
    }

    /** M3 用：返回 seq 水位，之后取增量 */
    suspend fun changeLogSince(seq: Long): List<ChangeLogEntry> = changeLogDao.since(seq)
}
