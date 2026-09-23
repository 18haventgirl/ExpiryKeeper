package com.expirykeeper

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.expirykeeper.core.data.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate1To2KeepsData() {
        helper.createDatabase("m1", 1).apply {
            execSQL("""INSERT INTO items (id,name,categoryId,reminderKind,expireAtEpochDay,reminderOffsetsDays,createdAt,updatedAt)
                VALUES ('i1','牛奶','food-chilled','EXPIRY',20000,'2,0',1,1)""")
            close()
        }
        helper.runMigrationsAndValidate("m1", 2, true, AppDatabase.MIGRATION_1_2).apply {
            // M-6：Kotlin stdlib assert 依赖 -ea 开关（可被 elide），插桩测试下形同虚设；改用 JUnit 断言
            query("SELECT name, emoji, snoozedUntilEpochDay FROM items").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("牛奶", c.getString(0))
                assertTrue(c.isNull(1))
                assertTrue(c.isNull(2))
            }
            query("SELECT COUNT(*) FROM events").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0))
            }
            close()
        }
    }
}
