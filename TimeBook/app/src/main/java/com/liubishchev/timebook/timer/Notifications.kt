package com.liubishchev.timebook.timer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/** 通知渠道 / ID / 权限相关的小工具。 */
object Notifications {

    const val CHANNEL_ONGOING = "timer_ongoing"
    const val CHANNEL_REMINDER = "timer_reminder"

    const val ID_ONGOING = 1001
    const val ID_REMINDER = 1002

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        if (manager.getNotificationChannel(CHANNEL_ONGOING) == null) {
            val channel = NotificationChannel(
                CHANNEL_ONGOING,
                "计时中",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "常驻显示正在计时的事项与已用时间"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        }

        if (manager.getNotificationChannel(CHANNEL_REMINDER) == null) {
            val channel = NotificationChannel(
                CHANNEL_REMINDER,
                "到点提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "做满目标时长、或到达指定时刻时提醒"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                setShowBadge(true)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
