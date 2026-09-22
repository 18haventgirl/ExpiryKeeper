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

    @Test fun freshIdsPassThrough() {
        val r = SyncMerge.merge(listOf(item("a", 1)), listOf(item("b", 1)))
        assertEquals(listOf("b"), r.toWrite.map { it.id })
    }
}
