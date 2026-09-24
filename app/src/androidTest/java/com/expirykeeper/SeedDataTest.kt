package com.expirykeeper

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.expirykeeper.core.data.Item
import com.expirykeeper.core.data.ReminderKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * 开发期种子数据：唯一被允许的写库方式——在目标进程内走 ItemRepository，
 * 而不是 `adb shell run-as … sqlite3` 直改文件。后者会在 WAL 之外产生写入，
 * 与快照/覆盖层混代时会让 SQLite 判定 WAL 无效并整份丢弃（2026-09-24 测试数据消失的成因）。
 *
 * 不带 `-Pandroid.testInstrumentationRunnerArguments.seed=on` 时整个用例跳过，
 * 所以常规 connectedAndroidTest 不会污染设备数据。id 固定，重复执行是 upsert 而非追加。
 *
 * 注意：`connectedDebugAndroidTest` 跑完会回滚安装（app 连 /data/data 一起删），
 * 用它种数据等于种完就清空。要留下数据得自己装 APK 再 instrument：
 *   adb install -r app/build/outputs/apk/debug/app-debug.apk
 *   adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
 *   adb shell "am instrument -w -e class 'com.expirykeeper.SeedDataTest#seed' \
 *     -e seed on com.expirykeeper.test/androidx.test.runner.AndroidJUnitRunner"
 */
@RunWith(AndroidJUnit4::class)
class SeedDataTest {

    @Test
    fun seed() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("pass seed=on to write demo data", "on" == args.getString("seed"))

        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as App
        val repo = app.container.repository
        val today = LocalDate.now()
        fun day(offset: Long) = today.plusDays(offset).toEpochDay()

        val seeded = listOf(
            // 直接给到期日、不给保质期：绕过派生，造一条纯逾期
            Item(id = "seed-yogurt", name = "酸奶", categoryId = "food-chilled", emoji = "🥤",
                location = "冰箱·中层", expireAtEpochDay = day(-2), reminderOffsetsDays = listOf(2, 0)),
            Item(id = "seed-milk", name = "鲜牛奶", categoryId = "food-chilled",
                location = "冰箱·门架", openedAtEpochDay = day(-2), shelfLifeDays = 3,
                reminderOffsetsDays = listOf(2, 0)),
            Item(id = "seed-syrup", name = "布洛芬混悬液", categoryId = "medicine",
                location = "药箱·左上", note = "开瓶后 4 周作废",
                openedAtEpochDay = day(-21), shelfLifeDays = 28, reminderOffsetsDays = listOf(7, 3, 0)),
            Item(id = "seed-mascara", name = "睫毛膏", categoryId = "cosmetic",
                location = "梳妆台", openedAtEpochDay = day(-150), shelfLifeDays = 180,
                reminderOffsetsDays = listOf(30, 7, 0)),
            Item(id = "seed-license", name = "驾驶证换证", categoryId = "document", emoji = "🪪",
                note = "到期前 90 天可办", expireAtEpochDay = day(12),
                reminderOffsetsDays = listOf(90, 30, 7)),
            Item(id = "seed-video", name = "视频会员", categoryId = "subscription",
                reminderKind = ReminderKind.RECURRING, recurrenceDays = 30, nextDueAtEpochDay = day(0),
                reminderOffsetsDays = listOf(3, 0)),
            Item(id = "seed-detergent", name = "洗衣液", categoryId = "household",
                reminderKind = ReminderKind.CONSUMABLE, quantity = 0.4, unit = "瓶",
                lowStockThreshold = 1.0),
        )
        seeded.forEach { repo.save(it) }

        assertEquals(7, repo.getAll().count { it.id.startsWith("seed-") })
    }
}
