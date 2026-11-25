package com.liubishchev.timebook.util

import com.liubishchev.timebook.data.AppDatabase
import org.json.JSONArray
import org.json.JSONObject

/**
 * 备份文件的唯一生成入口。
 *
 * 单独放出来是为了两件事共用一份格式：
 *  - 界面上的「导出备份」；
 *  - 自动备份（后台定时写文件，不经过界面）。
 *
 * 全部**直查 DAO**，不读界面 Flow —— 刚启动就导出时 Flow 可能还是空的，
 * 那会把备份导成空文件，等换手机时才发现，最要命。
 */
object BackupJson {

    /** 当前备份格式版本：3 = 加入了计划档案（每期总结 + 归档快照）。 */
    const val SCHEMA_VERSION = 3

    suspend fun build(db: AppDatabase): String {
        val root = JSONObject()
        root.put("app", "TimeBook")
        root.put("schemaVersion", SCHEMA_VERSION)
        root.put("exportedAt", System.currentTimeMillis())

        val tagsArray = JSONArray()
        db.tagDao().findAll().forEach { tag ->
            tagsArray.put(JSONObject().apply {
                put("id", tag.id)
                put("name", tag.name)
                put("colorArgb", tag.colorArgb)
                put("note", tag.note)
                put("archived", tag.archived)
                put("createdAt", tag.createdAt)
                put("sortOrder", tag.sortOrder)
            })
        }

        val plansArray = JSONArray()
        db.planTargetDao().findAll().forEach { plan ->
            plansArray.put(JSONObject().apply {
                put("refId", plan.refId)
                put("targetType", plan.targetType)
                put("periodType", plan.periodType)
                put("periodKey", plan.periodKey)
                put("targetMinutes", plan.targetMinutes)
            })
        }

        // 计划档案：每期的总结与归档快照，换手机后历史计划不会白丢
        val docsArray = JSONArray()
        db.planDocDao().findAll().forEach { doc ->
            docsArray.put(JSONObject().apply {
                put("periodType", doc.periodType)
                put("periodKey", doc.periodKey)
                put("status", doc.status)
                put("summary", doc.summary)
                if (doc.closedAt == null) put("closedAt", JSONObject.NULL) else put("closedAt", doc.closedAt)
                put("snapshot", parseSnapshotJson(doc.snapshotJson))
            })
        }

        val linksArray = JSONArray()
        db.tagDao().findAllLinks().forEach { link ->
            linksArray.put(JSONObject().apply {
                put("itemId", link.itemId)
                put("tagId", link.tagId)
            })
        }

        val itemsArray = JSONArray()
        db.taskItemDao().findAll().forEach { item ->
            itemsArray.put(JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("colorArgb", item.colorArgb)
                put("note", item.note)
                put("archived", item.archived)
                put("createdAt", item.createdAt)
                put("sortOrder", item.sortOrder)
                put("targetMinutes", item.targetMinutes)
            })
        }

        val recordsArray = JSONArray()
        db.timeRecordDao().findAll().forEach { record ->
            recordsArray.put(JSONObject().apply {
                put("id", record.id)
                put("itemId", record.itemId)
                put("startAt", record.startAt)
                if (record.endAt == null) put("endAt", JSONObject.NULL) else put("endAt", record.endAt)
                put("note", record.note)
                put("targetMinutes", record.targetMinutes)
                if (record.remindAt == null) put("remindAt", JSONObject.NULL) else put("remindAt", record.remindAt)
            })
        }

        root.put("tags", tagsArray)
        root.put("plans", plansArray)
        root.put("planDocs", docsArray)
        root.put("itemTags", linksArray)
        root.put("items", itemsArray)
        root.put("records", recordsArray)
        return root.toString(2)
    }

    /** 快照在库里是字符串，导出时还原成对象，让 JSON 仍是可读文本。 */
    private fun parseSnapshotJson(json: String): Any {
        if (json.isBlank()) return JSONObject.NULL
        return runCatching { JSONObject(json) }.getOrDefault(JSONObject.NULL)
    }

    /** 备份文件名：TimeBook-备份-20261125-1040.json（带时分，同一天多次备份不互相覆盖）。 */
    fun fileName(now: Long): String {
        val date = TimeFormat.toLocalDate(now)
        val time = TimeFormat.toLocalTime(now)
        return "TimeBook-备份-%04d%02d%02d-%02d%02d.json".format(
            date.year, date.monthValue, date.dayOfMonth, time.hour, time.minute
        )
    }

    const val FILE_PREFIX = "TimeBook-备份-"
}
