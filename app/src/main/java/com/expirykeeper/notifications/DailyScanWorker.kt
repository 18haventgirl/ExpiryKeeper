package com.expirykeeper.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import com.expirykeeper.core.domain.ReminderEngine
import java.time.LocalDate

class DailyScanWorker(context: Context, params: androidx.work.WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as com.expirykeeper.App
        val items = app.container.repository.getAll()
        NotificationHelper.notifyAll(applicationContext, ReminderEngine.computeForDate(items, LocalDate.now()))
        return Result.success()
    }
}
