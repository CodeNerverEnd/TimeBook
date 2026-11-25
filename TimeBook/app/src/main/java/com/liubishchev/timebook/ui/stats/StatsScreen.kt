package com.liubishchev.timebook.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liubishchev.timebook.data.ItemTag
import com.liubishchev.timebook.data.PlanTarget
import com.liubishchev.timebook.ui.AppViewModel
import com.liubishchev.timebook.ui.components.BarChart
import com.liubishchev.timebook.ui.components.ColorDot
import com.liubishchev.timebook.ui.components.EmptyHint
import com.liubishchev.timebook.ui.components.RatioBar
import com.liubishchev.timebook.ui.components.SectionHeader
import com.liubishchev.timebook.util.ItemTotal
import com.liubishchev.timebook.util.PlanPeriod
import com.liubishchev.timebook.util.StatMode
import com.liubishchev.timebook.util.TagTotal
import com.liubishchev.timebook.util.TimeFormat
import com.liubishchev.timebook.util.computeStats
import com.liubishchev.timebook.util.planPeriodKey
import java.time.LocalDate

@Composable
fun StatsScreen(vm: AppViewModel) {
    val items by vm.items.collectAsState()
    val records by vm.records.collectAsState()
    val now by vm.now.collectAsState()
    val tags by vm.tags.collectAsState()
    val tagIdsByItem by vm.tagIdsByItem.collectAsState()
    val planTargets by vm.planTargets.collectAsState()

    var modeIndex by rememberSaveable { mutableIntStateOf(0) }
    var epochDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    val mode = StatMode.entries[modeIndex]
    val anchor = LocalDate.ofEpochDay(epochDay)

    val links = remember(tagIdsByItem) {
        tagIdsByItem.flatMap { (itemId, tagIds) -> tagIds.map { ItemTag(itemId, it) } }
    }

    val stats = remember(mode, epochDay, items, records, tags, links, now / 60_000L) {
        computeStats(items, records, tags, links, mode, anchor, now)
    }

    // 日视图没有「计划周期」，不显示目标；月/年视图对上同期的计划目标
    val planPeriod: PlanPeriod? = when (mode) {
        StatMode.DAY -> null
        StatMode.MONTH -> PlanPeriod.MONTH
        StatMode.YEAR -> PlanPeriod.YEAR
    }
    val planKey = planPeriod?.let { planPeriodKey(it, anchor) }
    val tagTargets = remember(planTargets, planPeriod, planKey) {
        if (planPeriod == null || planKey == null) emptyMap()
        else planTargets
            .filter { it.periodType == planPeriod.type && it.periodKey == planKey && it.targetType == PlanTarget.TYPE_TAG }
            .associate { it.refId to it.targetMinutes }
    }
    val itemTargets = remember(planTargets, planPeriod, planKey) {
        if (planPeriod == null || planKey == null) emptyMap()
        else planTargets
            .filter { it.periodType == planPeriod.type && it.periodKey == planKey && it.targetType == PlanTarget.TYPE_ITEM }
            .associate { it.refId to it.targetMinutes }
    }

    val expanded = remember { mutableStateMapOf<Long, Boolean>() }
    val allExpanded = stats.tagTotals.isNotEmpty() && stats.tagTotals.all { expanded[it.tag.id] == true }
    val taggedItemIds = remember(links) { links.map { it.itemId }.toHashSet() }
    val untagged = stats.itemTotals.filterNot { taggedItemIds.contains(it.item.id) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        TabRow(selectedTabIndex = modeIndex) {
            StatMode.entries.forEachIndexed { index, m ->
                Tab(
                    selected = modeIndex == index,
                    onClick = { modeIndex = index },
                    text = { Text(m.label) }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { epochDay = shift(anchor, mode, -1).toEpochDay() }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "上一段")
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stats.rangeLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${stats.recordCount} 条记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { epochDay = shift(anchor, mode, 1).toEpochDay() }) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "下一段")
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                TotalCard(
                    totalLabel = TimeFormat.human(stats.totalMillis),
                    subLabel = totalSubLabel(stats.totalMillis, mode)
                )
            }

            if (stats.bucketValues.isNotEmpty() && stats.totalMillis > 0L) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            SectionHeader(
                                title = if (mode == StatMode.MONTH) "每日用时" else "每月用时",
                                trailing = "峰值 " + TimeFormat.human(stats.bucketValues.maxOrNull() ?: 0L)
                            )
                            Spacer(Modifier.height(12.dp))
                            BarChart(
                                values = stats.bucketValues,
                                labels = stats.bucketLabels
                            )
                        }
                    }
                }
            }

            if (stats.tagTotals.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "按标签",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (allExpanded) "收起全部" else "展开全部",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    stats.tagTotals.forEach { expanded[it.tag.id] = !allExpanded }
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }

                items(stats.tagTotals, key = { "tag${it.tag.id}" }) { row ->
                    TagStatRow(
                        row = row,
                        totalMillis = stats.totalMillis,
                        tagTargetMinutes = tagTargets[row.tag.id] ?: 0,
                        itemTargets = itemTargets,
                        expanded = expanded[row.tag.id] == true,
                        onToggle = { expanded[row.tag.id] = !(expanded[row.tag.id] ?: false) }
                    )
                }

                if (stats.tagTotals.size > 1 &&
                    stats.tagTotals.sumOf { it.millis } > stats.totalMillis
                ) {
                    item {
                        Text(
                            text = "提示：一个事项挂了多个标签时，同一段时间会在每个标签下各算一次，所以标签之和可能大于总用时。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 没打标签的事项单独列，避免它们在任何标签下面都看不到
            if (untagged.isNotEmpty()) {
                item {
                    SectionHeader("未打标签的事项", "${untagged.size} 项")
                }
                items(untagged, key = { "u${it.item.id}" }) { row ->
                    ItemStatRow(row = row, totalMillis = stats.totalMillis, targetMinutes = itemTargets[row.item.id] ?: 0)
                }
            }

            if (stats.itemTotals.isEmpty()) {
                item { EmptyHint("这段时间还没有记录。\n先去计时或补录一条吧。") }
            }
        }
    }
}

private fun shift(anchor: LocalDate, mode: StatMode, delta: Long): LocalDate = when (mode) {
    StatMode.DAY -> anchor.plusDays(delta)
    StatMode.MONTH -> anchor.plusMonths(delta)
    StatMode.YEAR -> anchor.plusYears(delta)
}

private fun totalSubLabel(totalMillis: Long, mode: StatMode): String = when (mode) {
    StatMode.DAY -> "占全天 ${String.format("%.1f", totalMillis / 86_400_000.0 * 100)}%"
    StatMode.MONTH -> "日均 ${TimeFormat.human(totalMillis / 30L)}"
    StatMode.YEAR -> "月均 ${TimeFormat.human(totalMillis / 12L)}"
}

@Composable
private fun TotalCard(totalLabel: String, subLabel: String) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = "总用时",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = totalLabel,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
            )
        }
    }
}

@Composable
private fun ItemStatRow(row: ItemTotal, totalMillis: Long, targetMinutes: Int = 0) {
    val fraction = if (totalMillis > 0L) row.millis.toFloat() / totalMillis.toFloat() else 0f
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorDot(row.item.colorArgb, 10)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = row.item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${row.count} 次 · " + String.format("%.0f%%", fraction * 100),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = TimeFormat.human(row.millis),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(8.dp))
            RatioBar(fraction = fraction, color = Color(row.item.colorArgb))
        }
    }
}

@Composable
private fun TagStatRow(
    row: TagTotal,
    totalMillis: Long,
    tagTargetMinutes: Int,
    itemTargets: Map<Long, Int>,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val fraction = if (totalMillis > 0L) row.millis.toFloat() / totalMillis.toFloat() else 0f
    val color = Color(row.tag.colorArgb)
    val targetMillis = tagTargetMinutes * 60_000L
    val targetPercent = if (targetMillis > 0L) (row.millis * 100 / targetMillis).toInt() else 0

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggle() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                ColorDot(row.tag.colorArgb, 10)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = row.tag.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${row.count} 次 · ${row.itemCount} 项",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = TimeFormat.human(row.millis),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp)
                )
            }

            Spacer(Modifier.height(8.dp))
            if (targetMillis > 0L) {
                // 有计划就显示「占计划多少」，而不只是占总量多少
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "计划 ${TimeFormat.human(targetMillis)} · 完成 $targetPercent%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (row.millis > targetMillis) {
                        Text(
                            text = "已超 ${TimeFormat.human(row.millis - targetMillis)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFA3601A)
                        )
                    } else {
                        Text(
                            text = "还剩 ${TimeFormat.human(targetMillis - row.millis)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                RatioBar(
                    fraction = (row.millis.toFloat() / targetMillis).coerceIn(0f, 1f),
                    color = color
                )
            } else {
                RatioBar(fraction = fraction, color = color)
            }

            if (expanded) {
                if (row.items.isEmpty()) {
                    Text(
                        text = "这个标签下这段时间没有记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else {
                    row.items.forEach { item ->
                        StatItemLine(
                            item = item,
                            baseMillis = row.millis,
                            targetMinutes = itemTargets[item.item.id] ?: 0
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItemLine(item: ItemTotal, baseMillis: Long, targetMinutes: Int) {
    val pctOfTag = if (baseMillis > 0L) (item.millis * 100 / baseMillis).toInt() else 0
    val targetMillis = targetMinutes * 60_000L
    Column(Modifier.padding(top = 9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ColorDot(item.item.colorArgb, 8)
            Spacer(Modifier.width(8.dp))
            Text(
                text = item.item.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${item.count} 次",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = TimeFormat.human(item.millis),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$pctOfTag%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(34.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        }
        if (targetMillis > 0L) {
            val done = (item.millis * 100 / targetMillis).toInt()
            Spacer(Modifier.height(3.dp))
            Text(
                text = "目标 ${TimeFormat.human(targetMillis)} · $done%" +
                    if (item.millis > targetMillis) " · 已超 ${TimeFormat.human(item.millis - targetMillis)}"
                    else " · 还差 ${TimeFormat.human(targetMillis - item.millis)}",
                style = MaterialTheme.typography.bodySmall,
                color = if (item.millis > targetMillis) Color(0xFFA3601A) else Color(0xFF2F6B4F)
            )
            Spacer(Modifier.height(4.dp))
            RatioBar(
                fraction = (item.millis.toFloat() / targetMillis).coerceIn(0f, 1f),
                color = Color(item.item.colorArgb)
            )
        }
    }
}
