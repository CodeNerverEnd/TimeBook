package com.liubishchev.timebook.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 标签：事项的上层归类，比如「读书」「工作」「运动」。
 *
 * 一个标签下面可以有很多事项（读书 → 三体 / 人类简史 / …），
 * 一个事项也可以挂多个标签（「通勤听书」既算「读书」也算「通勤」）。
 * 统计既能按事项看，也能按标签汇总。
 */
@Entity(tableName = "tag")
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val colorArgb: Int,
    val note: String = "",
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0
)

/**
 * 事项 ↔ 标签 的多对多关联。
 * 删事项或删标签都会自动清掉对应关联（外键 CASCADE）。
 */
@Entity(
    tableName = "item_tag",
    primaryKeys = ["itemId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = TaskItem::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("itemId"), Index("tagId")]
)
data class ItemTag(
    val itemId: Long,
    val tagId: Long
)
