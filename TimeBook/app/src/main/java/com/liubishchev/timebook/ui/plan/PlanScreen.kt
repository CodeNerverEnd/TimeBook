package com.liubishchev.timebook.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.liubishchev.timebook.data.ItemTag
import com.liubishchev.timebook.data.PlanDoc
import com.liubishchev.timebook.data.PlanTarget
import com.liubishchev.timebook.data.Tag
import com.liubishchev.timebook.data.TaskItem
import com.liubishchev.timebook.ui.AppViewModel
import com.liubishchev.timebook.ui.components.ColorDot
import com.liubishchev.timebook.ui.components.EmptyHint
import com.liubishchev.timebook.ui.components.RatioBar
import com.liubishchev.timebook.ui.components.TagChip
import com.liubishchev.timebook.util.ItemPlanProgress
import com.liubishchev.timebook.util.PlanOverview
import com.liubishchev.timebook.util.PlanPeriod
import com.liubishchev.timebook.util.PlanProgress
import com.liubishchev.timebook.util.PlanSnapshot
import com.liubishchev.timebook.util.SnapshotRow
import com.liubishchev.timebook.util.TimeFormat
import com.liubishchev.timebook.util.computePlan
import com.liubishchev.timebook.util.duePeriods
import com.liubishchev.timebook.util.parseSnapshot
import com.liubishchev.timebook.util.planPeriodSpec
import com.liubishchev.timebook.util.planPeriodSpecFromKey
import com.liubishchev.timebook.util.shiftPeriod
import java.time.LocalDate

/** 要改目标的那个对象：标签或具体事项。 */
private data class TargetRef(val type: String, val id: Long, val name: String, val colorArgb: Int)

@Composable
fun PlanScreen(vm: AppViewModel) {
    val tags by vm.tags.collectAsState()
    val items by vm.items.collectAsState()
    val records by vm.records.collectAsState()
    val planTargets by vm.planTargets.collectAsState()
    val planDocs by vm.planDocs.collectAsState()
    val tagIdsByItem by vm.tagIdsByItem.collectAsState()
    val now by vm.now.collectAsState()

    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var periodIndex by rememberSaveable { mutableIntStateOf(0) }
    var epochDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    val openTags = remember { mutableStateMapOf<Long, Boolean>() }

    var editingTarget by remember { mutableStateOf<TargetRef?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var pickerItemTab by remember { mutableIntStateOf(0) }
    var closing by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<PlanDoc?>(null) }

    val period = PlanPeriod.entries[periodIndex]
    val date = LocalDate.ofEpochDay(epochDay)
    val spec = remember(period, epochDay) { planPeriodSpec(period, date) }

    val links = remember(tagIdsByItem) {
        tagIdsByItem.flatMap { (itemId, tagIds) -> tagIds.map { ItemTag(itemId, it) } }
    }
    val overview = remember(period, epochDay, tags, links, items, records, planTargets, now / 60_000L) {
        computePlan(period, date, tags, links, items, records, planTargets, now)
    }

    val doc = planDocs.firstOrNull { it.periodType == period.type && it.periodKey == spec.key }
    val closed = doc?.isClosed == true
    val ended = spec.endAt <= now
    val due = remember(planTargets, planDocs, now / 60_000L) {
        duePeriods(planTargets, planDocs, now)
    }
    val archived = remember(planDocs) {
        planDocs.filter { it.isClosed }.sortedByDescending { it.closedAt ?: 0L }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "计划",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "月度 / 年度 / 五年，给标签或具体事项定时长目标；到期写总结后归档",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (tabIndex == 0 && !closed) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { showPicker = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = "新增计划",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 本期 / 历史 分段
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(3.dp)
        ) {
            listOf("本期计划", if (due.isEmpty()) "历史计划" else "历史计划 ●").forEachIndexed { index, label ->
                val on = tabIndex == index
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable { tabIndex = index }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (on) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (tabIndex == 0) {
            CurrentPlanTab(
                overview = overview,
                doc = doc,
                closed = closed,
                ended = ended,
                period = period,
                periodIndex = periodIndex,
                onSelectPeriod = { periodIndex = it },
                onShift = { delta -> epochDay = shiftPeriod(period, date, delta).toEpochDay() },
                onCopyPrevious = { vm.copyPreviousPlan(period, date) },
                openTags = openTags,
                onToggleTag = { id -> openTags[id] = !(openTags[id] ?: false) },
                onEditTag = { row ->
                    editingTarget = TargetRef(PlanTarget.TYPE_TAG, row.tag.id, row.tag.name, row.tag.colorArgb)
                },
                onEditItem = { item ->
                    val tag = rowTagOf(item.item.id, links, tags)
                    editingTarget = TargetRef(
                        PlanTarget.TYPE_ITEM,
                        item.item.id,
                        item.item.name,
                        tag?.colorArgb ?: item.item.colorArgb
                    )
                },
                onAdd = { showPicker = true },
                onClose = { closing = true },
                onOpenSummary = { doc?.let { detail = it } }
            )
        } else {
            HistoryTab(
                due = due,
                archived = archived,
                onWriteSummary = { duePeriod ->
                    planPeriodSpecFromKey(duePeriod.period.type, duePeriod.key)?.let { s ->
                        periodIndex = PlanPeriod.entries.indexOf(duePeriod.period)
                        epochDay = TimeFormat.toLocalDate(s.startAt).toEpochDay()
                        tabIndex = 0
                        closing = true
                    }
                },
                onOpenDetail = { detail = it }
            )
        }
    }

    // 新增计划：先选对象
    if (showPicker) {
        val tagRowById = overview.rows.associateBy { it.tag.id }
        val itemRowById = overview.rows.flatMap { it.items }.associateBy { it.item.id }
        PlanPickerDialog(
            periodLabel = overview.periodLabel,
            itemTab = pickerItemTab,
            onTabChange = { pickerItemTab = it },
            tags = tags.filter { !it.archived },
            items = items.filter { !it.archived },
            tagUsed = { id -> tagRowById[id] },
            itemUsed = { id -> itemRowById[id] },
            tagTargetOf = { id ->
                planTargets.firstOrNull {
                    it.refId == id && it.targetType == PlanTarget.TYPE_TAG &&
                        it.periodType == period.type && it.periodKey == spec.key
                }?.targetMinutes ?: 0
            },
            itemTargetOf = { id ->
                planTargets.firstOrNull {
                    it.refId == id && it.targetType == PlanTarget.TYPE_ITEM &&
                        it.periodType == period.type && it.periodKey == spec.key
                }?.targetMinutes ?: 0
            },
            onPickTag = { tag ->
                showPicker = false
                editingTarget = TargetRef(PlanTarget.TYPE_TAG, tag.id, tag.name, tag.colorArgb)
            },
            onPickItem = { item ->
                showPicker = false
                val tag = rowTagOf(item.id, links, tags)
                editingTarget = TargetRef(
                    PlanTarget.TYPE_ITEM,
                    item.id,
                    item.name,
                    tag?.colorArgb ?: item.colorArgb
                )
            },
            onDismiss = { showPicker = false }
        )
    }

    // 设定目标
    editingTarget?.let { target ->
        val isItem = target.type == PlanTarget.TYPE_ITEM
        val current = planTargets.firstOrNull {
            it.refId == target.id && it.targetType == target.type &&
                it.periodType == period.type && it.periodKey == spec.key
        }?.targetMinutes ?: 0
        val parentHint = if (isItem) {
            val parentTag = rowTagOf(target.id, links, tags)
            val parentTarget = parentTag?.let { pt ->
                planTargets.firstOrNull {
                    it.refId == pt.id && it.targetType == PlanTarget.TYPE_TAG &&
                        it.periodType == period.type && it.periodKey == spec.key
                }?.targetMinutes ?: 0
            } ?: 0
            if (parentTag != null) {
                if (parentTarget > 0) {
                    "属于标签「${parentTag.name}」，该标签本期目标 ${TimeFormat.human(parentTarget * 60_000L)}。\n" +
                        "事项目标只算它自己的进度，不会重复计入本期总目标。"
                } else {
                    "属于标签「${parentTag.name}」（该标签本期没设目标）。\n" +
                        "只给事项设目标时，合计会算进本期总目标。"
                }
            } else null
        } else null

        PlanTargetDialog(
            title = "「${target.name}」${overview.periodLabel}计划",
            kindLabel = if (isItem) "事项" else "标签",
            parentHint = parentHint,
            initialMinutes = current,
            presets = presetsFor(period),
            onDismiss = { editingTarget = null },
            onConfirm = { minutes ->
                vm.setPlanTarget(target.type, target.id, period, date, minutes)
                editingTarget = null
            }
        )
    }

    // 写总结并归档
    if (closing) {
        ClosePlanDialog(
            overview = overview,
            onDismiss = { closing = false },
            onConfirm = { summary ->
                vm.closePlanPeriod(period, date, summary)
                closing = false
            }
        )
    }

    // 归档详情 / 改总结 / 取消归档
    detail?.let { d ->
        ArchiveDetailDialog(
            doc = d,
            onDismiss = { detail = null },
            onSaveSummary = { text ->
                vm.updatePlanSummary(d.periodType, d.periodKey, text)
                detail = null
            },
            onReopen = {
                vm.reopenPlan(d.periodType, d.periodKey)
                detail = null
            }
        )
    }
}

@Composable
private fun ColumnScope.CurrentPlanTab(
    overview: PlanOverview,
    doc: PlanDoc?,
    closed: Boolean,
    ended: Boolean,
    period: PlanPeriod,
    periodIndex: Int,
    onSelectPeriod: (Int) -> Unit,
    onShift: (Long) -> Unit,
    onCopyPrevious: () -> Unit,
    openTags: MutableMap<Long, Boolean>,
    onToggleTag: (Long) -> Unit,
    onEditTag: (PlanProgress) -> Unit,
    onEditItem: (ItemPlanProgress) -> Unit,
    onAdd: () -> Unit,
    onClose: () -> Unit,
    onOpenSummary: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlanPeriod.entries.forEachIndexed { index, p ->
                    val selected = periodIndex == index
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                            )
                            .clickable { onSelectPeriod(index) }
                            .padding(horizontal = 15.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "${p.label}计划",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onShift(-1) }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "上一期")
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = overview.periodLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = when {
                            closed -> "已归档"
                            ended -> "已结束"
                            else -> "${TimeFormat.toLocalDate(overview.endAt).let { "${it.monthValue}月${it.dayOfMonth}日" }} 结束"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ended && !closed) Color(0xFFA3601A)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onShift(1) }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "下一期")
                }
            }
        }

        if (closed) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TagChip(name = "已归档", colorArgb = 0xFF2F6B4F.toInt())
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "本期已于 ${doc?.closedAt?.let { TimeFormat.toLocalDate(it).let { d -> "${d.monthValue}月${d.dayOfMonth}日" } } ?: ""} 归档",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "看总结",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onOpenSummary() }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        } else if (ended) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFBF0D9))
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFF0D8A8))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "已到期",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF6B4408)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "本期已结束，写几句总结就可以归档",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF7A5410),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFB4803A))
                                .clickable { onClose() }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "写总结并归档",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        item {
            OverviewCard(
                overview = overview,
                closed = closed,
                onCopyPrevious = onCopyPrevious
            )
        }

        val rows = overview.rows
        if (rows.isEmpty()) {
            item {
                EmptyHint(
                    "本期还没有计划目标。\n点右上角「新增计划」，给标签或具体事项定时长。"
                )
            }
        } else {
            item {
                val allOpen = rows.all { openTags[it.tag.id] == true }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "各标签目标",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (allOpen) "收起全部" else "展开全部",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                rows.forEach { openTags[it.tag.id] = !allOpen }
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }

            items(rows, key = { "row${it.tag.id}" }) { row ->
                PlanRowCard(
                    row = row,
                    expanded = openTags[row.tag.id] == true,
                    closed = closed,
                    onToggle = { onToggleTag(row.tag.id) },
                    onEditTag = { onEditTag(row) },
                    onEditItem = { onEditItem(it) }
                )
            }
        }

        if (!closed) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onAdd() }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "＋ 新增计划目标（标签 / 具体事项）",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (!closed && !ended && overview.hasPlan) {
            item {
                TextButton(onClick = { onClose() }, modifier = Modifier.fillMaxWidth()) {
                    Text("提前结束本期并归档")
                }
            }
        }

        item {
            Text(
                text = "标签和事项可以各设目标：两者并存时，事项目标算作该标签的拆解，不会重复计入本期总目标；" +
                    "只给事项设了目标、所属标签没设时，事项目标会计入总目标。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OverviewCard(overview: PlanOverview, closed: Boolean, onCopyPrevious: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "本期计划完成度",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    modifier = Modifier.weight(1f)
                )
                if (!closed) {
                    Text(
                        text = "沿用上期",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onCopyPrevious() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                } else {
                    Text(
                        text = "已归档",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))

            if (!overview.hasPlan) {
                Text(
                    text = "还没定计划",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "点右上角「新增计划」，选一个标签或具体事项，设定「${overview.periodLabel}」要花多少小时。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                )
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${overview.percent}%",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "已用 ${TimeFormat.human(overview.totalUsedMillis)} / 计划 ${TimeFormat.human(overview.totalTargetMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                RatioBar(
                    fraction = if (overview.totalTargetMillis > 0L) {
                        (overview.totalUsedMillis.toFloat() / overview.totalTargetMillis).coerceIn(0f, 1f)
                    } else 0f,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun PlanRowCard(
    row: PlanProgress,
    expanded: Boolean,
    closed: Boolean,
    onToggle: () -> Unit,
    onEditTag: () -> Unit,
    onEditItem: (ItemPlanProgress) -> Unit
) {
    val color = Color(row.tag.colorArgb)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                Spacer(Modifier.width(9.dp))
                Text(
                    text = row.tag.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                TagChip(name = "${row.itemCount} 项 · ${row.usedCount} 次", colorArgb = row.tag.colorArgb)
                Spacer(Modifier.width(6.dp))
                if (row.hasTarget) {
                    Text(
                        text = "${row.percent}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (row.overMillis > 0L) Color(0xFFA3601A) else color
                    )
                } else if (row.itemTargetMinutes > 0) {
                    Text(
                        text = "按事项",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "未设目标",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!closed) {
                    IconButton(onClick = onEditTag, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "设定标签目标",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = when {
                    row.hasTarget -> "已用 ${TimeFormat.human(row.usedMillis)} / 计划 ${TimeFormat.human(row.targetMillis)}" +
                        if (row.overMillis > 0L) " · 已超 ${TimeFormat.human(row.overMillis)}"
                        else " · 还剩 ${TimeFormat.human(row.remainMillis)}"

                    row.itemTargetMinutes > 0 -> "已用 ${TimeFormat.human(row.usedMillis)} · 未设标签目标，事项拆解 ${
                        TimeFormat.human(row.itemTargetMinutes * 60_000L)
                    }"

                    else -> "已用 ${TimeFormat.human(row.usedMillis)} · 未设目标"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (row.hasTarget) {
                Spacer(Modifier.height(7.dp))
                RatioBar(fraction = row.fraction, color = color)
            }

            if (expanded) {
                Spacer(Modifier.height(4.dp))
                if (row.items.isEmpty()) {
                    Text(
                        text = "这个标签下还没有事项",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    row.items.forEach { item ->
                        ItemPlanRow(item = item, closed = closed, onEdit = { onEditItem(item) })
                    }
                }

                val itemSum = row.itemTargetMinutes
                if (row.hasTarget && itemSum > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "事项拆解合计 ${TimeFormat.human(itemSum * 60_000L)} / 标签目标 ${
                            TimeFormat.human(row.targetMillis)
                        }" + if (itemSum * 60_000L != row.targetMillis) "（两者不必相等）" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (!row.hasTarget && itemSum > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "只给事项定了目标，合计 ${TimeFormat.human(itemSum * 60_000L)}，会算进本期总目标",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemPlanRow(item: ItemPlanProgress, closed: Boolean, onEdit: () -> Unit) {
    val color = Color(item.item.colorArgb)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = !closed) { onEdit() }
            .padding(top = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ColorDot(item.item.colorArgb, 8)
            Spacer(Modifier.width(8.dp))
            Text(
                text = item.item.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${item.usedCount} 次",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = TimeFormat.human(item.usedMillis),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            if (!closed) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "设定事项目标",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(14.dp)
                )
            }
        }
        if (item.hasTarget) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "目标 ${TimeFormat.human(item.targetMillis)} · " +
                    (if (item.overMillis > 0L) "已超 ${TimeFormat.human(item.overMillis)}"
                    else "还差 ${TimeFormat.human(item.remainMillis)}") +
                    "  ${item.percent}%",
                style = MaterialTheme.typography.bodySmall,
                color = if (item.overMillis > 0L) Color(0xFFA3601A) else color
            )
            Spacer(Modifier.height(5.dp))
            RatioBar(fraction = item.fraction, color = color)
        }
    }
}

// ------------------------------------------------------------------ 历史计划

@Composable
private fun ColumnScope.HistoryTab(
    due: List<com.liubishchev.timebook.util.DuePeriod>,
    archived: List<PlanDoc>,
    onWriteSummary: (com.liubishchev.timebook.util.DuePeriod) -> Unit,
    onOpenDetail: (PlanDoc) -> Unit
) {
    LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (due.isEmpty() && archived.isEmpty()) {
            item {
                EmptyHint("还没有到期或归档的计划。\n一期计划结束后，会在这里提示写总结。")
            }
        }

        if (due.isNotEmpty()) {
            item {
                Text(
                    text = "待总结 · ${due.size} 期",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFA3601A)
                )
            }
            items(due, key = { "due${it.period.type}${it.key}" }) { d ->
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFBF0D9))
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = d.label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            TagChip(name = "已到期", colorArgb = 0xFFB4803A.toInt())
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${TimeFormat.toLocalDate(d.endAt).let { "${it.year}年${it.monthValue}月${it.dayOfMonth}日" }} 结束",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF7A5410)
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFB4803A))
                                    .clickable { onWriteSummary(d) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "写总结并归档",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        if (archived.isNotEmpty()) {
            item {
                Spacer(Modifier.height(if (due.isEmpty()) 0.dp else 6.dp))
                Text(
                    text = "已归档 · ${archived.size} 期",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(archived, key = { "doc${it.periodType}${it.periodKey}" }) { d ->
                ArchiveCard(doc = d, onClick = { onOpenDetail(d) })
            }
        }
    }
}

@Composable
private fun ArchiveCard(doc: PlanDoc, onClick: () -> Unit) {
    val snapshot = remember(doc.snapshotJson) { parseSnapshot(doc.snapshotJson) }
    val label = snapshot?.label?.takeIf { it.isNotBlank() }
        ?: com.liubishchev.timebook.util.planPeriodSpecFromKey(doc.periodType, doc.periodKey)?.label
        ?: doc.periodKey
    val percent = snapshot?.percent ?: 0

    Card(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "完成度 ${percent}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (percent >= 100) Color(0xFF2F6B4F) else Color(0xFFA3601A)
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = if (snapshot != null) {
                    "计划 ${TimeFormat.human(snapshot.totalTargetMinutes * 60_000L)} · 实际 ${
                        TimeFormat.human(snapshot.totalUsedMillis)
                    } · ${snapshot.recordCount} 次"
                } else {
                    "归档记录（无快照）"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (doc.summary.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = doc.summary.lineSequence().first(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ------------------------------------------------------------------ 弹窗

@Composable
private fun PlanPickerDialog(
    periodLabel: String,
    itemTab: Int,
    onTabChange: (Int) -> Unit,
    tags: List<Tag>,
    items: List<TaskItem>,
    tagUsed: (Long) -> PlanProgress?,
    itemUsed: (Long) -> ItemPlanProgress?,
    tagTargetOf: (Long) -> Int,
    itemTargetOf: (Long) -> Int,
    onPickTag: (Tag) -> Unit,
    onPickItem: (TaskItem) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增计划 · $periodLabel") },
        text = {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(3.dp)
                ) {
                    listOf("按标签", "按具体事项").forEachIndexed { index, label ->
                        val on = itemTab == index
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                                .clickable { onTabChange(index) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (on) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "点一行设定时长，已有目标的可在这里改",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))

                val list = if (itemTab == 0) tags else items
                if (list.isEmpty()) {
                    EmptyHint(if (itemTab == 0) "还没有标签，先去「事项」页建一个。" else "还没有事项，先去「事项」页建一个。")
                } else {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        if (itemTab == 0) {
                            items(tags, key = { "pick${it.id}" }) { tag ->
                                val row = tagUsed(tag.id)
                                val target = tagTargetOf(tag.id)
                                PickerRow(
                                    name = tag.name,
                                    colorArgb = tag.colorArgb,
                                    subtitle = "${row?.itemCount ?: 0} 个事项 · ${row?.usedCount ?: 0} 次 · 已用 " +
                                        (row?.usedMillis?.takeIf { it > 0L }?.let { TimeFormat.human(it) } ?: "还没记录"),
                                    targetText = if (target > 0) "目标 ${TimeFormat.human(target * 60_000L)}" else "未设目标",
                                    targetColor = if (target > 0) Color(tag.colorArgb)
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    onClick = { onPickTag(tag) }
                                )
                            }
                        } else {
                            items(items, key = { "pick${it.id}" }) { item ->
                                val row = itemUsed(item.id)
                                val target = itemTargetOf(item.id)
                                PickerRow(
                                    name = item.name,
                                    colorArgb = item.colorArgb,
                                    subtitle = "${row?.usedCount ?: 0} 次 · 已用 " +
                                        (row?.usedMillis?.takeIf { it > 0L }?.let { TimeFormat.human(it) } ?: "还没记录"),
                                    targetText = if (target > 0) "目标 ${TimeFormat.human(target * 60_000L)}" else "未设目标",
                                    targetColor = if (target > 0) Color(item.colorArgb)
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    onClick = { onPickItem(item) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun PickerRow(
    name: String,
    colorArgb: Int,
    subtitle: String,
    targetText: String,
    targetColor: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(Color(colorArgb))
        )
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Text(
            text = targetText,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = targetColor
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun PlanTargetDialog(
    title: String,
    kindLabel: String,
    parentHint: String?,
    initialMinutes: Int,
    presets: List<Int>,
    onDismiss: () -> Unit,
    onConfirm: (minutes: Int) -> Unit
) {
    var text by remember {
        mutableStateOf(if (initialMinutes <= 0) "" else trimZero(initialMinutes / 60.0))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("$title · $kindLabel", style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column {
                if (parentHint != null) {
                    Text(
                        text = parentHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { input ->
                        text = input.filter { it.isDigit() || it == '.' }.take(6)
                    },
                    label = { Text("计划时长（小时）") },
                    placeholder = { Text("比如 40") },
                    supportingText = { Text("可填小数，0.5 = 30 分钟；留空或 0 = 取消这个$kindLabel 的计划") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                // 五年计划的快捷值是「5000 小时」这种长文本，五个并排会超出弹窗宽度，改成可横向滑动
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { h ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { text = h.toString() }
                                .padding(horizontal = 9.dp, vertical = 6.dp)
                        ) {
                            Text("$h 小时", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "计划只影响进度显示，不限制计时。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val hours = text.trim().toDoubleOrNull() ?: 0.0
                onConfirm((hours * 60).toInt().coerceAtLeast(0))
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun ClosePlanDialog(
    overview: PlanOverview,
    onDismiss: () -> Unit,
    onConfirm: (summary: String) -> Unit
) {
    var summary by remember { mutableStateOf("") }
    val rows = overview.rows.filter { it.hasTarget || it.usedMillis > 0L }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("结束「${overview.periodLabel}」") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            text = "自动统计",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "完成度 ${overview.percent}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "计划 ${TimeFormat.human(overview.totalTargetMillis)} · 实际 ${
                                TimeFormat.human(overview.totalUsedMillis)
                            } · ${rows.sumOf { it.usedCount }} 次",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        rows.forEach { row ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ColorDot(row.tag.colorArgb, 8)
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    text = row.tag.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${TimeFormat.human(row.usedMillis)} / " +
                                        if (row.hasTarget) TimeFormat.human(row.targetMillis) else "未定",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (row.hasTarget) "${row.percent}%" else "—",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = when {
                                        !row.hasTarget -> MaterialTheme.colorScheme.onSurfaceVariant
                                        row.usedMillis >= row.targetMillis -> Color(0xFF2F6B4F)
                                        else -> Color(0xFFA3601A)
                                    },
                                    modifier = Modifier.width(38.dp),
                                    textAlign = TextAlign.End
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it.take(600) },
                    label = { Text("这一期的心得（可不写）") },
                    placeholder = { Text("比如：读书没跟上，下期改成每天 30 分钟") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "归档后本期目标锁定不能再改，记录仍可查看；总结留空也能归档。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(summary) }) { Text("归档") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun ArchiveDetailDialog(
    doc: PlanDoc,
    onDismiss: () -> Unit,
    onSaveSummary: (String) -> Unit,
    onReopen: () -> Unit
) {
    val snapshot = remember(doc.snapshotJson) { parseSnapshot(doc.snapshotJson) }
    var summary by remember(doc) { mutableStateOf(doc.summary) }
    var confirmReopen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = snapshot?.label?.takeIf { it.isNotBlank() } ?: doc.periodKey,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                if (snapshot != null) {
                    Text(
                        text = "完成度 ${snapshot.percent}% · 计划 ${TimeFormat.human(snapshot.totalTargetMinutes * 60_000L)}" +
                            " · 实际 ${TimeFormat.human(snapshot.totalUsedMillis)} · ${snapshot.recordCount} 次",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    snapshot.rows.forEach { row ->
                        SnapshotRowView(row)
                    }
                } else {
                    Text(
                        text = "这一期没有保存统计快照。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "总结",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it.take(600) },
                    label = { Text("这一期的心得") },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (!confirmReopen) {
                    TextButton(onClick = { confirmReopen = true }) { Text("取消归档") }
                } else {
                    Text(
                        text = "取消归档会把这一期放回「本期」，总结保留、目标重新可改。确定吗？",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Row {
                        TextButton(onClick = onReopen) { Text("确定取消归档") }
                        TextButton(onClick = { confirmReopen = false }) { Text("算了") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSaveSummary(summary) }) { Text("保存总结") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun SnapshotRowView(row: SnapshotRow) {
    Column(Modifier.padding(top = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ColorDot(row.colorArgb, 8)
            Spacer(Modifier.width(7.dp))
            Text(
                text = row.name,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${TimeFormat.human(row.usedMillis)} / " +
                    if (row.targetMinutes > 0) TimeFormat.human(row.targetMinutes * 60_000L) else "未定",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (row.targetMinutes > 0) {
                    "${(row.usedMillis * 100 / (row.targetMinutes * 60_000L)).toInt()}%"
                } else "—",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (row.targetMinutes > 0 && row.usedMillis >= row.targetMinutes * 60_000L) {
                    Color(0xFF2F6B4F)
                } else if (row.targetMinutes > 0) Color(0xFFA3601A)
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(38.dp),
                textAlign = TextAlign.End
            )
        }
        row.items.forEach { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${TimeFormat.human(item.usedMillis)}" +
                        if (item.targetMinutes > 0) " / ${TimeFormat.human(item.targetMinutes * 60_000L)}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun rowTagOf(itemId: Long, links: List<ItemTag>, tags: List<Tag>): Tag? {
    val tagId = links.firstOrNull { it.itemId == itemId }?.tagId ?: return null
    return tags.firstOrNull { it.id == tagId }
}

private fun presetsFor(period: PlanPeriod): List<Int> = when (period) {
    PlanPeriod.MONTH -> listOf(5, 10, 20, 40, 80)
    PlanPeriod.YEAR -> listOf(50, 100, 200, 400, 800)
    PlanPeriod.FIVE -> listOf(500, 1000, 2000, 3000, 5000)
}

private fun trimZero(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
