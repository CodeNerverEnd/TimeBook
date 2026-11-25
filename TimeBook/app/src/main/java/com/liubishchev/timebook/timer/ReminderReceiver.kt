package com.liubishchev.timebook.timer

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.liubishchev.timebook.MainActivity
import com.liubishchev.timebook.R
import com.liubishchev.timebook.util.TimeFormat

/**
 * 到点提醒的落地点。由 AlarmManager 精确闹钟触发，即使 App 已被划掉、屏幕锁着也能发通知。
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Notifications.ensureChannels(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val itemName = intent.getStringExtra(EXTRA_ITEM_NAME) ?: "当前事项"
        val targetMinutes = intent.getIntExtra(EXTRA_TARGET_MINUTES, 0)
        val startAt = intent.getLongExtra(EXTRA_START_AT, 0L)
        val remindAt = intent.getLongExtra(EXTRA_REMIND_AT, 0L)
        val used = (System.currentTimeMillis() - startAt).coerceAtLeast(0L)

        val headline = if (targetMinutes > 0) {
            "已达到 $targetMinutes 分钟目标"
        } else {
            "到 ${TimeFormat.hm(remindAt)} 了"
        }
        val body = "$headline，已用 ${TimeFormat.human(used)}。计时仍在继续，结束后回来结算。"

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopTimer = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, TimerActionReceiver::class.java)
                .setAction(TimerActionReceiver.ACTION_STOP_TIMER),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("时间到 · $itemName")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .addAction(0, "结束计时", stopTimer)
            .build()

        NotificationManagerCompat.from(context).notify(Notifications.ID_REMINDER, notification)
    }

    companion object {
        const val EXTRA_ITEM_NAME = "extra_item_name"
        const val EXTRA_TARGET_MINUTES = "extra_target_minutes"
        const val EXTRA_START_AT = "extra_start_at"
        const val EXTRA_REMIND_AT = "extra_remind_at"
    }
}
