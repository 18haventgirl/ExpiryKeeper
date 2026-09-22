package com.expirykeeper.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.expirykeeper.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val app = context.applicationContext as App
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val items = app.container.repository.getAll()
                NotificationHelper.notifyAll(context, ReminderEngine.computeForDate(items, LocalDate.now()))
                ReminderScheduler.schedule(context)
            } finally {
                pending.finish()
            }
        }
    }
}
