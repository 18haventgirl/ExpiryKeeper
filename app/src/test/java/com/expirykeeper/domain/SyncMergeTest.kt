package com.expirykeeper.domain

import com.expirykeeper.core.data.Item
import com.expirykeeper.core.domain.SyncMerge
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncMergeTest {
    private fun item(id: String, updatedAt: Long, deletedAt: Long? = null, name: String = "n") =
        Item(id = id, name = name, categoryId = "c", updatedAt = updatedAt, deletedAt = deletedAt)

    @Test fun newerIncomingWins() {
        val r = SyncMerge.merge(listOf(item("a", 10, name = "old")), listOf(item("a", 20, name = "new")))
        assertEquals(listOf("new"), r.toWrite.map { it.name })
    }

    @Test fun newerLocalDefends() {
        val r = SyncMerge.merge(listOf(item("a", 30, name = "mine")), listOf(item("a", 20, name = "theirs")))
        assertEquals(0, r.toWrite.size)
        assertEquals(1, r.skipped)
    }

    @Test fun tiePrefersIncoming() {
        val r = SyncMerge.merge(listOf(item("a", 20, name = "mine")), listOf(item("a", 20, name = "incoming")))
        assertEquals(listOf("incoming"), r.toWrite.map { it.name })
    }

    @Test fun tombstoneReapesOldEdit() {
        val r = SyncMerge.merge(listOf(item("a", 10)), listOf(item("a", 20, deletedAt = 20)))
        assertEquals(1, r.toWrite.size)
        assertEquals(20L, r.toWrite[0].deletedAt)
    }

    @Test fun liveEditBeatsOldTombstone() {
        val r = SyncMerge.merge(listOf(item("a", 50, name = "revive")), listOf(item("a", 20, deletedAt = 20)))
        assertEquals(0, r.toWrite.size)
    }

    /**
     * C1 回归护栏：本地墓碑必须对合并"可见"。
     * 导入路径改用 getAllIncludingTombstones 后，本地快照含墓碑行——
     * 更旧的到来记录被墓碑挡下（删除保持）；更新鲜的到来记录仍可复活（正确 LWW）。
     * 本测试固化 SyncMerge 侧对墓碑的两向语义（repo 层 importMerged 是 instrumented-only）。
     */
    @Test fun localTombstoneBlocksOlderLiveIncoming() {
        val tombstone = item("a", 50, deletedAt = 50)
        val rejected = SyncMerge.merge(listOf(tombstone), listOf(item("a", 20, name = "stale-live")))
        assertEquals(0, rejected.toWrite.size)
        assertEquals(1, rejected.skipped)
        val revived = SyncMerge.merge(listOf(tombstone), listOf(item("a", 80, name = "edited-after-delete")))
        assertEquals(listOf("edited-after-delete"), revived.toWrite.map { it.name })
    }

    @Test fun freshIdsPassThrough() {
        val r = SyncMerge.merge(listOf(item("a", 1)), listOf(item("b", 1)))
        assertEquals(listOf("b"), r.toWrite.map { it.id })
    }
}
