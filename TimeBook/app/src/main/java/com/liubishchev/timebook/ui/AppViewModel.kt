package com.liubishchev.timebook.ui

import android.app.AlarmManager
import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.liubishchev.timebook.data.AppDatabase
import com.liubishchev.timebook.data.ItemTag
import com.liubishchev.timebook.data.PlanDoc
import com.liubishchev.timebook.data.PlanTarget
import com.liubishchev.timebook.data.Tag
import com.liubishchev.timebook.data.TaskItem
import com.liubishchev.timebook.data.TimeRecord
import com.liubishchev.timebook.timer.TimerService
import com.liubishchev.timebook.util.AutoBackup
import com.liubishchev.timebook.util.BackupJson
import com.liubishchev.timebook.util.BackupPrefs
import com.liubishchev.timebook.util.PlanPeriod
import com.liubishchev.timebook.util.TimeFormat
import com.liubishchev.timebook.util.computePlan
import com.liubishchev.timebook.util.planPeriodKey
import com.liubishchev.timebook.util.shiftPeriod
import com.liubishchev.timebook.util.snapshotOf
import com.liubishchev.timebook.util.toJson
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.time.LocalDate
import org.json.JSONObject

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val itemDao = db.taskItemDao()
    private val recordDao = db.timeRecordDao()
    private val tagDao = db.tagDao()
    private val planDao = db.planTargetDao()
    private val planDocDao = db.planDocDao()

    /** 数据量是「一个人一辈子」级别，直接全量放内存，统计和列表都不必再查库。 */
    val items: StateFlow<List<TaskItem>> =
        itemDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val records: StateFlow<List<TimeRecord>> =
        recordDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val tags: StateFlow<List<Tag>> =
        tagDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val planTargets: StateFlow<List<PlanTarget>> =
        planDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 计划档案：只有归档过的期才有记录。 */
    val planDocs: StateFlow<List<PlanDoc>> =
        planDocDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 事项 ↔ 标签 关联。用 Map<事项id, List<标签id>> 方便界面直接查。 */
    val tagIdsByItem: StateFlow<Map<Long, List<Long>>> = tagDao.observeLinks()
        .map { links ->
            links.groupBy({ it.itemId }, { it.tagId })
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** 全局唯一的进行中记录。 */
    val running: StateFlow<TimeRecord?> = records
        .map { list -> list.firstOrNull { it.endAt == null } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /**
     * 刚刚结束的那条记录 id（非空 = 界面该弹「这次做了什么」备注面板）。
     *
     * 只在用户主动点「结束计时」时置位；切换事项、通知栏结束都不置位，
     * 避免正在开始下一件事时被打断。界面消费后调 [consumeStoppedRecord] 清空。
     */
    private val _stoppedRecordId = MutableStateFlow<Long?>(null)
    val stoppedRecordId: StateFlow<Long?> = _stoppedRecordId.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                _now.value = System.currentTimeMillis()
                delay(1000L)
            }
        }
        // App 被划掉后重开：数据库里还有进行中的记录，就把前台服务和提醒恢复起来
        viewModelScope.launch {
            if (recordDao.findRunning() != null) {
                runCatching { TimerService.start(getApplication()) }
            }
        }
        // 自动备份：距上次满 24 小时才写一次，不引 WorkManager，不额外耗电
        viewModelScope.launch {
            runCatching { AutoBackup.maybeRun(getApplication()) }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    /** 供界面层（导入 / 导出结果）直接提示。 */
    fun notify(text: String) {
        _message.value = text
    }

    // ---------------------------------------------------------------- 计时

    /** 开始计时。若已有进行中的记录，先自动结束它，再开新的（同一时刻只允许一件事）。 */
    fun startTimer(itemId: Long) {
        viewModelScope.launch {
            val ts = System.currentTimeMillis()
            recordDao.findRunning()?.let { recordDao.close(it.id, maxOf(ts, it.startAt)) }

            val item = itemDao.findById(itemId)
            val minutes = item?.targetMinutes ?: 0
            recordDao.insert(
                TimeRecord(
                    itemId = itemId,
                    startAt = ts,
                    targetMinutes = minutes,
                    remindAt = if (minutes > 0) ts + minutes * 60_000L else null
                )
            )
            runCatching { TimerService.start(getApplication()) }

            if (minutes > 0) {
                val tail = if (canScheduleExactAlarms()) "" else "（系统未允许精确闹钟，可能延迟）"
                notify("开始「${item?.name ?: "事项"}」，${minutes} 分钟后提醒$tail")
            }
        }
    }

    fun stopTimer() {
        viewModelScope.launch {
            val ts = System.currentTimeMillis()
            val runningRecord = recordDao.findRunning() ?: return@launch
            val end = maxOf(ts, runningRecord.startAt)
            recordDao.close(runningRecord.id, end)
            runCatching { TimerService.stop(getApplication()) }

            val remindAt = runningRecord.remindAt
            val tail = when {
                remindAt == null -> ""
                end >= remindAt -> "，已过提醒点"
                else -> ""
            }
            notify("已记录 ${TimeFormat.human(end - runningRecord.startAt)}$tail")
            _stoppedRecordId.value = runningRecord.id
        }
        viewModelScope.launch { runCatching { AutoBackup.maybeRun(getApplication()) } }
    }

    fun consumeStoppedRecord() {
        _stoppedRecordId.value = null
    }

    /** 只改备注，不动时间、不碰提醒；进行中的记录也能改。 */
    fun updateRecordNote(id: Long, note: String) {
        val text = note.trim()
        viewModelScope.launch {
            val record = recordDao.findById(id) ?: return@launch
            if (record.note == text) return@launch
            recordDao.update(record.copy(note = text))
            notify(if (text.isEmpty()) "备注已清空" else "备注已保存")
        }
    }

    /**
     * 设定 / 修改「本次提醒」。
     *
     * @param targetMinutes 做满 N 分钟提醒；0 表示不是时长型（用 remindAt 表达时刻）
     * @param remindAt 绝对提醒时刻；null 表示不提醒
     */
    fun setReminder(record: TimeRecord, targetMinutes: Int, remindAt: Long?) {
        viewModelScope.launch {
            recordDao.update(record.copy(targetMinutes = targetMinutes, remindAt = remindAt))
            runCatching { TimerService.start(getApplication()) }

            if (remindAt != null) {
                val tail = if (canScheduleExactAlarms()) "" else "（系统未允许精确闹钟，可能延迟）"
                notify("将在 ${TimeFormat.hm(remindAt)} 提醒您$tail")
            } else {
                notify("已清除提醒")
            }
        }
    }

    /** Android 12 起精确闹钟需要单独授权，没授权就退化成「可能晚几分钟」。 */
    private fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = getApplication<Application>().getSystemService(AlarmManager::class.java)
            ?: return false
        return manager.canScheduleExactAlarms()
    }

    // ---------------------------------------------------------------- 记录

    fun addRecord(itemId: Long, startAt: Long, endAt: Long, note: String) {
        if (itemId <= 0L) {
            notify("请先选择事项")
            return
        }
        if (endAt <= startAt) {
            notify("结束时间必须晚于开始时间")
            return
        }
        viewModelScope.launch {
            recordDao.insert(
                TimeRecord(itemId = itemId, startAt = startAt, endAt = endAt, note = note.trim())
            )
            notify("已补录 ${TimeFormat.human(endAt - startAt)}")
        }
    }

    fun updateRecord(record: TimeRecord) {
        if (record.endAt != null && record.endAt <= record.startAt) {
            notify("结束时间必须晚于开始时间")
            return
        }
        viewModelScope.launch {
            recordDao.update(record)
            // 改的正好是进行中那条（比如调了提醒），让服务按新数据重排通知和闹钟
            if (record.endAt == null) {
                runCatching { TimerService.start(getApplication()) }
            }
        }
    }

    fun deleteRecord(id: Long) {
        viewModelScope.launch {
            recordDao.deleteById(id)
            if (recordDao.findRunning() == null) {
                runCatching { TimerService.stop(getApplication()) }
            }
            notify("记录已删除")
        }
    }

    // ---------------------------------------------------------------- 事项

    fun saveItem(
        existing: TaskItem?,
        name: String,
        colorArgb: Int,
        note: String,
        targetMinutes: Int,
        tagIds: List<Long>
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            notify("事项名称不能为空")
            return
        }
        if (tagIds.isEmpty()) {
            notify("至少选一个标签，比如「读书」「工作」")
            return
        }
        val target = targetMinutes.coerceAtLeast(0)
        viewModelScope.launch {
            db.withTransaction {
                val itemId = if (existing == null) {
                    itemDao.insert(
                        TaskItem(
                            name = trimmed,
                            colorArgb = colorArgb,
                            note = note.trim(),
                            targetMinutes = target
                        )
                    )
                } else {
                    itemDao.update(
                        existing.copy(
                            name = trimmed,
                            colorArgb = colorArgb,
                            note = note.trim(),
                            targetMinutes = target
                        )
                    )
                    existing.id
                }
                tagDao.unlinkItem(itemId)
                tagDao.linkAll(tagIds.distinct().map { ItemTag(itemId, it) })
            }
            if (existing == null) {
                notify(
                    if (target > 0) "已新建「$trimmed」，默认目标 $target 分钟"
                    else "已新建事项「$trimmed」"
                )
            } else {
                notify("事项已更新")
            }
        }
    }

    // ---------------------------------------------------------------- 标签

    fun saveTag(existing: Tag?, name: String, colorArgb: Int, note: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            notify("标签名称不能为空")
            return
        }
        viewModelScope.launch {
            if (existing == null) {
                tagDao.insert(Tag(name = trimmed, colorArgb = colorArgb, note = note.trim()))
                notify("已新建标签「$trimmed」")
            } else {
                tagDao.update(existing.copy(name = trimmed, colorArgb = colorArgb, note = note.trim()))
                notify("标签已更新")
            }
        }
    }

    /**
     * 删除标签只解开「事项 ↔ 标签」的关联，事项本身和记录都保留。
     * 删完若某个事项一个标签都不剩，界面会把它显示在「未打标签」里，可以再补标签。
     */
    fun deleteTag(tag: Tag, itemCount: Int) {
        viewModelScope.launch {
            planDao.deleteByRef(tag.id, PlanTarget.TYPE_TAG)
            tagDao.delete(tag)
            notify(
                if (itemCount > 0) "已删除标签「${tag.name}」，$itemCount 个事项仍保留，只是不再挂这个标签"
                else "已删除标签「${tag.name}」"
            )
        }
    }

    fun setArchived(item: TaskItem, archived: Boolean) {
        viewModelScope.launch {
            itemDao.update(item.copy(archived = archived))
            notify(if (archived) "「${item.name}」已归档，历史记录保留" else "「${item.name}」已恢复")
        }
    }

    /** 删除事项会连带删除它的全部记录（外键 CASCADE），界面层必须二次确认。 */
    fun deleteItem(item: TaskItem) {
        viewModelScope.launch {
            planDao.deleteByRef(item.id, PlanTarget.TYPE_ITEM)
            itemDao.delete(item)
            if (recordDao.findRunning() == null) {
                runCatching { TimerService.stop(getApplication()) }
            }
            notify("已删除事项「${item.name}」及其记录")
        }
    }

    // ---------------------------------------------------------------- 计划

    /**
     * 设定某标签 / 某事项在某周期的计划时长（分钟；0 = 清除该条计划）。
     *
     * @param targetType PlanTarget.TYPE_TAG（标签级）或 TYPE_ITEM（事项级）
     */
    fun setPlanTarget(
        targetType: String,
        refId: Long,
        period: PlanPeriod,
        date: LocalDate,
        minutes: Int
    ) {
        viewModelScope.launch {
            val key = planPeriodKey(period, date)
            if (minutes <= 0) {
                planDao.deleteOne(refId, targetType, period.type, key)
            } else {
                planDao.upsert(PlanTarget(refId, targetType, period.type, key, minutes))
            }
            val name = if (targetType == PlanTarget.TYPE_ITEM) {
                items.value.firstOrNull { it.id == refId }?.name ?: "事项"
            } else {
                tags.value.firstOrNull { it.id == refId }?.name ?: "标签"
            }
            notify(
                if (minutes <= 0) "已清除「$name」的计划"
                else "「$name」计划 ${TimeFormat.human(minutes * 60_000L)}"
            )
        }
    }

    /** 把上一期的计划整体复制到本期（标签目标和事项目标一起，本期已有的会被覆盖）。 */
    fun copyPreviousPlan(period: PlanPeriod, date: LocalDate) {
        viewModelScope.launch {
            val prevDate = shiftPeriod(period, date, -1)
            val prevKey = planPeriodKey(period, prevDate)
            val prev = planDao.findPeriod(period.type, prevKey).filter { it.targetMinutes > 0 }
            if (prev.isEmpty()) {
                notify("上一期没有设置过计划")
                return@launch
            }
            val key = planPeriodKey(period, date)
            planDao.upsertAll(prev.map { it.copy(periodKey = key) })
            val nTag = prev.count { it.targetType == PlanTarget.TYPE_TAG }
            val nItem = prev.size - nTag
            notify("已沿用上一期计划（$nTag 个标签${if (nItem > 0) " / $nItem 个事项" else ""}）")
        }
    }

    /**
     * 结束本期：把当前统计定格成快照，连同总结一起归档。
     * 归档后该期目标锁定（不能再改），但记录照常可查可改。
     */
    fun closePlanPeriod(period: PlanPeriod, date: LocalDate, summary: String) {
        viewModelScope.launch {
            val key = planPeriodKey(period, date)
            val overview = computePlan(
                period = period,
                date = date,
                tags = tagDao.findAll(),
                links = tagDao.findAllLinks(),
                items = itemDao.findAll(),
                records = recordDao.findAll(),
                planTargets = planDao.findAll(),
                now = System.currentTimeMillis()
            )
            if (!overview.hasPlan && overview.totalUsedMillis <= 0L) {
                notify("本期还没有计划或记录，不用归档")
                return@launch
            }
            val snapshot = snapshotOf(overview)
            planDocDao.upsert(
                PlanDoc(
                    periodType = period.type,
                    periodKey = key,
                    status = PlanDoc.STATUS_CLOSED,
                    summary = summary.trim(),
                    closedAt = System.currentTimeMillis(),
                    snapshotJson = snapshot.toJson()
                )
            )
            notify("「${overview.periodLabel}」已归档，共 ${snapshot.rows.size} 个标签")
        }
    }

    /** 改已归档那期的总结（不动快照，数字仍是归档当时的）。 */
    fun updatePlanSummary(periodType: String, periodKey: String, summary: String) {
        viewModelScope.launch {
            val doc = planDocDao.find(periodType, periodKey) ?: return@launch
            planDocDao.upsert(doc.copy(summary = summary.trim()))
            notify("总结已保存")
        }
    }

    /** 取消归档：回到「本期」，总结保留，目标重新可改。 */
    fun reopenPlan(periodType: String, periodKey: String) {
        viewModelScope.launch {
            planDocDao.delete(periodType, periodKey)
            notify("已取消归档，回到本期")
        }
    }

    // ---------------------------------------------------------------- 备份

    /**
     * 导出备份。**直查 DAO**，不依赖界面 Flow 是否已经收到数据
     * （刚启动就点导出时，Flow 可能还是空列表，会把备份导成空文件）。
     */
    suspend fun exportJson(): String = BackupJson.build(db)

    /** 上次备份时间（毫秒；0 = 从未备份）。 */
    fun lastBackupAt(): Long = BackupPrefs.lastBackupAt(getApplication())

    /** 导入会整体覆盖本机数据，界面层必须二次确认。 */
    fun importJson(text: String) {
        viewModelScope.launch {
            try {
                val root = JSONObject(text)
                val itemsArray = root.optJSONArray("items") ?: JSONArray()
                val recordsArray = root.optJSONArray("records") ?: JSONArray()
                val tagsArray = root.optJSONArray("tags") ?: JSONArray()
                val linksArray = root.optJSONArray("itemTags") ?: JSONArray()
                val plansArray = root.optJSONArray("plans") ?: JSONArray()
                val docsArray = root.optJSONArray("planDocs") ?: JSONArray()

                // 兼容 v2 备份（只有 tagId、没有 targetType）：一律按标签级目标导入
                val newPlans = ArrayList<PlanTarget>(plansArray.length())
                for (i in 0 until plansArray.length()) {
                    val o = plansArray.getJSONObject(i)
                    val type = o.optString("targetType", PlanTarget.TYPE_TAG)
                    newPlans.add(
                        PlanTarget(
                            refId = o.optLong("refId", o.optLong("tagId")),
                            targetType = if (type == PlanTarget.TYPE_ITEM) type else PlanTarget.TYPE_TAG,
                            periodType = o.optString("periodType"),
                            periodKey = o.optString("periodKey"),
                            targetMinutes = o.optInt("targetMinutes", 0)
                        )
                    )
                }

                // v3 才有计划档案；v2 备份里没有就跳过，计划目标照旧恢复
                val newDocs = ArrayList<PlanDoc>(docsArray.length())
                for (i in 0 until docsArray.length()) {
                    val o = docsArray.getJSONObject(i)
                    val snapshot = o.optJSONObject("snapshot")
                    newDocs.add(
                        PlanDoc(
                            periodType = o.optString("periodType"),
                            periodKey = o.optString("periodKey"),
                            status = o.optString("status", PlanDoc.STATUS_CLOSED),
                            summary = o.optString("summary"),
                            closedAt = if (o.isNull("closedAt")) null else o.optLong("closedAt"),
                            snapshotJson = snapshot?.toString().orEmpty()
                        )
                    )
                }

                val newTags = ArrayList<Tag>(tagsArray.length())
                for (i in 0 until tagsArray.length()) {
                    val o = tagsArray.getJSONObject(i)
                    newTags.add(
                        Tag(
                            id = o.optLong("id"),
                            name = o.optString("name"),
                            colorArgb = o.optInt("colorArgb", AppDatabase.DEFAULT_TAG_COLOR),
                            note = o.optString("note"),
                            archived = o.optBoolean("archived", false),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                            sortOrder = o.optInt("sortOrder", 0)
                        )
                    )
                }

                val newItems = ArrayList<TaskItem>(itemsArray.length())
                for (i in 0 until itemsArray.length()) {
                    val o = itemsArray.getJSONObject(i)
                    newItems.add(
                        TaskItem(
                            id = o.optLong("id"),
                            name = o.optString("name"),
                            colorArgb = o.optInt("colorArgb", DEFAULT_ITEM_COLOR),
                            note = o.optString("note"),
                            archived = o.optBoolean("archived", false),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                            sortOrder = o.optInt("sortOrder", 0),
                            targetMinutes = o.optInt("targetMinutes", 0)
                        )
                    )
                }

                val newRecords = ArrayList<TimeRecord>(recordsArray.length())
                for (i in 0 until recordsArray.length()) {
                    val o = recordsArray.getJSONObject(i)
                    val endAt = if (o.isNull("endAt")) null else o.optLong("endAt")
                    val remindAt = if (o.isNull("remindAt")) null else o.optLong("remindAt")
                    newRecords.add(
                        TimeRecord(
                            id = o.optLong("id"),
                            itemId = o.optLong("itemId"),
                            startAt = o.optLong("startAt"),
                            endAt = endAt,
                            note = o.optString("note"),
                            targetMinutes = o.optInt("targetMinutes", 0),
                            remindAt = remindAt
                        )
                    )
                }

                val newLinks = ArrayList<ItemTag>(linksArray.length())
                for (i in 0 until linksArray.length()) {
                    val o = linksArray.getJSONObject(i)
                    newLinks.add(ItemTag(o.optLong("itemId"), o.optLong("tagId")))
                }

                db.withTransaction {
                    recordDao.deleteAll()
                    tagDao.deleteAllLinks()
                    itemDao.deleteAll()
                    tagDao.deleteAll()
                    planDao.deleteAll()
                    planDocDao.deleteAll()
                    if (newTags.isNotEmpty()) tagDao.insertAll(newTags)
                    itemDao.insertAll(newItems)
                    recordDao.insertAll(newRecords)
                    if (newPlans.isNotEmpty()) planDao.upsertAll(newPlans)
                    if (newDocs.isNotEmpty()) planDocDao.upsertAll(newDocs)
                    if (newLinks.isNotEmpty()) {
                        tagDao.linkAll(newLinks)
                    } else if (newItems.isNotEmpty()) {
                        // 旧版备份里没有标签：建一个「未分类」兜住，别让事项变成没标签的孤儿
                        val fallbackId = tagDao.insert(
                            Tag(name = "未分类", colorArgb = AppDatabase.DEFAULT_TAG_COLOR)
                        )
                        tagDao.linkAll(newItems.map { ItemTag(it.id, fallbackId) })
                    }
                }

                // 导入的数据可能带着一条「进行中」的记录，按它重新挂服务与提醒
                if (recordDao.findRunning() == null) {
                    runCatching { TimerService.stop(getApplication()) }
                } else {
                    runCatching { TimerService.start(getApplication()) }
                }
                notify("导入完成：${newItems.size} 个事项 / ${newTags.size} 个标签 / ${newRecords.size} 条记录")
            } catch (e: Exception) {
                notify("导入失败：${e.message ?: "文件格式不正确"}")
            }
        }
    }

    companion object {
        const val DEFAULT_ITEM_COLOR = 0xFF2F6B4F.toInt()
    }
}
