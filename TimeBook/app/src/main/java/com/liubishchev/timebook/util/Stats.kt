package com.liubishchev.timebook.util

import com.liubishchev.timebook.data.ItemTag
import com.liubishchev.timebook.data.Tag
import com.liubishchev.timebook.data.TaskItem
import com.liubishchev.timebook.data.TimeRecord
import java.time.LocalDate
import java.time.YearMonth

enum class StatMode(val label: String) {
    DAY("日"), MONTH("月"), YEAR("年")
}

data class ItemTotal(val item: TaskItem, val millis: Long, val count: Int)

/** 标签汇总：该标签下所有事项的用时之和。 */
data class TagTotal(
    val tag: Tag,
    val millis: Long,
    /** 该标签下的记录条数（一个事项挂多个标签时会被各标签各算一次）。 */
    val count: Int,
    val itemCount: Int,
    val items: List<ItemTotal>
)

data class StatsResult(
    val rangeLabel: String,
    val totalMillis: Long,
    val recordCount: Int,
    val itemTotals: List<ItemTotal>,
    val tagTotals: List<TagTotal>,
    val bucketLabels: List<String>,
    val bucketValues: List<Long>
)

/** 两个区间重叠部分的毫秒数，负值归零。 */
fun overlapMillis(start: Long, end: Long, rangeStart: Long, rangeEnd: Long): Long =
    (minOf(end, rangeEnd) - maxOf(start, rangeStart)).coerceAtLeast(0L)

/**
 * 统计核心。
 *
 * 口径：一条跨零点的记录会按「实际重叠时长」拆到它跨越的每一天，
 * 所以「日」加起来正好等于「月」，「月」加起来正好等于「年」，不会重复也不会漏。
 */
fun computeStats(
    items: List<TaskItem>,
    records: List<TimeRecord>,
    tags: List<Tag>,
    links: List<ItemTag>,
    mode: StatMode,
    anchor: LocalDate,
    now: Long
): StatsResult {

    val range: Triple<Long, Long, String> = when (mode) {
        StatMode.DAY -> Triple(
            TimeFormat.atStartOfDay(anchor),
            TimeFormat.atStartOfDay(anchor.plusDays(1)),
            TimeFormat.dateLabel(anchor)
        )

        StatMode.MONTH -> {
            val ym = YearMonth.from(anchor)
            Triple(
                TimeFormat.atStartOfDay(ym.atDay(1)),
                TimeFormat.atStartOfDay(ym.plusMonths(1).atDay(1)),
                "${ym.year}年${ym.monthValue}月"
            )
        }

        StatMode.YEAR -> Triple(
            TimeFormat.atStartOfDay(LocalDate.of(anchor.year, 1, 1)),
            TimeFormat.atStartOfDay(LocalDate.of(anchor.year + 1, 1, 1)),
            "${anchor.year}年"
        )
    }
    val rangeStart = range.first
    val rangeEnd = range.second
    val rangeLabel = range.third

    val itemMap = items.associateBy { it.id }
    val totals = LinkedHashMap<Long, Long>()
    val counts = LinkedHashMap<Long, Int>()
    var count = 0
    var sum = 0L

    for (r in records) {
        val s = maxOf(r.startAt, rangeStart)
        val e = minOf(r.endAt ?: now, rangeEnd)
        if (e <= s) continue
        val delta = e - s
        totals[r.itemId] = (totals[r.itemId] ?: 0L) + delta
        counts[r.itemId] = (counts[r.itemId] ?: 0) + 1
        sum += delta
        count++
    }

    val itemTotals = totals.entries
        .mapNotNull { entry ->
            itemMap[entry.key]?.let {
                ItemTotal(it, entry.value, counts[entry.key] ?: 0)
            }
        }
        .sortedWith(compareByDescending<ItemTotal> { it.millis }.thenBy { it.item.name })

    // 标签汇总：把事项的用时累加到它挂的每个标签上。
    // 注意：一个事项挂多个标签时，各标签之和会大于总用时（同一段时间被两个标签都算了一次），
    // 这是「多标签」的固有口径，界面里会明确提示。
    val tagMap = tags.associateBy { it.id }
    val itemTotalIndex = itemTotals.associateBy { it.item.id }
    val itemsByTag = LinkedHashMap<Long, MutableList<ItemTotal>>()
    for (link in links) {
        val itemTotal = itemTotalIndex[link.itemId] ?: continue
        if (tagMap[link.tagId] == null) continue
        itemsByTag.getOrPut(link.tagId) { ArrayList() }.add(itemTotal)
    }
    val tagTotals = itemsByTag.entries
        .map { entry ->
            val tagItems = entry.value.sortedByDescending { it.millis }
            TagTotal(
                tag = tagMap.getValue(entry.key),
                millis = tagItems.sumOf { it.millis },
                count = tagItems.sumOf { it.count },
                itemCount = tagItems.size,
                items = tagItems
            )
        }
        .sortedWith(compareByDescending<TagTotal> { it.millis }.thenBy { it.tag.name })

    // 趋势桶：按月 = 每一天一根柱；按年 = 每个月一根柱；按日不画趋势图
    val bucketLabels = ArrayList<String>()
    val bucketValues = ArrayList<Long>()
    when (mode) {
        StatMode.MONTH -> {
            val len = YearMonth.from(anchor).lengthOfMonth()
            for (d in 1..len) {
                bucketLabels.add(d.toString())
                bucketValues.add(0L)
            }
        }

        StatMode.YEAR -> {
            for (m in 1..12) {
                bucketLabels.add("${m}月")
                bucketValues.add(0L)
            }
        }

        StatMode.DAY -> Unit
    }

    if (bucketValues.isNotEmpty()) {
        for (r in records) {
            val s = maxOf(r.startAt, rangeStart)
            val e = minOf(r.endAt ?: now, rangeEnd)
            if (e <= s) continue
            var cursor = s
            while (cursor < e) {
                val day = TimeFormat.toLocalDate(cursor)
                val dayEnd = TimeFormat.atStartOfDay(day.plusDays(1))
                val segEnd = minOf(e, dayEnd)
                if (segEnd <= cursor) break
                val delta = segEnd - cursor
                val index = if (mode == StatMode.MONTH) day.dayOfMonth - 1 else day.monthValue - 1
                if (index in bucketValues.indices) {
                    bucketValues[index] = bucketValues[index] + delta
                }
                cursor = segEnd
            }
        }
    }

    return StatsResult(
        rangeLabel = rangeLabel,
        totalMillis = sum,
        recordCount = count,
        itemTotals = itemTotals,
        tagTotals = tagTotals,
        bucketLabels = bucketLabels,
        bucketValues = bucketValues
    )
}
