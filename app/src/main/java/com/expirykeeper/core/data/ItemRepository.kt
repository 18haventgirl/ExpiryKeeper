package com.expirykeeper.core.data

import kotlinx.coroutines.flow.Flow

class ItemRepository(
    private val itemDao: ItemDao,
    private val changeLogDao: ChangeLogDao,
    private val eventDao: EventDao,
    private val deviceId: String,
) {
    fun observeAll(): Flow<List<Item>> = itemDao.observeAll()

    suspend fun getAll(): List<Item> = itemDao.getAll()

    suspend fun getById(id: String): Item? = itemDao.getById(id)

    /** 详情浮层数据源：含墓碑行（deletedAt 非空由 UI 显示"已不在清单"），删除后实时更新 */
    fun observeById(id: String): Flow<Item?> = itemDao.observeById(id)

    suspend fun save(item: Item) {
        val now = System.currentTimeMillis()
        val todayEpoch = java.time.LocalDate.now().toEpochDay()
        val derived = if (item.expireAtEpochDay == null && item.shelfLifeDays != null && item.reminderKind == ReminderKind.EXPIRY) {
            val base = item.openedAtEpochDay ?: (item.createdAt / 86_400_000L)
            item.copy(expireAtEpochDay = base + item.shelfLifeDays)
        } else item
        val isNew = itemDao.getById(item.id) == null
        val toWrite = derived.copy(updatedAt = now, lastModifiedBy = deviceId)
        itemDao.upsert(toWrite)
        changeLogDao.insert(ChangeLogEntry(itemId = item.id, op = "upsert", updatedAt = now, deviceId = deviceId))
        eventDao.insert(ItemEvent(itemId = item.id, kind = if (isNew) "add" else "edit", epochDay = todayEpoch))
    }

    suspend fun softDelete(id: String) {
        val now = System.currentTimeMillis()
        itemDao.softDelete(id, now)
        changeLogDao.insert(ChangeLogEntry(itemId = id, op = "delete", updatedAt = now, deviceId = deviceId))
        eventDao.insert(ItemEvent(itemId = id, kind = "delete", epochDay = java.time.LocalDate.now().toEpochDay()))
    }

    /** 快速操作"续期/吃完"：EXPIRY 按保质期滚动，RECURRING 按周期滚到未来；无法推导返回 false */
    suspend fun rollForward(id: String, today: java.time.LocalDate): Boolean {
        val item = itemDao.getById(id) ?: return false
        val target = when (item.reminderKind) {
            ReminderKind.EXPIRY -> item.shelfLifeDays?.let { today.toEpochDay() + it } ?: return false
            ReminderKind.RECURRING -> {
                var next = (item.nextDueAtEpochDay ?: today.toEpochDay()) + (item.recurrenceDays ?: return false)
                while (next < today.toEpochDay()) next += item.recurrenceDays!!
                next
            }
            ReminderKind.CONSUMABLE -> return false
        }
        val now = System.currentTimeMillis()
        when (item.reminderKind) {
            // RECURRING: single full-row upsert carrying the new due date; no expireAt write.
            ReminderKind.RECURRING ->
                itemDao.upsert(item.copy(nextDueAtEpochDay = target, updatedAt = now, lastModifiedBy = deviceId))
            // EXPIRY: single targeted update, sharing `now` with the change_log entry below.
            else ->
                itemDao.setExpire(id, target, now, deviceId)
        }
        changeLogDao.insert(ChangeLogEntry(itemId = id, op = "upsert", updatedAt = now, deviceId = deviceId))
        eventDao.insert(ItemEvent(itemId = id, kind = "roll", epochDay = today.toEpochDay()))
        return true
    }

    suspend fun markHandled(id: String, status: String, today: java.time.LocalDate) {
        val now = System.currentTimeMillis()
        itemDao.setHandled(id, status, today.toEpochDay(), now, deviceId)
        changeLogDao.insert(ChangeLogEntry(itemId = id, op = "upsert", updatedAt = now, deviceId = deviceId))
        eventDao.insert(ItemEvent(itemId = id, kind = "handle", epochDay = today.toEpochDay()))
    }

    suspend fun snooze(id: String, days: Int, today: java.time.LocalDate) {
        val now = System.currentTimeMillis()
        itemDao.setSnoozedUntil(id, today.toEpochDay() + days, now, deviceId)
        changeLogDao.insert(ChangeLogEntry(itemId = id, op = "upsert", updatedAt = now, deviceId = deviceId))
        eventDao.insert(ItemEvent(itemId = id, kind = "snooze", epochDay = today.toEpochDay()))
    }

    suspend fun recentEvents(days: Int = 30): List<ItemEvent> =
        eventDao.since(java.time.LocalDate.now().minusDays(days.toLong()).toEpochDay())

    suspend fun rollCount30d(): Int =
        eventDao.rollCountSince(java.time.LocalDate.now().minusDays(30).toEpochDay())

    suspend fun consumeOne(id: String) {
        val item = itemDao.getById(id) ?: return
        val qty = (item.quantity ?: 1.0) - 1.0
        itemDao.setQuantity(id, qty.coerceAtLeast(0.0))
        changeLogDao.insert(ChangeLogEntry(itemId = id, op = "upsert", updatedAt = System.currentTimeMillis(), deviceId = deviceId))
    }

    /** M3 用：返回 seq 水位，之后取增量 */
    suspend fun changeLogSince(seq: Long): List<ChangeLogEntry> = changeLogDao.since(seq)

    /**
     * 恢复入口：与本地 LWW 合并后写入，绝不清空数据库。
     *
     * 先按 id 对 incoming 去重（同 id 保留 updatedAt 最大的一条）再进 SyncMerge：
     * SyncMerge 只拿 incoming 与"写库前的本地快照"逐条比较，对文件内部自相矛盾的
     * 重复条目不设防——若不去重，同一 id 的旧记录排在后面会把先写入的新记录盖掉。
     *
     * 写入走 [upsertRaw] 而非 [save]：save 会重新盖 updatedAt 时间戳，
     * 销毁备份文件携带的 LWW 历史，导致下一次合并误判。
     *
     * @return written（实际落库条数）与 skipped（incoming 总数 − written，
     *         含去重折叠与本地更新被 LWW 拒绝两类，账目诚实）
     */
    suspend fun importMerged(incoming: List<Item>): Pair<Int, Int> {
        val deduped = incoming.groupBy { it.id }.values.map { group -> group.maxBy { it.updatedAt } }
        val result = com.expirykeeper.core.domain.SyncMerge.merge(itemDao.getAll(), deduped)
        result.toWrite.forEach { upsertRaw(it) }
        return result.toWrite.size to (incoming.size - result.toWrite.size)
    }

    /** 原始写入：不动 updatedAt；change_log 记物品自身的 updatedAt，deviceId 用本机 */
    private suspend fun upsertRaw(item: Item) {
        itemDao.upsert(item)
        changeLogDao.insert(
            ChangeLogEntry(
                itemId = item.id,
                op = if (item.deletedAt != null) "delete" else "upsert",
                updatedAt = item.updatedAt,
                deviceId = deviceId,
            )
        )
    }
}
