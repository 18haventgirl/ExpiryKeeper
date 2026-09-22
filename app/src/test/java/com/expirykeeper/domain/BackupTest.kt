package com.expirykeeper.domain

import com.expirykeeper.core.data.Item
import com.expirykeeper.core.data.ReminderKind
import com.expirykeeper.core.domain.Backup
import com.expirykeeper.core.domain.BackupFormatException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupTest {
    @Test fun roundTripPreservesAllFields() {
        val items = listOf(
            Item(id = "a", name = "牛奶", categoryId = "food-chilled", reminderKind = ReminderKind.EXPIRY,
                expireAtEpochDay = 20600, reminderOffsetsDays = listOf(2, 0), quantity = 3.0, unit = "盒",
                emoji = "🥛", openedAtEpochDay = 20000, shelfLifeDays = 3, updatedAt = 123),
            Item(id = "b", name = "视频会员", categoryId = "subscription", reminderKind = ReminderKind.RECURRING,
                nextDueAtEpochDay = 20700, recurrenceDays = 30, deletedAt = 999),
        )
        assertEquals(items, Backup.parse(Backup.toJson(items)))
    }

    @Test fun envelopeHasVersionAndCount() {
        val json = Backup.toJson(List(2) { Item(id = "i$it", name = "n", categoryId = "c") })
        // toString(2) 美化输出为 "key": value（冒号后带空格），直接 contains 会因空格失配，解析后断言更稳
        val root = org.json.JSONObject(json)
        assertEquals(1, root.getInt("formatVersion"))
        assertEquals(2, root.getInt("count"))
    }

    @Test fun garbageInputThrowsTyped() {
        // parse 侧垃圾输入 与 toJson 侧非法时间戳，都必须抛类型化异常
        assertThrows(BackupFormatException::class.java) { Backup.parse("{oops") }
        assertThrows(BackupFormatException::class.java) { Backup.toJson(listOf(Item(id = "x", name = "n", categoryId = "c").copy(updatedAt = -5))) }
    }

    /** C2：全有或全无契约——单条垃圾字段必须整文件拒绝，不得静默降级 */
    private fun envelope(item: String) = """{"formatVersion":1,"count":1,"items":[$item]}"""

    @Test fun missingUpdatedAtRejected() {
        // 缺 updatedAt：optLong 旧行为静默给 0（LWW 下永输），必须拒
        val json = envelope("""{"id":"a","name":"n","categoryId":"c","reminderKind":"EXPIRY","createdAt":100}""")
        assertThrows(BackupFormatException::class.java) { Backup.parse(json) }
    }

    @Test fun unknownReminderKindRejected() {
        // 拼错的 reminderKind 旧行为回退 EXPIRY（数据被悄悄改类），必须拒
        val json = envelope("""{"id":"a","name":"n","categoryId":"c","reminderKind":"BOGUS","createdAt":100,"updatedAt":200}""")
        assertThrows(BackupFormatException::class.java) { Backup.parse(json) }
    }

    @Test fun negativeTimestampRejected() {
        // 负时间戳/负 epoch-day 一律拒（updatedAt=-1 与负 expireAtEpochDay 各验一条）
        val badStamp = envelope("""{"id":"a","name":"n","categoryId":"c","reminderKind":"EXPIRY","createdAt":100,"updatedAt":-1}""")
        assertThrows(BackupFormatException::class.java) { Backup.parse(badStamp) }
        val badEpochDay = envelope("""{"id":"a","name":"n","categoryId":"c","reminderKind":"EXPIRY","expireAtEpochDay":-7,"createdAt":100,"updatedAt":200}""")
        assertThrows(BackupFormatException::class.java) { Backup.parse(badEpochDay) }
    }
}
