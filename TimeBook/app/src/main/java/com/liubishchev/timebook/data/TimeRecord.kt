package com.liubishchev.timebook.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一条时间开销记录。
 *
 * endAt 为 null 表示「正在计时」，因此杀掉进程 / 重启手机都不会丢失计时：
 * 时长始终用 endAt（或当前时间）- startAt 实时算出来。
 *
 * 提醒作为快照存在记录里，之后改事项目标不会影响历史：
 *  - [targetMinutes] > 0：做满 N 分钟提醒，[remindAt] = startAt + N 分钟
 *  - [targetMinutes] == 0 且 [remindAt] != null：到某个钟点提醒
 *  - 两者都空：不提醒
 */
@Entity(
    tableName = "time_record",
    foreignKeys = [
        ForeignKey(
            entity = TaskItem::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("itemId"), Index("startAt")]
)
data class TimeRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val itemId: Long,
    val startAt: Long,
    val endAt: Long? = null,
    val note: String = "",
    val targetMinutes: Int = 0,
    val remindAt: Long? = null
) {
    fun millisAt(now: Long): Long = ((endAt ?: now) - startAt).coerceAtLeast(0L)

    val isRunning: Boolean get() = endAt == null
}
