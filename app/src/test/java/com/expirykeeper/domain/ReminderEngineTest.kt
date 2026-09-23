package com.expirykeeper.domain

import com.expirykeeper.core.data.Item
import com.expirykeeper.core.data.ReminderKind
import com.expirykeeper.core.domain.DueStatus
import com.expirykeeper.core.domain.ReminderEngine
import com.expirykeeper.core.domain.daysCaption
import com.expirykeeper.core.domain.ringSpec
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

    @Test fun upcomingHonorsDerivedExpireDate() {
        val opened = today.minusDays(5).toEpochDay()
        val item = expiryItem(0, listOf(0)).copy(
            expireAtEpochDay = null, openedAtEpochDay = opened, shelfLifeDays = 6,
        )
        val list = ReminderEngine.upcoming(listOf(item), today)
        assertEquals(1, list.size)
        assertEquals(1L, list[0].second)
    }

    @Test fun `notification ids stable per day unique per status`() {
        val a = ReminderEngine.computeOne(expiryItem(0, listOf(0)), today)!!
        val b = ReminderEngine.computeOne(expiryItem(0, listOf(0)), today)!!
        assertEquals(a.notificationId, b.notificationId)
    }

    @Test fun snoozedItemStaysSilent() {
        val item = expiryItem(0, listOf(0)).copy(snoozedUntilEpochDay = epoch + 5)
        assertTrue(ReminderEngine.computeForDate(listOf(item), today).isEmpty())
    }

    @Test fun handledTodaySilentButReturnsTomorrow() {
        val item = expiryItem(0, listOf(0)).copy(handledAtEpochDay = epoch, handledStatus = "DUE_TODAY")
        assertTrue(ReminderEngine.computeForDate(listOf(item), today).isEmpty())
        val tomorrow = today.plusDays(1)
        val r = ReminderEngine.computeForDate(listOf(item), tomorrow)
        assertEquals(1, r.size)
        assertEquals(DueStatus.OVERDUE, r[0].status)
    }

    @Test fun effectiveExpireDerivedFromOpenedShelfLife() {
        val opened = LocalDate.of(2026, 9, 1).toEpochDay()
        val item = expiryItem(0, listOf(0)).copy(expireAtEpochDay = null, openedAtEpochDay = opened, shelfLifeDays = 3)
        assertEquals(opened + 3, ReminderEngine.effectiveExpireDay(item))
    }

    @Test fun openedPlusShelfLifeFiresExpiryReminder() {
        val opened = today.minusDays(5).toEpochDay()
        val item = expiryItem(0, listOf(0)).copy(
            expireAtEpochDay = null, openedAtEpochDay = opened, shelfLifeDays = 5,
        )
        val r = ReminderEngine.computeOne(item, today)!!
        assertEquals(DueStatus.DUE_TODAY, r.status)
    }

    // ---- A4 到期环显示规格：逾期与「今天到期」「还剩 N 天」必须一眼可分 ----

    @Test fun overdueRingShowsDaysOverdueAndFullArc() {
        val spec = ringSpec(DueStatus.OVERDUE, daysLeft = 0, overdueDays = 3, offsets = listOf(3, 0))
        assertEquals("3", spec.text)
        assertEquals(1f, spec.fraction, 0.0001f)
    }

    @Test fun longOverdueStaysReadableInsteadOfZero() {
        val spec = ringSpec(DueStatus.OVERDUE, daysLeft = 0, overdueDays = 200, offsets = listOf(3, 0))
        assertEquals("200", spec.text)
    }

    @Test fun dueTodayRingIsNotConfusableWithOverdue() {
        val spec = ringSpec(DueStatus.DUE_TODAY, daysLeft = 0, overdueDays = 0, offsets = listOf(3, 0))
        assertEquals("今", spec.text)
        assertEquals(0f, spec.fraction, 0.0001f)
    }

    @Test fun renewalTodayRingAlsoReadsToday() {
        assertEquals("今", ringSpec(DueStatus.RENEWAL_TODAY, daysLeft = 0, overdueDays = 0, offsets = listOf(3, 0)).text)
    }

    @Test fun futureRingCountsDownWithinWindow() {
        val spec = ringSpec(DueStatus.DUE_SOON, daysLeft = 7, overdueDays = 0, offsets = listOf(3, 0))
        assertEquals("7", spec.text)
        assertEquals(0.5f, spec.fraction, 0.0001f)
    }

    @Test fun ringWindowFollowsLongOffsetsInsteadOfPinningFull() {
        val spec = ringSpec(DueStatus.DUE_SOON, daysLeft = 45, overdueDays = 0, offsets = listOf(60, 30))
        assertEquals(0.75f, spec.fraction, 0.0001f)
    }

    @Test fun lowStockRingShowsPlaceholderWithoutNumber() {
        val spec = ringSpec(DueStatus.LOW_STOCK, daysLeft = null, overdueDays = 0, offsets = listOf(3, 0))
        assertEquals("·", spec.text)
        assertEquals(0f, spec.fraction, 0.0001f)
    }

    // ---- A6 今日「即将到期」与 hero「两周内」必须同口径 ----

    @Test fun soonSectionCoversWholeHorizonNotOnlyOffsetDays() {
        val at1 = expiryItem(1, listOf(3, 0))
        val at10 = expiryItem(10, listOf(3, 0))
        val soon = ReminderEngine.soonSection(listOf(at10 to 10L, at1 to 1L), withinDays = 14)
        assertEquals(listOf(1L, 10L), soon.map { it.second })
    }

    @Test fun soonSectionExcludesTodayAndBeyondWindow() {
        val todayItem = expiryItem(0, listOf(0))
        val overdue = expiryItem(-2, listOf(3, 0))
        val far = expiryItem(30, listOf(3, 0))
        val soon = ReminderEngine.soonSection(
            listOf(far to 30L, overdue to -2L, todayItem to 0L), withinDays = 14,
        )
        assertTrue(soon.isEmpty())
    }

    // ---- A5 清单尾部相对天数文案（替代光秃秃的「—」） ----

    @Test fun daysCaptionNamesTheActualDistance() {
        assertEquals("逾 3 天", daysCaption(-3))
        assertEquals("今天", daysCaption(0))
        assertEquals("剩 6 天", daysCaption(6))
    }

    @Test fun dueDayFollowsReminderKind() {
        val expiry = expiryItem(5, listOf(3, 0))
        assertEquals(epoch + 5, ReminderEngine.dueDayOf(expiry))
        val recurring = Item(id = "r9", name = "视频会员", categoryId = "subscription",
            reminderKind = ReminderKind.RECURRING, nextDueAtEpochDay = epoch + 2, reminderOffsetsDays = listOf(3, 0))
        assertEquals(epoch + 2, ReminderEngine.dueDayOf(recurring))
        val consumable = Item(id = "c9", name = "猫粮", categoryId = "pet",
            reminderKind = ReminderKind.CONSUMABLE, quantity = 1.2, lowStockThreshold = 2.0)
        assertNull(ReminderEngine.dueDayOf(consumable))
    }
}
