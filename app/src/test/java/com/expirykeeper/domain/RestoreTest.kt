package com.expirykeeper.domain

import com.expirykeeper.core.data.Item
import com.expirykeeper.core.domain.Restore
import com.expirykeeper.core.domain.RestorePlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreTest {

    private fun item(id: String, updatedAt: Long, deletedAt: Long? = null, name: String = "n") =
        Item(id = id, name = name, categoryId = "c", updatedAt = updatedAt, deletedAt = deletedAt)

    private fun planOf(local: List<Item>, incoming: List<Item>): RestorePlan {
        val p = Restore.plan(local, incoming)
        assertNotNull("非空备份必须给出计划", p)
        return p!!
    }

    /** 用户验收 2026-09-25 的原始缺陷：导出 → 改一条 → 导入，改动的必须被带回备份时的样子 */
    @Test fun restoreRollsBackEditsMadeAfterExport() {
        val exported = item("a", 100, name = "导出时的样子")
        val editedLocally = item("a", 200, name = "导出之后改的")
        val plan = planOf(listOf(editedLocally), listOf(exported))
        assertEquals(listOf("导出时的样子"), plan.rows.map { it.name })
        assertEquals(100L, plan.rows[0].updatedAt)
        assertTrue(plan.removed.isEmpty())
    }

    /** 空文件不该清空用户的清单 */
    @Test fun emptyBackupIsRefused() {
        assertNull(Restore.plan(listOf(item("a", 1)), emptyList()))
    }

    /** 备份里是墓碑 → 恢复重放删除 */
    @Test fun tombstoneInFileReplaysDeletion() {
        val plan = planOf(listOf(item("a", 10, name = "还活着")), listOf(item("a", 9, deletedAt = 9)))
        assertEquals(1, plan.tombstones)
        assertEquals(0, plan.liveWritten)
    }

    /** 备份之后新增的物品会被移出清单，且必须在计划里可见（确认框要报数） */
    @Test fun localOnlyLiveItemIsReportedAsRemoved() {
        val plan = planOf(
            listOf(item("a", 1, name = "在备份里"), item("b", 99, name = "备份之后加的")),
            listOf(item("a", 1, name = "在备份里")),
        )
        assertEquals(listOf("备份之后加的"), plan.removed.map { it.name })
    }

    /** 本地已经是墓碑的行不算"被移除"——它本来就不在清单上 */
    @Test fun localTombstoneIsNotCountedAsRemoved() {
        val plan = planOf(
            listOf(item("a", 1), item("z", 5, deletedAt = 5)),
            listOf(item("a", 1)),
        )
        assertTrue(plan.removed.isEmpty())
    }

    /** 文件内部同一 id 出现两条自相矛盾的记录：取时间最新的那条，顺序不参与判断 */
    @Test fun duplicateIdsCollapseToNewestRegardlessOfOrder() {
        val old = item("a", 10, name = "旧")
        val fresh = item("a", 20, name = "新")
        assertEquals("新", planOf(emptyList(), listOf(old, fresh)).rows[0].name)
        assertEquals("新", planOf(emptyList(), listOf(fresh, old)).rows[0].name)
    }

    /** 新设备导入：本地为空，全部按存活/墓碑分别计数 */
    @Test fun freshDeviceImportCountsLiveAndTombstonesSeparately() {
        val plan = planOf(
            emptyList(),
            listOf(item("a", 1), item("b", 2), item("c", 3, deletedAt = 3)),
        )
        assertEquals(2, plan.liveWritten)
        assertEquals(1, plan.tombstones)
        assertEquals(3, plan.rows.size)
    }
}
