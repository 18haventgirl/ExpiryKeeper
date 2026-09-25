package com.expirykeeper.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.expirykeeper.App
import com.expirykeeper.core.domain.ReminderEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class ReminderAlarmReceiver : BroadcastReceiver() {

    companion object {
        /** 通知「稍后 3 天」动作按钮回传的广播 action（NotificationHelper 构造 PendingIntent） */
        const val ACTION_SNOOZE = "com.expirykeeper.action.SNOOZE_REMINDER"
        const val EXTRA_ITEM_ID = "itemId"
        const val EXTRA_NOTIF_ID = "notificationId"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_SNOOZE) {
            val itemId = intent.getStringExtra(EXTRA_ITEM_ID) ?: return
            val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 0)
            val pending = goAsync()
            val app = context.applicationContext as App
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    app.container.repository.snooze(itemId, 3, LocalDate.now())
                    NotificationHelper.cancel(context, notifId)
                    // 同物品可能有多个日期变体的通知并存，一并清扫兄弟通知
                    NotificationHelper.cancelItem(context, itemId)
                    ReminderScheduler.runNow(context)
                } finally {
                    pending.finish()
                }
            }
            return
        }
        val pending = goAsync()
        val app = context.applicationContext as App
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val items = app.container.repository.getAll()
                // 闹钟本身就是"到点了"才响的，所以这条路不再过一遍时刻闸；
                // 记 lastNotifiedDay 由 postDailyReminders 统一负责，避免当天兜底再弹一遍。
                NotificationHelper.postDailyReminders(
                    context,
                    ReminderEngine.computeForDate(items, LocalDate.now()),
                    respectSchedule = false,
                )
                ReminderScheduler.schedule(context)
            } finally {
                pending.finish()
            }
        }
    }
}
