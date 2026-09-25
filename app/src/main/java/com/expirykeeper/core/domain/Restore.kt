package com.expirykeeper.core.domain

import com.expirykeeper.core.data.Item

/**
 * 恢复计划：[rows] 原样落库（含墓碑，所以"备份时已删除"会被重放），
 * [removed] 是本地活物品里备份文件没有的那些——备份之后新增的物品，恢复后被移出清单。
 */
data class RestorePlan(val rows: List<Item>, val removed: List<Item>) {
    val tombstones: Int get() = rows.count { it.deletedAt != null }
    val liveWritten: Int get() = rows.size - tombstones
}

/**
 * 导入备份 = **时间点还原**，不是合并。
 *
 * 文件那一刻的状态整体接管本地：每一条都以文件为准写回，不做 last-writer-wins。
 * 这一点是 2026-09-25 用户验收推翻旧实现得来的——原来这里走 LWW（只接受
 * `incoming.updatedAt >= local.updatedAt`），于是"导出 → 改一条 → 导入"必然恢复不动：
 * 改过的那条时间戳永远比备份新，被当作过期数据拒收，而 LWW 恰恰是**合并**的策略。
 * 该函数唯一的调用方就是恢复路径（M3 同步已取消），所以这里不再保留合并语义。
 *
 * 被移出的物品是**打墓碑而不是物理删除**：库里的行永远不删，
 * 万一恢复错了，数据还在，导一份更新的备份就能带回来。
 */
object Restore {

    /** 空备份返回 null：一份"里面什么都没有"的文件不该把用户的清单清空 */
    fun plan(local: List<Item>, incoming: List<Item>): RestorePlan? {
        val deduped = incoming.groupBy { it.id }.values.map { group -> group.maxBy { it.updatedAt } }
        if (deduped.isEmpty()) return null
        val ids = deduped.map { it.id }.toSet()
        return RestorePlan(
            rows = deduped,
            removed = local.filter { it.deletedAt == null && it.id !in ids },
        )
    }
}
