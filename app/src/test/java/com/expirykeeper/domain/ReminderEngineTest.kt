package com.expirykeeper.domain

import com.expirykeeper.core.data.Item
import com.expirykeeper.core.data.ReminderKind
import com.expirykeeper.core.domain.DueStatus
import com.expirykeeper.core.domain.ReminderEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReminderEngineTest {

    private val today = LocalDate.of(2026, 9, 22)
    private val epoch = today.toEpochDay()

    private fun expiryItem(daysToExpire: Long, offsets: List<Int>) = Item(
        id = "i-$daysToExpire-${offsets.joinToString()}",
        name = "牛奶",
        categoryId = "food-chilled",
        reminderKind = ReminderKind.EXPIRY,
        expireAtEpochDay = epoch + daysToExpire,
        reminderOffsetsDays = offsets,
    )

    @Test fun `fires at each configured offset`() {
        val r = ReminderEngine.computeOne(expiryItem(3, listOf(3, 0)), today)!!
        assertEquals(DueStatus.DUE_SOON, r.status)
        assertEquals(3L, r.daysLeft)
    }

    @Test fun `silent between offsets`() {
        assertNull(ReminderEngine.computeOne(expiryItem(5, listOf(3, 0)), today))
    }

    @Test fun `due today at zero`() {
        assertEquals(DueStatus.DUE_TODAY, ReminderEngine.computeOne(expiryItem(0, listOf(3, 0)), today)!!.status)
    }

    @Test fun `overdue fires daily with days count`() {
        val r = ReminderEngine.computeOne(expiryItem(-2, listOf(3, 0)), today)!!
        assertEquals(DueStatus.OVERDUE, r.status)
        assertEquals(2L, r.overdueDays)
    }

    @Test fun `low stock triggers at or under threshold`() {
        val base = Item(id = "c1", name = "布洛芬", categoryId = "medicine",
            reminderKind = ReminderKind.CONSUMABLE, quantity = 2.0, lowStockThreshold = 2.0)
        assertEquals(DueStatus.LOW_STOCK, ReminderEngine.computeOne(base, today)!!.status)
        assertEquals(null, ReminderEngine.computeOne(base.copy(quantity = 3.0), today))
    }

    @Test fun `recurring fires within offsets only`() {
        val item = Item(id = "r1", name = "视频会员", categoryId = "subscription",
            reminderKind = ReminderKind.RECURRING, nextDueAtEpochDay = epoch + 3,
            reminderOffsetsDays = listOf(3, 0))
        assertEquals(DueStatus.RENEWAL_SOON, ReminderEngine.computeOne(item, today)!!.status)
        assertEquals(DueStatus.RENEWAL_TODAY, ReminderEngine.computeOne(item.copy(nextDueAtEpochDay = epoch), today)!!.status)
        assertNull(ReminderEngine.computeOne(item.copy(nextDueAtEpochDay = epoch + 4), today))
    }

    @Test fun `deleted items never fire`() {
        val item = expiryItem(0, listOf(0)).copy(deletedAt = System.currentTimeMillis())
        assertTrue(ReminderEngine.computeForDate(listOf(item), today).isEmpty())
    }

    @Test fun `upcoming sorted and windowed`() {
        val soon = expiryItem(1, listOf(0))
        val later = expiryItem(10, listOf(0))
        val outWindow = expiryItem(30, listOf(0))
        val list = ReminderEngine.upcoming(listOf(later, outWindow, soon), today, withinDays = 14)
        assertEquals(listOf(1L, 10L), list.map { it.second })
    }

    @Test fun `notification ids stable per day unique per status`() {
        val a = ReminderEngine.computeOne(expiryItem(0, listOf(0)), today)!!
        val b = ReminderEngine.computeOne(expiryItem(0, listOf(0)), today)!!
        assertEquals(a.notificationId, b.notificationId)
    }
}
