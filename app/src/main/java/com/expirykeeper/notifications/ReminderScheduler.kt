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
import com.expirykeeper.App
import com.expirykeeper.core.domain.nextDailyFire
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 每天在用户设定的时刻（默认 9:00）打精确闹钟 + 每 24h WorkManager 兜底（闹钟被 ROM 杀掉也能补发）。
 * 改完设置要重新调 [schedule] 才会换到新时刻——闹钟是一次性的，不重排就还在老点响。
 */
object ReminderScheduler {
    private const val WORK_NAME = "daily-scan"

    fun schedule(context: Context) {
        scheduleAlarm(context)
        scheduleFallback(context)
    }

    /**
     * 立刻跑一轮扫描。默认 **不**过"到点才发"的闸：调用方是用户刚改了数据（续期/延后/恢复）
     * 或点了通知按钮，此时当场刷新通知栏才是对的；到点判断只约束 24h 兜底那条路。
     */
    fun runNow(context: Context, respectSchedule: Boolean = false) {
        val request = androidx.work.OneTimeWorkRequestBuilder<DailyScanWorker>()
            .setInputData(
                androidx.work.workDataOf(DailyScanWorker.KEY_RESPECT_SCHEDULE to respectSchedule),
            )
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    private fun scheduleAlarm(context: Context) {
        if (!canScheduleExact(context)) return
        val prefs = (context.applicationContext as App).container.prefs
        val manager = context.getSystemService(AlarmManager::class.java)
        val next = nextDailyFire(LocalDateTime.now(), prefs.reminderHour, prefs.reminderMinute)
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
