package com.expirykeeper

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.expirykeeper.core.data.AppDatabase
import com.expirykeeper.core.data.Item
import com.expirykeeper.core.data.ItemRepository
import com.expirykeeper.core.domain.Restore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 恢复 = 整库替换，这是全 App 唯一会动到"别人的行"的操作，所以它的三条不变量必须真跑一遍
 * Room 才算证明（JVM 单测只能证明计划算对了，证明不了事务与落库结果）：
 * ① 备份里的旧版本确实盖掉本地更新（用户报的那个 bug）；
 * ② 备份之后新增的物品只是被打墓碑，行还在库里，永不物理删除；
 * ③ 撤销（restoreSnapshot）能把整库换回恢复之前。
 */
@RunWith(AndroidJUnit4::class)
class RestoreDbTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: ItemRepository

    private fun item(id: String, name: String, updatedAt: Long, deletedAt: Long? = null) =
        Item(id = id, name = name, categoryId = "food-chilled", updatedAt = updatedAt, deletedAt = deletedAt)

    @Before
    fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        repo = ItemRepository(db.itemDao(), db.changeLogDao(), db.eventDao(), "device-under-test")
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun restoreReplacesWholeLibraryAndKeepsRows() = runBlocking(Dispatchers.IO) {
        // 场景：导出一份备份 → 本地改名 → 又新增一件 → 导入那份备份。
        // save() 会把 updatedAt 刷成"现在"（远大于文件里的 100），所以本地两行必然比备份新——
        // 正是用户验收时那个"改过的行时间戳永远赢过备份"的形状。
        repo.save(item("yogurt", "草莓", 200))
        repo.save(item("egg", "鸡蛋", 300))
        val before = repo.getAllIncludingTombstones()

        val file = listOf(item("yogurt", "原味", 100))
        val plan = Restore.plan(repo.getAllIncludingTombstones(), file)
        assertNotNull("非空备份应给出计划", plan)
        repo.restore(plan!!)

        // ① 旧版本盖掉本地更新，且时间戳是文件里的 100，不是"刚刚"
        val live = repo.getAll()
        assertEquals(listOf("yogurt"), live.map { it.id })
        assertEquals("原味", live[0].name)
        assertEquals(100L, live[0].updatedAt)

        // ② 备份之后加的鸡蛋被移出清单，但行还在（可再导回来，不是真删）
        assertEquals(0, repo.getAll().count { it.id == "egg" })
        val eggRow = repo.getAllIncludingTombstones().first { it.id == "egg" }
        assertNotNull("鸡蛋必须留下墓碑行", eggRow.deletedAt)

        // ③ 撤销：整库换回恢复之前
        repo.restoreSnapshot(before)
        assertEquals(setOf("草莓", "鸡蛋"), repo.getAll().map { it.name }.toSet())
        assertNull(repo.getAll().first { it.id == "egg" }.deletedAt)
    }

    /** 备份里自带的墓碑要能重放：恢复后那件物品仍然是删除状态，且保住当初的删除时间 */
    @Test
    fun tombstoneInBackupIsReplayed() = runBlocking(Dispatchers.IO) {
        repo.save(item("milk", "鲜牛奶", 500))
        val plan = Restore.plan(repo.getAllIncludingTombstones(), listOf(item("milk", "鲜牛奶", 100, deletedAt = 100)))!!
        repo.restore(plan)

        val row = repo.getAllIncludingTombstones().first { it.id == "milk" }
        assertEquals(0, repo.getAll().size)
        assertEquals(100L, row.deletedAt)
        assertEquals(100L, row.updatedAt)
    }
}
