package com.expirykeeper.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** 每天 9:00 精确闹钟 + 每 24h WorkManager 兜底（闹钟被 ROM 杀掉也能补发） */
object ReminderScheduler {
    private const val HOUR = 9
    private const val WORK_NAME = "daily-scan"

    fun schedule(context: Context) {
        scheduleAlarm(context)
        scheduleFallback(context)
    }

    fun runNow(context: Context) {
        val request = androidx.work.OneTimeWorkRequestBuilder<DailyScanWorker>().build()
        WorkManager.getInstance(context).enqueue(request)
    }

    private fun scheduleAlarm(context: Context) {
        if (!canScheduleExact(context)) return
        val manager = context.getSystemService(AlarmManager::class.java)
        val today9 = LocalDateTime.of(LocalDate.now(), LocalTime.of(HOUR, 0))
        val next = if (LocalDateTime.now().isBefore(today9)) today9 else today9.plusDays(1)
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, alarmIntent(context))
    }

    /** API 31 起才需要精确闹钟权限；31 以下系统始终允许精确闹钟 */
    private fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun alarmIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 0, Intent(context, ReminderAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun scheduleFallback(context: Context) {
        val request = PeriodicWorkRequestBuilder<DailyScanWorker>(Duration.ofDays(1))
            .setConstraints(Constraints())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request,
        )
    }
}
