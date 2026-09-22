package com.expirykeeper.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.expirykeeper.MainActivity
import com.expirykeeper.R
import com.expirykeeper.core.data.Categories
import com.expirykeeper.core.domain.DueStatus
import com.expirykeeper.core.domain.Reminder
import com.expirykeeper.core.domain.ReminderEngine
import java.time.LocalDate

object NotificationHelper {
    const val CHANNEL_ID = "expiry_reminders"

    /** 取消单条通知（快速操作后清掉对应 id 的既有通知） */
    fun cancel(ctx: Context, id: Int) {
        ctx.getSystemService(NotificationManager::class.java).cancel(id)
    }

    /**
     * 取消某物品所有可能的既有通知：notifId 是 (itemId, status, date) 纯函数，
     * 对 6 种 status × {today, today-1} 重算 cancel（LOW_STOCK 的 id 与日期无关，走 static 变体）。
     */
    fun cancelItem(ctx: Context, itemId: String) {
        val today = LocalDate.now()
        DueStatus.entries.forEach { status ->
            if (status == DueStatus.LOW_STOCK) {
                cancel(ctx, ReminderEngine.notifIdFor(itemId, status, null))
            } else {
                cancel(ctx, ReminderEngine.notifIdFor(itemId, status, today))
                cancel(ctx, ReminderEngine.notifIdFor(itemId, status, today.minusDays(1)))
            }
        }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "到期提醒", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    fun notifyAll(context: Context, reminders: List<Reminder>) {
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        reminders.forEach { reminder ->
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(context, reminder.notificationId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val body = when (reminder.status) {
                DueStatus.DUE_SOON -> "「${reminder.item.name}」还有 ${reminder.daysLeft} 天到期"
                DueStatus.DUE_TODAY -> "「${reminder.item.name}」今天到期"
                DueStatus.OVERDUE -> "「${reminder.item.name}」已过期 ${reminder.overdueDays} 天，检查还能不能用"
                DueStatus.LOW_STOCK -> "「${reminder.item.name}」库存不足，该补货了"
                DueStatus.RENEWAL_SOON -> "「${reminder.item.name}」还有 ${reminder.daysLeft} 天扣费"
                DueStatus.RENEWAL_TODAY -> "「${reminder.item.name}」今天扣费，不需要就取消订阅"
            }
            manager.notify(
                reminder.notificationId,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("${ReminderEngine.displayIcon(reminder.item, Categories.byId(reminder.item.categoryId)?.emoji ?: "📦")} 到期管家")
                    .setContentText(body)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build()
            )
        }
    }
}
