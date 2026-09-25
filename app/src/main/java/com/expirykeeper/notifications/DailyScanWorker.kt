package com.expirykeeper.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import com.expirykeeper.core.domain.ReminderEngine
import java.time.LocalDate

class DailyScanWorker(context: Context, params: androidx.work.WorkerParameters) :
    CoroutineWorker(context, params) {

    companion object {
        /** true = 走"到点才发"的闸（24h 兜底）；false = 闹钟正点或用户刚改过数据，当场刷新 */
        const val KEY_RESPECT_SCHEDULE = "respectSchedule"
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as com.expirykeeper.App
        val items = app.container.repository.getAll()
        NotificationHelper.postDailyReminders(
            applicationContext,
            ReminderEngine.computeForDate(items, LocalDate.now()),
            respectSchedule = inputData.getBoolean(KEY_RESPECT_SCHEDULE, true),
        )
        return Result.success()
    }
}
