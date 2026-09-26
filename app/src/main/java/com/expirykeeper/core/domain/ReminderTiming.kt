package com.expirykeeper.core.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 每日提醒的下一次触发时刻：今天 HH:mm 还没到就今天，已到或已过就明天。
 *
 * 压线（now 正好等于设定时刻）算明天：用户刚把时间改成 9:00，那一刻不该立刻弹一条通知。
 * hour/minute 钳进合法区间而不是抛异常——它们存在 SharedPreferences 里，
 * 可能被旧版本写入的值或手工改动的数据塞进非法数字，闹钟不该因此整个不再排。
 */
fun nextDailyFire(now: LocalDateTime, hour: Int, minute: Int): LocalDateTime {
    val setting = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    val today = LocalDateTime.of(now.toLocalDate(), setting)
    return if (now.isBefore(today)) today else today.plusDays(1)
}

/**
 * 兜底路径（WorkManager 每 24h、打开 App 时的补发）此刻该不该发通知。
 *
 * 没有这道闸，"每天 20:00 提醒"就是个假设置：兜底任务会在它自己被调度的任何时刻
 * （凌晨 3 点、或用户随手打开 App 的下午）把通知弹出来。两条规则：
 * ① 没到点不发；② 今天已经发过就不再发第二遍。
 */
fun shouldNotifyNow(now: LocalDateTime, hour: Int, minute: Int, lastNotifiedDay: LocalDate?): Boolean {
    if (lastNotifiedDay == now.toLocalDate()) return false
    val setting = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    return !now.toLocalTime().isBefore(setting)
}
