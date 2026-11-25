package com.liubishchev.timebook.timer

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.liubishchev.timebook.MainActivity
import com.liubishchev.timebook.R
import com.liubishchev.timebook.data.AppDatabase
import com.liubishchev.timebook.data.TimeRecord
import com.liubishchev.timebook.util.TimeFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 计时前台服务：
 *  1. 常驻通知显示「事项名 + 已用时间（系统计时器）+ 目标/提醒」
 *  2. 负责给「到点提醒」设定精确闹钟（锁屏、Doze 也能响）
 *  3. 自身不保存计时状态，一切都以数据库里那条 endAt 为空的记录为准
 */
class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var ticker: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Android 要求 5 秒内进入前台，先用占位通知顶上
        startForeground(
            Notifications.ID_ONGOING,
            buildOngoingNotification(itemName = null, record = null)
        )
        refresh()

        if (ticker == null) {
            ticker = scope.launch {
                while (isActive) {
                    delay(REFRESH_INTERVAL_MS)
                    refresh()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        ticker?.cancel()
        ticker = null
        cancelReminder()
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    // ------------------------------------------------------------------ 内部

    private fun refresh() {
        scope.launch {
            val db = AppDatabase.get(applicationContext)
            val record = db.timeRecordDao().findRunning()
            if (record == null) {
                stopSelf()
                return@launch
            }
            val item = db.taskItemDao().findById(record.itemId)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.notify(
                Notifications.ID_ONGOING,
                buildOngoingNotification(item?.name, record)
            )
            scheduleReminder(record, item?.name ?: "当前事项")
        }
    }

    private fun buildOngoingNotification(itemName: String?, record: TimeRecord?): Notification {
        val builder = NotificationCompat.Builder(this, Notifications.CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)

        if (record == null) {
            builder.setContentTitle(itemName ?: "TimeBook")
                .setContentText("正在恢复计时状态…")
        } else {
            val remindAt = record.remindAt
            val subtitle = when {
                remindAt != null && record.targetMinutes > 0 ->
                    "目标 ${record.targetMinutes} 分钟 · ${TimeFormat.hm(remindAt)} 提醒"

                remindAt != null -> "${TimeFormat.hm(remindAt)} 提醒"
                else -> "未设提醒"
            }
            builder.setContentTitle(itemName ?: "计时中")
                .setContentText(subtitle)
                .setWhen(record.startAt)
                .setUsesChronometer(true)
                .setShowWhen(true)
                .addAction(0, "结束计时", stopTimerIntent())
        }
        return builder.build()
    }

    private fun scheduleReminder(record: TimeRecord, itemName: String) {
        val at = record.remindAt ?: return
        if (at <= System.currentTimeMillis()) return

        val manager = getSystemService(AlarmManager::class.java) ?: return
        val pending = reminderPendingIntent(record, itemName)

        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            manager.canScheduleExactAlarms()
        if (exactAllowed) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } else {
            // 没给精确闹钟权限时退化为不精确提醒（可能晚几分钟），App 内会提示去开启
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    private fun cancelReminder() {
        val manager = getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(this, ReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            this,
            REQ_REMINDER,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pending != null) {
            manager.cancel(pending)
            pending.cancel()
        }
    }

    private fun reminderPendingIntent(record: TimeRecord, itemName: String): PendingIntent {
        val intent = Intent(this, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_ITEM_NAME, itemName)
            putExtra(ReminderReceiver.EXTRA_TARGET_MINUTES, record.targetMinutes)
            putExtra(ReminderReceiver.EXTRA_START_AT, record.startAt)
            putExtra(ReminderReceiver.EXTRA_REMIND_AT, record.remindAt ?: 0L)
        }
        return PendingIntent.getBroadcast(
            this,
            REQ_REMINDER,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun stopTimerIntent(): PendingIntent = PendingIntent.getBroadcast(
        this,
        1,
        Intent(this, TimerActionReceiver::class.java).setAction(TimerActionReceiver.ACTION_STOP_TIMER),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        private const val ACTION_START = "com.liubishchev.timebook.action.START_TIMER"
        private const val ACTION_STOP = "com.liubishchev.timebook.action.STOP_TIMER"
        private const val REQ_REMINDER = 2001
        private const val REFRESH_INTERVAL_MS = 20_000L

        /** 幂等：服务没在跑就拉起来，在跑就刷新通知 + 重新排提醒。 */
        fun start(context: Context) {
            val intent = Intent(context, TimerService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TimerService::class.java))
        }
    }
}
