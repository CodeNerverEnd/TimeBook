package com.liubishchev.timebook.data

import androidx.room.Entity

/**
 * 计划目标：某个周期内「某个标签」或「某个具体事项」打算花多少时间。
 *
 * [targetType] 只有两种：
 *  - TAG：标签级目标，算作本期总目标；
 *  - ITEM：具体事项目标。所属标签也有目标时按「拆解」处理，不重复计入总目标。
 *
 * [periodType] 三种："MONTH" / "YEAR" / "FIVE"；
 * [periodKey] 月为 "2026-11"，年为 "2026"，五年为 "2026-2030"。
 *
 * 同一对象在同一周期只有一条（复合主键），改计划就是覆盖。
 */
@Entity(
    tableName = "plan_target",
    primaryKeys = ["refId", "targetType", "periodType", "periodKey"]
)
data class PlanTarget(
    val refId: Long,
    val targetType: String = TYPE_TAG,
    val periodType: String,
    val periodKey: String,
    val targetMinutes: Int
) {
    companion object {
        const val TYPE_TAG = "TAG"
        const val TYPE_ITEM = "ITEM"
    }
}
