package com.expirykeeper.notifications

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
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

    /** 渠道组（设置页可见的归类）与通知组（通知栏聚合）两把钥匙，各司其职 */
    private const val CHANNEL_GROUP_ID = "household"
    private const val CHANNEL_GROUP_NAME = "到期提醒"
    private const val GROUP_KEY = "com.expirykeeper.GROUP"

    /** 摘要通知固定 id：notifIdFor 恒为 >=0，取负值避免碰撞 */
    private const val SUMMARY_ID = -1

    /** 取消单条通知（快速操作后清掉对应 id 的既有通知） */
    fun cancel(ctx: Context, id: Int) {
        ctx.getSystemService(NotificationManager::class.java).cancel(id)
    }

    /**
     * 取消某物品所有可能的既有通知：notifId 是 (itemId, status, date) 纯函数，
     * 对全部 status × {today, today-1} 重算 cancel（走 entries 枚举，加新状态不必改这里；
     * LOW_STOCK 的 id 与日期无关，走 static 变体）。
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
        // minSdk 29：始终高于 O，渠道组直接建；createNotificationChannelGroup 幂等
        manager.createNotificationChannelGroup(
            NotificationChannelGroup(CHANNEL_GROUP_ID, CHANNEL_GROUP_NAME)
        )
        // createNotificationChannel 幂等；对既有渠道重新下发可更新其 group（regroup-on-update 合法），
        // 让已安装但未带 group 的开发设备渠道也归入 "household" 组
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "到期提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                group = CHANNEL_GROUP_ID
            }
        )
    }

    fun notifyAll(context: Context, reminders: List<Reminder>) {
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        // I-1 陈旧子通知清扫：本轮之前的活动通知里，凡属本提醒渠道、且不在本轮待办集合
        // （含组摘要 SUMMARY_ID）的一律取消——处理到只剩 1 件或清空时，落单的旧子通知不再滞留。
        // notifIdFor 是纯哈希无法反查全集，故以系统活动通知列表为准；cancelAll 会误伤前台/其它
        // 通知，绝不使用。
        val keepIds = reminders.map { it.notificationId }.toHashSet().apply { add(SUMMARY_ID) }
        manager.activeNotifications.forEach { act ->
            if (act.notification?.channelId == CHANNEL_ID && act.id !in keepIds) manager.cancel(act.id)
        }
        val grouped = reminders.size > 1 // 单条不挂组机制，避免摘要闪烁
        reminders.forEach { reminder ->
            val pi = PendingIntent.getActivity(
                context, reminder.notificationId, mainIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val snoozePi = PendingIntent.getBroadcast(
                context, reminder.notificationId,
                Intent(context, ReminderAlarmReceiver::class.java).apply {
                    action = ReminderAlarmReceiver.ACTION_SNOOZE
                    putExtra(ReminderAlarmReceiver.EXTRA_ITEM_ID, reminder.item.id)
                    putExtra(ReminderAlarmReceiver.EXTRA_NOTIF_ID, reminder.notificationId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val body = when (reminder.status) {
                DueStatus.DUE_SOON -> "「${reminder.item.name}」还有 ${reminder.daysLeft} 天到期"
                DueStatus.DUE_TODAY -> "「${reminder.item.name}」今天到期"
                DueStatus.OVERDUE -> "「${reminder.item.name}」已过期 ${reminder.overdueDays} 天，检查还能不能用"
                DueStatus.LOW_STOCK -> "「${reminder.item.name}」库存不足，该补货了"
                DueStatus.RENEWAL_SOON -> "「${reminder.item.name}」还有 ${reminder.daysLeft} 天扣费"
                DueStatus.RENEWAL_TODAY -> "「${reminder.item.name}」今天扣费，不需要就取消订阅"
                DueStatus.RENEWAL_OVERDUE -> "「${reminder.item.name}」扣费日已过 ${reminder.overdueDays} 天，没在用的话记得取消"
            }
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("${ReminderEngine.displayIcon(reminder.item, Categories.byId(reminder.item.categoryId)?.emoji ?: "📦")} ${reminder.item.name}")
                .setContentText(body)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .addAction(0, "稍后 3 天", snoozePi)
            if (grouped) builder.setGroup(GROUP_KEY)
            manager.notify(reminder.notificationId, builder.build())
        }
        if (grouped) {
            val summaryPi = PendingIntent.getActivity(
                context, SUMMARY_ID, mainIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            manager.notify(
                SUMMARY_ID,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setGroup(GROUP_KEY)
                    .setGroupSummary(true)
                    .setContentTitle("到期管家 · ${reminders.size} 件事需要处理")
                    .setContentText(reminders.take(3).joinToString("、") { it.item.name })
                    .setContentIntent(summaryPi)
                    .setAutoCancel(true)
                    .build()
            )
        } else {
            // 单条时若遗留上一轮的组摘要（如从 2 件处理剩 1 件），主动撤销避免「2 件事」滞留；
            // 每条处理路径最终都收敛到 runNow→notifyAll，故只需在此唯一 owner 处兜底清理
            manager.cancel(SUMMARY_ID)
        }
    }

    private fun mainIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
}
