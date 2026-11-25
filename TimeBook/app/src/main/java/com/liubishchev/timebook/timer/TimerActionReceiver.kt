package com.liubishchev.timebook.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.liubishchev.timebook.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 通知栏上的「结束计时」按钮：关掉那条记录，然后收掉前台服务和提醒。 */
class TimerActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_STOP_TIMER) return

        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.get(app)
                db.timeRecordDao().findRunning()?.let { running ->
                    db.timeRecordDao().close(
                        running.id,
                        maxOf(System.currentTimeMillis(), running.startAt)
                    )
                }
            } catch (_: Exception) {
                // 数据库暂时不可用时忽略，服务停止后状态会在下次打开 App 时以数据库为准
            } finally {
                TimerService.stop(app)
                NotificationManagerCompat.from(app).cancel(Notifications.ID_REMINDER)
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_STOP_TIMER = "com.liubishchev.timebook.action.STOP_TIMER_FROM_NOTIFICATION"
    }
}
