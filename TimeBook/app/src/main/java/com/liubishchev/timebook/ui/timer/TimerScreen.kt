package com.liubishchev.timebook.ui.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liubishchev.timebook.data.Tag
import com.liubishchev.timebook.data.TaskItem
import com.liubishchev.timebook.data.TimeRecord
import com.liubishchev.timebook.ui.AppViewModel
import com.liubishchev.timebook.ui.components.EmptyHint
import com.liubishchev.timebook.ui.components.TagChip
import com.liubishchev.timebook.ui.dialogs.NoteSheet
import com.liubishchev.timebook.ui.dialogs.RecordEditDialog
import com.liubishchev.timebook.ui.dialogs.ReminderDialog
import com.liubishchev.timebook.util.TimeFormat
import com.liubishchev.timebook.util.overlapMillis
import java.time.LocalDate

@Composable
fun TimerScreen(vm: AppViewModel) {
    val items by vm.items.collectAsState()
    val records by vm.records.collectAsState()
    val running by vm.running.collectAsState()
    val now by vm.now.collectAsState()
    val tags by vm.tags.collectAsState()
    val tagIdsByItem by vm.tagIdsByItem.collectAsState()
    val tagById = tags.associateBy { it.id }

    var showRecordDialog by remember { mutableStateOf(false) }
    var editingReminder by remember { mutableStateOf(false) }
    // 非空 = 正在给这条记录写备注；停止计时后自动置位，也可从计时卡片点开
    var noteTargetId by remember { mutableStateOf<Long?>(null) }

    val today = LocalDate.now()
    val dayStart = TimeFormat.atStartOfDay(today)
    val dayEnd = TimeFormat.atStartOfDay(today.plusDays(1))

    val todayTotal = records.sumOf { r -> overlapMillis(r.startAt, r.endAt ?: now, dayStart, dayEnd) }
    val todayByItem: Map<Long, Long> = records
        .mapNotNull { r ->
            val m = overlapMillis(r.startAt, r.endAt ?: now, dayStart, dayEnd)
            if (m > 0L) r.itemId to m else null
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { entry -> entry.value.sum() }

    val activeItems = items.filter { !it.archived }

    // 主动点「结束计时」后弹备注；切换事项 / 通知栏结束不弹，免得打断下一件事
    val stoppedId by vm.stoppedRecordId.collectAsState()
    LaunchedEffect(stoppedId) {
        val id = stoppedId ?: return@LaunchedEffect
        vm.consumeStoppedRecord()
        // 从通知栏结束后 App 可能在后台待很久，太旧的记录不必再弹
        val stopped = vm.records.value.firstOrNull { it.id == id }
        val end = stopped?.endAt
        if (end != null && System.currentTimeMillis() - end < 120_000L) {
            noteTargetId = id
        }
    }

    if (showRecordDialog) {
        RecordEditDialog(
            title = "补录时间",
            initial = null,
            items = activeItems,
            onDismiss = { showRecordDialog = false },
            onConfirm = { itemId, startAt, endAt, note ->
                vm.addRecord(itemId, startAt, endAt, note)
                showRecordDialog = false
            }
        )
    }

    running?.let { record ->
        if (editingReminder) {
            ReminderDialog(
                record = record,
                onDismiss = { editingReminder = false },
                onConfirm = { minutes, remindAt ->
                    vm.setReminder(record, minutes, remindAt)
                    editingReminder = false
                }
            )
        }
    }

    val noteRecord = noteTargetId?.let { id -> records.firstOrNull { it.id == id } }
    if (noteRecord != null) {
        val noteItem = items.firstOrNull { it.id == noteRecord.itemId }
        val endAt = noteRecord.endAt
        val remindAt = noteRecord.remindAt
        NoteSheet(
            itemName = noteItem?.name ?: "已删除事项",
            itemColorArgb = noteItem?.colorArgb ?: 0xFF9E9E9E.toInt(),
            startAt = noteRecord.startAt,
            endAt = endAt,
            now = now,
            initialNote = noteRecord.note,
            fresh = endAt != null,
            goalHint = if (endAt != null && remindAt != null) {
                if (endAt >= remindAt) "，目标达成 ✓" else "，未到目标"
            } else {
                null
            },
            onDismiss = { noteTargetId = null },
            onSave = { text -> vm.updateRecordNote(noteRecord.id, text) }
        )
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))

            Text(
                text = TimeFormat.dateLabel(today),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = TimeFormat.human(todayTotal),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "今日已记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            RunningCard(
                running = running,
                items = items,
                tagsOfRunning = running?.let { r ->
                    tagIdsByItem[r.itemId].orEmpty().mapNotNull { tagById[it] }
                }.orEmpty(),
                now = now,
                onStop = { vm.stopTimer() },
                onEditReminder = { editingReminder = true },
                onEditNote = { running?.let { noteTargetId = it.id } }
            )

            Spacer(Modifier.height(20.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "选择事项开始计时",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${activeItems.size} 项",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (activeItems.isEmpty()) {
                    item {
                        EmptyHint("还没有事项。\n去「事项」页新建一个，比如「写代码」「读书」。")
                    }
                }
                items(activeItems, key = { it.id }) { item ->
                    ItemRow(
                        item = item,
                        tags = tagIdsByItem[item.id].orEmpty().mapNotNull { tagById[it] },
                        isRunning = running?.itemId == item.id,
                        todayMillis = todayByItem[item.id] ?: 0L,
                        onClick = { vm.startTimer(item.id) }
                    )
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showRecordDialog = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("补录") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
}

@Composable
private fun RunningCard(
    running: TimeRecord?,
    items: List<TaskItem>,
    tagsOfRunning: List<Tag>,
    now: Long,
    onStop: () -> Unit,
    onEditReminder: () -> Unit,
    onEditNote: () -> Unit
) {
    val item = items.firstOrNull { it.id == running?.itemId }
    val remindAt = running?.remindAt
    val targetMinutes = running?.targetMinutes ?: 0
    val elapsed = if (running != null) (now - running.startAt).coerceAtLeast(0L) else 0L
    val goalTotal = if (running != null && remindAt != null) {
        (remindAt - running.startAt).coerceAtLeast(1L)
    } else {
        0L
    }
    val over = goalTotal > 0L && elapsed >= goalTotal
    val fraction = if (goalTotal > 0L) (elapsed.toFloat() / goalTotal).coerceIn(0f, 1f) else 0f

    val container = if (over) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val onContainer = if (over) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(Modifier.padding(20.dp)) {
            if (running == null || item == null) {
                Text(
                    text = "当前没有进行中的计时",
                    style = MaterialTheme.typography.titleMedium,
                    color = onContainer
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "点下面任一事项即可开始；结束后自动落账",
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer.copy(alpha = 0.75f)
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(item.colorArgb))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = onContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (tagsOfRunning.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            tagsOfRunning.take(2).forEach { tag ->
                                TagChip(name = tag.name, colorArgb = tag.colorArgb)
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "开始于 ${TimeFormat.hm(running.startAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = onContainer.copy(alpha = 0.75f)
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = TimeFormat.clock(elapsed),
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Light,
                    color = onContainer
                )

                if (remindAt != null) {
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(onContainer.copy(alpha = 0.16f))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (over) MaterialTheme.colorScheme.secondary
                                    else MaterialTheme.colorScheme.primary
                                )
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (targetMinutes > 0) {
                                "目标 $targetMinutes 分 · ${TimeFormat.hm(remindAt)} 提醒"
                            } else {
                                "${TimeFormat.hm(remindAt)} 提醒"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = onContainer.copy(alpha = 0.8f),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (over) {
                                "已超出 ${TimeFormat.countdown(elapsed - goalTotal)}"
                            } else {
                                "还剩 ${TimeFormat.countdown(goalTotal - elapsed)}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (over) MaterialTheme.colorScheme.secondary else onContainer
                        )
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "未设提醒",
                        style = MaterialTheme.typography.bodySmall,
                        color = onContainer.copy(alpha = 0.75f)
                    )
                }

                Spacer(Modifier.height(12.dp))

                // 计时中也能先写一句，不用等结束
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.55f))
                        .clickable { onEditNote() }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                        tint = onContainer.copy(alpha = 0.7f),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = running.note.ifBlank { "这一段时间在做什么，记一句" },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (running.note.isBlank()) {
                            onContainer.copy(alpha = 0.7f)
                        } else {
                            onContainer
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (running.note.isBlank()) "写" else "改",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onStop,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("结束计时")
                    }
                    OutlinedButton(onClick = onEditReminder) {
                        Text(if (remindAt != null) "改提醒" else "设提醒")
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemRow(
    item: TaskItem,
    tags: List<Tag>,
    isRunning: Boolean,
    todayMillis: Long,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(item.colorArgb))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (tags.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.take(3).forEach { tag ->
                            TagChip(name = tag.name, colorArgb = tag.colorArgb)
                        }
                    }
                }
                val subtitle = buildString {
                    if (todayMillis > 0L) append("今日 ${TimeFormat.human(todayMillis)}")
                    if (item.note.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(item.note)
                    }
                }
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isRunning) {
                Text(
                    text = "计时中",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "开始计时",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
