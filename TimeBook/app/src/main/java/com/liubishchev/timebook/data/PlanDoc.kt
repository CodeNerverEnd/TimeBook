package com.liubishchev.timebook.data

import androidx.room.Entity

/**
 * 计划档案：某一期计划的「生命周期」。
 *
 * 有这行记录才说明这一期被「写总结并归档」过：
 *  - [status] = CLOSED 时，该期的目标锁定，不能再改，只能在历史计划里看或取消归档；
 *  - [summary] 是手写的总结原文，允许为空（留空就只留自动统计）；
 *  - [snapshotJson] 是归档当时算出来的统计快照（每个标签、每个事项的目标与实际），
 *    存成快照而不是每次重算，是为了以后删了记录、改了数据，历史总结里的数字也不会变。
 *
 * 没有任何行 = 这一期还没结束过，属于「本期」。
 */
@Entity(tableName = "plan_doc", primaryKeys = ["periodType", "periodKey"])
data class PlanDoc(
    val periodType: String,
    val periodKey: String,
    val status: String = STATUS_ACTIVE,
    val summary: String = "",
    val closedAt: Long? = null,
    val snapshotJson: String = ""
) {
    val isClosed: Boolean get() = status == STATUS_CLOSED

    companion object {
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_CLOSED = "CLOSED"
    }
}
