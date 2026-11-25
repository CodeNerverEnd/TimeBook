package com.liubishchev.timebook.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 事项：柳比歇夫时间统计里的「事件类型」。
 *
 * [targetMinutes] 是这件事的默认目标时长（分钟）。默认 0 = 不设目标，也就不提醒。
 */
@Entity(tableName = "task_item")
data class TaskItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val colorArgb: Int,
    val note: String = "",
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0,
    val targetMinutes: Int = 0
)
