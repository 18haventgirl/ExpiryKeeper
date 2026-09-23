package com.expirykeeper.core.domain

import com.expirykeeper.core.data.Item

data class MergeResult(val toWrite: List<Item>, val skipped: Int)

/** 冲突策略 = Last-Writer-Wins by updatedAt；平局信 incoming（备份导入语义：恢复优先）。M3 WebDAV 与备份共用。 */
object SyncMerge {
    fun merge(local: List<Item>, incoming: List<Item>): MergeResult {
        val byId = local.associateBy { it.id }
        var skipped = 0
        val writes = incoming.filter { inc ->
            val cur = byId[inc.id]
            val accept = cur == null || inc.updatedAt >= cur.updatedAt
            if (!accept) skipped += 1
            accept
        }
        return MergeResult(writes, skipped)
    }
}
