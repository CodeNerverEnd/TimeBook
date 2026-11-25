package com.liubishchev.timebook.util

import com.liubishchev.timebook.data.ItemTag
import com.liubishchev.timebook.data.PlanDoc
import com.liubishchev.timebook.data.PlanTarget
import com.liubishchev.timebook.data.Tag
import com.liubishchev.timebook.data.TaskItem
import com.liubishchev.timebook.data.TimeRecord
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

/**
 * 计划周期：月度 / 年度 / 五年。
 *
 * 五年按「五年规划」口径对齐，固定从 2021 起算：2021-2025、2026-2030、2031-2035……
 * 所以 2026 年的任意一天都落在「2026-2030」，不会出现 2026-2030 之外的怪区间。
 */
enum class PlanPeriod(val label: String, val type: String) {
    MONTH("月度", "MONTH"),
    YEAR("年度", "YEAR"),
    FIVE("五年", "FIVE")
}

/** 五年周期的起算年，改这里会改变所有五年计划的边界。 */
const val FIVE_YEAR_BASE = 2021

/** 2026 -> 2026；2029 -> 2026；2021 -> 2021。 */
fun fiveStartYear(year: Int): Int {
    val diff = year - FIVE_YEAR_BASE
    val offset = if (diff >= 0) (diff / 5) * 5 else -(((-diff) + 4) / 5) * 5
    return FIVE_YEAR_BASE + offset
}

fun planPeriodKey(period: PlanPeriod, date: LocalDate): String = when (period) {
    PlanPeriod.MONTH -> "%04d-%02d".format(date.year, date.monthValue)
    PlanPeriod.YEAR -> date.year.toString()
    PlanPeriod.FIVE -> {
        val start = fiveStartYear(date.year)
        "$start-${start + 4}"
    }
}

fun planPeriodLabel(period: PlanPeriod, date: LocalDate): String = when (period) {
    PlanPeriod.MONTH -> "${date.year}年${date.monthValue}月"
    PlanPeriod.YEAR -> "${date.year}年"
    PlanPeriod.FIVE -> {
        val start = fiveStartYear(date.year)
        "$start–${start + 4}年"
    }
}

fun planPeriodRange(period: PlanPeriod, date: LocalDate): Pair<Long, Long> = when (period) {
    PlanPeriod.MONTH -> {
        val ym = YearMonth.from(date)
        TimeFormat.atStartOfDay(ym.atDay(1)) to TimeFormat.atStartOfDay(ym.plusMonths(1).atDay(1))
    }

    PlanPeriod.YEAR -> {
        TimeFormat.atStartOfDay(LocalDate.of(date.year, 1, 1)) to
            TimeFormat.atStartOfDay(LocalDate.of(date.year + 1, 1, 1))
    }

    PlanPeriod.FIVE -> {
        val start = fiveStartYear(date.year)
        TimeFormat.atStartOfDay(LocalDate.of(start, 1, 1)) to
            TimeFormat.atStartOfDay(LocalDate.of(start + 5, 1, 1))
    }
}

/** 前后翻一期（五年一次翻 5 年）。 */
fun shiftPeriod(period: PlanPeriod, date: LocalDate, delta: Long): LocalDate = when (period) {
    PlanPeriod.MONTH -> date.plusMonths(delta)
    PlanPeriod.YEAR -> date.plusYears(delta)
    PlanPeriod.FIVE -> date.plusYears(delta * 5)
}

/** 一个周期的完整描述，多数界面只需要这一份。 */
data class PlanPeriodSpec(
    val period: PlanPeriod,
    val key: String,
    val label: String,
    val startAt: Long,
    val endAt: Long
)

fun planPeriodSpec(period: PlanPeriod, date: LocalDate): PlanPeriodSpec {
    val (start, end) = planPeriodRange(period, date)
    return PlanPeriodSpec(period, planPeriodKey(period, date), planPeriodLabel(period, date), start, end)
}

/** 从存档里的 periodType / periodKey 还原周期（历史计划列表、待总结识别要用）。 */
fun planPeriodSpecFromKey(type: String, key: String): PlanPeriodSpec? {
    val period = PlanPeriod.entries.firstOrNull { it.type == type } ?: return null
    return when (period) {
        PlanPeriod.MONTH -> {
            val ym = runCatching { YearMonth.parse(key) }.getOrNull() ?: return null
            planPeriodSpec(period, ym.atDay(1))
        }

        PlanPeriod.YEAR -> {
            val year = key.toIntOrNull() ?: return null
            planPeriodSpec(period, LocalDate.of(year, 1, 1))
        }

        PlanPeriod.FIVE -> {
            val startYear = key.substringBefore("-").toIntOrNull() ?: return null
            planPeriodSpec(period, LocalDate.of(startYear, 1, 1))
        }
    }
}

/** 单个事项在某一期内的计划与实际。 */
data class ItemPlanProgress(
    val item: TaskItem,
    val usedMillis: Long,
    val usedCount: Int,
    val targetMinutes: Int
) {
    val hasTarget: Boolean get() = targetMinutes > 0
    val targetMillis: Long get() = targetMinutes * 60_000L
    val fraction: Float
        get() = if (targetMillis <= 0L) 0f else (usedMillis.toFloat() / targetMillis).coerceIn(0f, 1f)
    val percent: Int
        get() = if (targetMillis <= 0L) 0 else (usedMillis * 100 / targetMillis).toInt()
    val remainMillis: Long get() = (targetMillis - usedMillis).coerceAtLeast(0L)
    val overMillis: Long get() = (usedMillis - targetMillis).coerceAtLeast(0L)
}

/** 单个标签在某一期内的计划与实际，含它下面的事项。 */
data class PlanProgress(
    val tag: Tag,
    val targetMinutes: Int,
    val usedMillis: Long,
    val usedCount: Int,
    val itemCount: Int,
    val items: List<ItemPlanProgress> = emptyList()
) {
    val hasTarget: Boolean get() = targetMinutes > 0
    val targetMillis: Long get() = targetMinutes * 60_000L
    val fraction: Float
        get() = if (targetMillis <= 0L) 0f else (usedMillis.toFloat() / targetMillis).coerceIn(0f, 1f)
    val percent: Int
        get() = if (targetMillis <= 0L) 0 else (usedMillis * 100 / targetMillis).toInt()
    val remainMillis: Long get() = (targetMillis - usedMillis).coerceAtLeast(0L)
    val overMillis: Long get() = (usedMillis - targetMillis).coerceAtLeast(0L)

    /** 该标签下各事项目标之和（用于显示「拆解合计」，不必等于标签目标）。 */
    val itemTargetMinutes: Int get() = items.sumOf { it.targetMinutes }
}

data class PlanOverview(
    val period: PlanPeriod,
    val periodKey: String,
    val periodLabel: String,
    val startAt: Long,
    val endAt: Long,
    /** 标签级目标之和 */
    val tagTargetMinutes: Int,
    /** 所属标签没设目标、因而直接计入总目标的事项目标之和 */
    val orphanItemTargetMinutes: Int,
    val totalUsedMillis: Long,
    val rows: List<PlanProgress>
) {
    /** 本期总目标 = 标签目标 + 无父标签目标的事项目标（同时存在时按拆解，不重复计）。 */
    val totalTargetMinutes: Int get() = tagTargetMinutes + orphanItemTargetMinutes
    val totalTargetMillis: Long get() = totalTargetMinutes * 60_000L
    val percent: Int
        get() = if (totalTargetMillis <= 0L) 0 else (totalUsedMillis * 100 / totalTargetMillis).toInt()
    val hasPlan: Boolean get() = totalTargetMinutes > 0
    fun endedAt(now: Long): Boolean = endAt <= now
}

/**
 * 把「计划」和「实际」对上。
 *
 * 计划两级：
 *  - 标签目标（targetType = TAG）算进本期总目标；
 *  - 事项目标（targetType = ITEM）在所属标签也有目标时按「拆解」处理，不重复计入总目标；
 *    所属标签没有目标时，这些事项目标计入总目标，不会被吞掉。
 *
 * 实际：跨周期的记录按实际重叠部分算；一个事项挂多个标签时，每个标签都会分到这段时间。
 */
fun computePlan(
    period: PlanPeriod,
    date: LocalDate,
    tags: List<Tag>,
    links: List<ItemTag>,
    items: List<TaskItem>,
    records: List<TimeRecord>,
    planTargets: List<PlanTarget>,
    now: Long
): PlanOverview {
    val spec = planPeriodSpec(period, date)

    val itemMillis = HashMap<Long, Long>()
    val itemCounts = HashMap<Long, Int>()
    for (r in records) {
        val s = maxOf(r.startAt, spec.startAt)
        val e = minOf(r.endAt ?: now, spec.endAt)
        if (e <= s) continue
        itemMillis[r.itemId] = (itemMillis[r.itemId] ?: 0L) + (e - s)
        itemCounts[r.itemId] = (itemCounts[r.itemId] ?: 0) + 1
    }

    val periodTargets = planTargets.filter { it.periodType == period.type && it.periodKey == spec.key }
    val tagTargets = periodTargets
        .filter { it.targetType == PlanTarget.TYPE_TAG }
        .associate { it.refId to it.targetMinutes }
    val itemTargets = periodTargets
        .filter { it.targetType == PlanTarget.TYPE_ITEM }
        .associate { it.refId to it.targetMinutes }

    val tagIdsOfItem = links.groupBy({ it.itemId }, { it.tagId })
    val itemsOfTag = links.groupBy({ it.tagId }, { it.itemId })
    val itemIndex = items.associateBy { it.id }

    val rows = tags.map { tag ->
        val ids = itemsOfTag[tag.id].orEmpty().filter { itemIndex.containsKey(it) }
        val itemRows = ids.mapNotNull { itemId ->
            val item = itemIndex[itemId] ?: return@mapNotNull null
            val used = itemMillis[itemId] ?: 0L
            // 归档的且本期没记录的隐藏，避免列表里挂一堆不再使用的旧事项
            if (item.archived && used <= 0L) return@mapNotNull null
            ItemPlanProgress(
                item = item,
                usedMillis = used,
                usedCount = itemCounts[itemId] ?: 0,
                targetMinutes = itemTargets[itemId] ?: 0
            )
        }.sortedWith(
            compareByDescending<ItemPlanProgress> { it.usedMillis }
                .thenByDescending { it.targetMinutes }
                .thenBy { it.item.name }
        )

        PlanProgress(
            tag = tag,
            targetMinutes = tagTargets[tag.id] ?: 0,
            usedMillis = ids.sumOf { itemMillis[it] ?: 0L },
            usedCount = ids.sumOf { itemCounts[it] ?: 0 },
            itemCount = ids.size,
            items = itemRows
        )
    }
        .filter { it.targetMinutes > 0 || it.usedMillis > 0L || it.itemTargetMinutes > 0 }
        .sortedWith(
            compareByDescending<PlanProgress> { it.targetMinutes }
                .thenByDescending { it.usedMillis }
                .thenBy { it.tag.name }
        )

    // 事项目标里，所属标签一个都没设目标的部分，才计入总目标
    val orphanItemTarget = itemTargets.entries.sumOf { (itemId, minutes) ->
        val covered = tagIdsOfItem[itemId].orEmpty().any { (tagTargets[it] ?: 0) > 0 }
        if (covered) 0 else minutes
    }

    return PlanOverview(
        period = period,
        periodKey = spec.key,
        periodLabel = spec.label,
        startAt = spec.startAt,
        endAt = spec.endAt,
        tagTargetMinutes = rows.sumOf { it.targetMinutes },
        orphanItemTargetMinutes = orphanItemTarget,
        totalUsedMillis = itemMillis.values.sum(),
        rows = rows
    )
}

/** 某期的归档档案（没归档就是 null）。 */
fun findPlanDoc(docs: List<PlanDoc>, period: PlanPeriod, date: LocalDate): PlanDoc? {
    val key = planPeriodKey(period, date)
    return docs.firstOrNull { it.periodType == period.type && it.periodKey == key }
}

fun isPlanClosed(docs: List<PlanDoc>, period: PlanPeriod, date: LocalDate): Boolean =
    findPlanDoc(docs, period, date)?.isClosed == true

// ------------------------------------------------------------------ 归档快照

data class SnapshotItem(
    val name: String,
    val colorArgb: Int,
    val targetMinutes: Int,
    val usedMillis: Long,
    val count: Int
)

data class SnapshotRow(
    val name: String,
    val colorArgb: Int,
    val targetMinutes: Int,
    val usedMillis: Long,
    val count: Int,
    val items: List<SnapshotItem>
)

data class PlanSnapshot(
    val label: String,
    val totalTargetMinutes: Int,
    val totalUsedMillis: Long,
    val recordCount: Int,
    val rows: List<SnapshotRow>
) {
    val percent: Int
        get() {
            val target = totalTargetMinutes * 60_000L
            return if (target <= 0L) 0 else (totalUsedMillis * 100 / target).toInt()
        }
}

/**
 * 归档时把当前统计定格下来。
 * 只存「有目标或有记录」的行，空行不占地方。
 */
fun snapshotOf(overview: PlanOverview): PlanSnapshot = PlanSnapshot(
    label = overview.periodLabel,
    totalTargetMinutes = overview.totalTargetMinutes,
    totalUsedMillis = overview.totalUsedMillis,
    recordCount = overview.rows.sumOf { it.usedCount },
    rows = overview.rows
        .filter { it.targetMinutes > 0 || it.usedMillis > 0L || it.itemTargetMinutes > 0 }
        .map { row ->
            SnapshotRow(
                name = row.tag.name,
                colorArgb = row.tag.colorArgb,
                targetMinutes = row.targetMinutes,
                usedMillis = row.usedMillis,
                count = row.usedCount,
                items = row.items
                    .filter { it.targetMinutes > 0 || it.usedMillis > 0L }
                    .map { it2 ->
                        SnapshotItem(
                            name = it2.item.name,
                            colorArgb = it2.item.colorArgb,
                            targetMinutes = it2.targetMinutes,
                            usedMillis = it2.usedMillis,
                            count = it2.usedCount
                        )
                    }
            )
        }
)

fun PlanSnapshot.toJson(): String {
    val root = JSONObject()
    root.put("label", label)
    root.put("totalTargetMinutes", totalTargetMinutes)
    root.put("totalUsedMillis", totalUsedMillis)
    root.put("recordCount", recordCount)
    val rowsArray = JSONArray()
    rows.forEach { row ->
        val o = JSONObject()
        o.put("name", row.name)
        o.put("colorArgb", row.colorArgb)
        o.put("targetMinutes", row.targetMinutes)
        o.put("usedMillis", row.usedMillis)
        o.put("count", row.count)
        val itemsArray = JSONArray()
        row.items.forEach { item ->
            itemsArray.put(JSONObject().apply {
                put("name", item.name)
                put("colorArgb", item.colorArgb)
                put("targetMinutes", item.targetMinutes)
                put("usedMillis", item.usedMillis)
                put("count", item.count)
            })
        }
        o.put("items", itemsArray)
        rowsArray.put(o)
    }
    root.put("rows", rowsArray)
    return root.toString()
}

fun parseSnapshot(json: String?): PlanSnapshot? {
    if (json.isNullOrBlank()) return null
    return runCatching {
        val root = JSONObject(json)
        val rowsArray = root.optJSONArray("rows") ?: JSONArray()
        val rows = ArrayList<SnapshotRow>(rowsArray.length())
        for (i in 0 until rowsArray.length()) {
            val o = rowsArray.getJSONObject(i)
            val itemsArray = o.optJSONArray("items") ?: JSONArray()
            val items = ArrayList<SnapshotItem>(itemsArray.length())
            for (j in 0 until itemsArray.length()) {
                val io = itemsArray.getJSONObject(j)
                items.add(
                    SnapshotItem(
                        name = io.optString("name"),
                        colorArgb = io.optInt("colorArgb", 0),
                        targetMinutes = io.optInt("targetMinutes", 0),
                        usedMillis = io.optLong("usedMillis", 0L),
                        count = io.optInt("count", 0)
                    )
                )
            }
            rows.add(
                SnapshotRow(
                    name = o.optString("name"),
                    colorArgb = o.optInt("colorArgb", 0),
                    targetMinutes = o.optInt("targetMinutes", 0),
                    usedMillis = o.optLong("usedMillis", 0L),
                    count = o.optInt("count", 0),
                    items = items
                )
            )
        }
        PlanSnapshot(
            label = root.optString("label"),
            totalTargetMinutes = root.optInt("totalTargetMinutes", 0),
            totalUsedMillis = root.optLong("totalUsedMillis", 0L),
            recordCount = root.optInt("recordCount", 0),
            rows = rows
        )
    }.getOrNull()
}

/**
 * 待总结的期：已经过了结束日、但还没写总结归档，且确实有计划或记录。
 * 数据来源是计划目标和计划档案，不遍历无穷多空周期。
 */
data class DuePeriod(val period: PlanPeriod, val key: String, val label: String, val endAt: Long)

fun duePeriods(
    planTargets: List<PlanTarget>,
    planDocs: List<PlanDoc>,
    now: Long
): List<DuePeriod> {
    val closed = planDocs.filter { it.isClosed }.map { it.periodType to it.periodKey }.toHashSet()
    val out = LinkedHashMap<String, DuePeriod>()
    planTargets
        .filter { it.targetMinutes > 0 }
        .forEach { target ->
            val id = target.periodType + "|" + target.periodKey
            if (closed.contains(target.periodType to target.periodKey)) return@forEach
            if (out.containsKey(id)) return@forEach
            val spec = planPeriodSpecFromKey(target.periodType, target.periodKey) ?: return@forEach
            if (spec.endAt <= now) {
                out[id] = DuePeriod(spec.period, spec.key, spec.label, spec.endAt)
            }
        }
    return out.values.sortedByDescending { it.endAt }
}
