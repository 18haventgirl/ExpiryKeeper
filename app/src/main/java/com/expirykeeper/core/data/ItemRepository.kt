package com.expirykeeper.core.data

import com.expirykeeper.core.domain.Restore
import com.expirykeeper.core.domain.RestorePlan
import kotlinx.coroutines.flow.Flow

class ItemRepository(
    private val itemDao: ItemDao,
    private val changeLogDao: ChangeLogDao,
    private val eventDao: EventDao,
    private val deviceId: String,
) {
    fun observeAll(): Flow<List<Item>> = itemDao.observeAll()

    suspend fun getAll(): List<Item> = itemDao.getAll()

    /**
     * 导出用全量读取（含墓碑）。恢复本身是整库替换，"文件里没有"就足以让物品消失；
     * 带上墓碑是为了让备份保住**删除发生的时间**：恢复后这些行的 deletedAt/updatedAt
     * 是当初真删的那一刻，而不是"恢复动作发生的这一刻"，详情浮层与后续判断才有据可依。
     */
    suspend fun getAllIncludingTombstones(): List<Item> = itemDao.getAllIncludingTombstones()


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

    /**
     * 延后提醒：`days` = 静默天数（含今天）。引擎语义是 today <= snoozedUntilEpochDay 时静默，
     * 故最后静默日 = today + days - 1，从 today + days 起恢复提醒。
     * 「稍后 3 天」= 今天/明天/后天静默、第 3 天(+3)重新到期——此前误写 today+days 导致延到第 4 天（M-3 off-by-one）。
     */
    suspend fun snooze(id: String, days: Int, today: java.time.LocalDate) {
        val now = System.currentTimeMillis()
        itemDao.setSnoozedUntil(id, today.toEpochDay() + days - 1, now, deviceId)
        changeLogDao.insert(ChangeLogEntry(itemId = id, op = "upsert", updatedAt = now, deviceId = deviceId))
        eventDao.insert(ItemEvent(itemId = id, kind = "snooze", epochDay = today.toEpochDay()))
    }

    suspend fun recentEvents(days: Int = 30): List<ItemEvent> =
        eventDao.since(java.time.LocalDate.now().minusDays(days.toLong()).toEpochDay())

    suspend fun rollCount30d(): Int =
        eventDao.rollCountSince(java.time.LocalDate.now().minusDays(30).toEpochDay())

    /**
     * 恢复预览：拿本地全量（**含墓碑**）与文件比对，算出"写回什么、移出什么"的账目，
     * 供确认框在动手之前把数字报给用户。本地快照必须含墓碑，否则"备份里已删、本地还在"
     * 的那条会被当成移出对象重复计一次。
     */
    suspend fun planRestore(incoming: List<Item>): RestorePlan? =
        Restore.plan(itemDao.getAllIncludingTombstones(), incoming)

    /**
     * 执行恢复 = **时间点还原**：整库换成备份那一刻的样子，不做 last-writer-wins
     * （那是合并语义，用在恢复上会让"导出→改一条→导入"什么都恢复不回来，2026-09-25 验收推翻）。
     *
     * 三条不变量：
     * 1. 用户数据行永不物理删除——备份里没有的那些只打墓碑，导一份更新的备份就能带回来；
     * 2. 清空 + 写入在 [ItemDao.replaceWith] 的同一事务里，中途崩溃不会留下半份清单；
     * 3. 写入保留文件里的 updatedAt（不走 [save]）——[save] 会把时间戳刷成"刚刚"，
     *    恢复后的库就带着一堆假历史，下一次恢复又被误判。
     *
     * @return 写回的条数（含墓碑）
     */
    suspend fun restore(plan: RestorePlan): Int {
        val now = System.currentTimeMillis()
        val removed = plan.removed.map { it.copy(deletedAt = now, updatedAt = now, lastModifiedBy = deviceId) }
        val rows = plan.rows + removed
        itemDao.replaceWith(rows)
        changeLogDao.insertAll(
            rows.map {
                ChangeLogEntry(
                    itemId = it.id,
                    op = if (it.deletedAt != null) "delete" else "upsert",
                    updatedAt = it.updatedAt,
                    deviceId = deviceId,
                )
            },
        )
        return rows.size
    }

    /** 撤销恢复：把恢复前那份全量原样换回去（同样单事务，不追加墓碑，时间戳保持快照原值） */
    suspend fun restoreSnapshot(rows: List<Item>) = itemDao.replaceWith(rows)
}
