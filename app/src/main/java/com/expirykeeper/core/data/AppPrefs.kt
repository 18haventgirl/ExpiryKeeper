package com.expirykeeper.core.data

import android.content.Context
import java.time.LocalDate

/** 应用偏好读写（包 SharedPreferences），UI 直接读，写时即时生效 */
class AppPrefs(context: Context) {
    private val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** 动态取色（Material You）开关，仅 API31+ 实际生效，默认开 */
    var dynamicColor: Boolean
        get() = p.getBoolean("dynamicColor", true)
        set(v) = p.edit().putBoolean("dynamicColor", v).apply()

    /**
     * 每日提醒时刻，默认 9:00。存两个 int 而不是 "HH:mm" 字符串：省一次解析，也没有格式歧义。
     * 读取端不校验范围——[com.expirykeeper.core.domain.nextDailyFire] 会钳，
     * 闹钟宁可偏到 0:00/23:59 也不该因为脏数据而整个不再排。
     */
    var reminderHour: Int
        get() = p.getInt("reminderHour", 9)
        set(v) = p.edit().putInt("reminderHour", v).apply()

    var reminderMinute: Int
        get() = p.getInt("reminderMinute", 0)
        set(v) = p.edit().putInt("reminderMinute", v).apply()

    /**
     * 最近一次真正弹出每日提醒的日期。兜底路径（WorkManager 24h、打开 App 补发）靠它
     * 避免同一天弹第二遍；null = 从未弹过。
     */
    var lastNotifiedDay: LocalDate?
        get() = if (p.contains("lastNotifiedDay")) LocalDate.ofEpochDay(p.getLong("lastNotifiedDay", 0)) else null
        set(v) {
            val e = p.edit()
            if (v == null) e.remove("lastNotifiedDay") else e.putLong("lastNotifiedDay", v.toEpochDay())
            e.apply()
        }
}
