package com.expirykeeper.domain

import com.expirykeeper.core.domain.nextDailyFire
import com.expirykeeper.core.domain.shouldNotifyNow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * 「每天几点提醒」的唯一算术：设置页存时刻，ReminderScheduler 拿它算闹钟。
 * 抽成纯函数是因为闹钟本身没法在 JVM 上测，而"用户改成 20:00 结果明天早上 9 点响"
 * 这类错全在这一步。
 */
class ReminderTimingTest {

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = LocalDateTime.of(y, mo, d, h, mi)

    @Test fun `setting not yet reached today fires today`() {
        assertEquals(at(2026, 9, 25, 9, 0), nextDailyFire(at(2026, 9, 25, 8, 0), 9, 0))
    }

    @Test fun `setting already passed today fires tomorrow`() {
        assertEquals(at(2026, 9, 26, 9, 0), nextDailyFire(at(2026, 9, 25, 10, 30), 9, 0))
    }

    /** 正好压在设定那一刻：不能再触发一次，否则用户改完时间会立刻收到一条通知 */
    @Test fun `exactly at the setting time fires the next day`() {
        assertEquals(at(2026, 9, 26, 9, 0), nextDailyFire(at(2026, 9, 25, 9, 0), 9, 0))
    }

    @Test fun `one minute before still fires today`() {
        assertEquals(at(2026, 9, 25, 20, 0), nextDailyFire(at(2026, 9, 25, 19, 59), 20, 0))
    }

    /** 月末/年末边界：plusDays(1) 必须进位到下个月/下一年，而不是把日子写死 */
    @Test fun `rolls over month and year boundaries`() {
        assertEquals(at(2026, 10, 1, 7, 0), nextDailyFire(at(2026, 9, 30, 23, 0), 7, 0))
        assertEquals(at(2027, 1, 1, 7, 0), nextDailyFire(at(2026, 12, 31, 23, 0), 7, 0))
    }

    /** 凌晨档：0:30 设定，23:50 之后算出来必须是明天凌晨而不是"今天已经过去的 0:30" */
    @Test fun `supports midnight settings`() {
        assertEquals(at(2026, 9, 25, 0, 30), nextDailyFire(at(2026, 9, 24, 23, 50), 0, 30))
        assertEquals(at(2026, 9, 26, 0, 30), nextDailyFire(at(2026, 9, 25, 0, 31), 0, 30))
    }

    /** 用户可能手改 prefs 塞进非法值（或旧版本存了别的范围）：钳到合法区间而不是崩 */
    @Test fun `out-of-range settings are clamped instead of crashing`() {
        assertEquals(at(2026, 9, 25, 23, 59), nextDailyFire(at(2026, 9, 25, 8, 0), 99, 99))
        assertEquals(at(2026, 9, 26, 0, 0), nextDailyFire(at(2026, 9, 25, 8, 0), -5, -5))
    }

    private val day = java.time.LocalDate.of(2026, 9, 25)
    private val yesterday = day.minusDays(1)

    /**
     * 兜底路径（WorkManager 每 24h、以及打开 App 时的补发）也会发通知。
     * 它不看用户设定的时刻就会变成："我设了 20:00，结果凌晨 3 点弹"。
     * 所以到点之前不许发，这是这个设置项说真话的前提。
     */
    @Test fun `fallback does not post before the set time`() {
        assertFalse(shouldNotifyNow(at(2026, 9, 25, 8, 0), 9, 0, null))
        assertFalse(shouldNotifyNow(at(2026, 9, 25, 19, 59), 20, 0, yesterday))
    }

    @Test fun `fallback posts once the set time has passed`() {
        assertTrue(shouldNotifyNow(at(2026, 9, 25, 9, 1), 9, 0, null))
        // 闹钟被 ROM 杀掉、两小时后才醒：仍然补发
        assertTrue(shouldNotifyNow(at(2026, 9, 25, 11, 0), 9, 0, yesterday))
    }

    /** 今天已经报过一次就不再报第二遍（否则每次打开 App 都重弹一轮） */
    @Test fun `already notified today stays quiet`() {
        assertFalse(shouldNotifyNow(at(2026, 9, 25, 21, 0), 9, 0, day))
        // 昨天报过、今天到点 → 该报
        assertTrue(shouldNotifyNow(at(2026, 9, 25, 21, 0), 9, 0, yesterday))
    }
}
