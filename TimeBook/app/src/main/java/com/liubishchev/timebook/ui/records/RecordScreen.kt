package com.liubishchev.timebook.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liubishchev.timebook.data.Tag
import com.liubishchev.timebook.data.TaskItem
import com.liubishchev.timebook.data.TimeRecord
import com.liubishchev.timebook.ui.AppViewModel
import com.liubishchev.timebook.ui.components.EmptyHint
import com.liubishchev.timebook.ui.components.TagChip
import com.liubishchev.timebook.ui.dialogs.NoteSheet
import com.liubishchev.timebook.ui.dialogs.PickDateDialog
import com.liubishchev.timebook.ui.dialogs.RecordEditDialog
import com.liubishchev.timebook.util.TimeFormat
import com.liubishchev.timebook.util.overlapMillis
import java.time.LocalDate

@Composable
fun RecordScreen(vm: AppViewModel) {
    val items by vm.items.collectAsState()
    val records by vm.records.collectAsState()
    val now by vm.now.collectAsState()
    val tags by vm.tags.collectAsState()
    val tagIdsByItem by vm.tagIdsByItem.collectAsState()
    val tagById = tags.associateBy { it.id }

    var epochDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var editing by remember { mutableStateOf<TimeRecord?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<TimeRecord?>(null) }
    var pickingDate by remember { mutableStateOf(false) }
    var noteEditing by remember { mutableStateOf<TimeRecord?>(null) }

    val date = LocalDate.ofEpochDay(epochDay)
    val dayStart = TimeFormat.atStartOfDay(date)
    val dayEnd = TimeFormat.atStartOfDay(date.plusDays(1))
    val dayRecords = records
        .filter { it.startAt < dayEnd && (it.endAt ?: now) > dayStart }
        .sortedBy { it.startAt }
    val total = dayRecords.sumOf { overlapMillis(it.startAt, it.endAt ?: now, dayStart, dayEnd) }
    val itemById: Map<Long, TaskItem> = items.associateBy { it.id }
    val activeItems = items.filter { !it.archived }

    if (pickingDate) {
        PickDateDialog(
            initial = date,
            onDismiss = { pickingDate = false },
            onConfirm = { epochDay = it.toEpochDay() }
        )
    }

    if (adding) {
        RecordEditDialog(
            title = "补录时间",
            initial = null,
            items = activeItems,
            onDismiss = { adding = false },
            onConfirm = { itemId, startAt, endAt, note ->
                vm.addRecord(itemId, startAt, endAt, note)
                adding = false
            }
        )
    }

    editing?.let { record ->
        RecordEditDialog(
            title = "编辑记录",
            initial = record,
            items = items,
            onDismiss = { editing = null },
            onConfirm = { itemId, startAt, endAt, note ->
                vm.updateRecord(record.copy(itemId = itemId, startAt = startAt, endAt = endAt, note = note))
                editing = null
            }
        )
    }

    deleting?.let { record ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除这条记录？") },
            text = {
                Text(
                    "${itemById[record.itemId]?.name ?: "未知事项"}  " +
                        "${TimeFormat.hm(record.startAt)} - " +
                        (record.endAt?.let { TimeFormat.hm(it) } ?: "进行中") +
                        "，时长 ${TimeFormat.human(record.millisAt(now))}。删除后不可恢复。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteRecord(record.id)
                    deleting = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消") }
            }
        )
    }

    noteEditing?.let { record ->
        val recordItem = itemById[record.itemId]
        val end = record.endAt
        val remindAt = record.remindAt
        NoteSheet(
            itemName = recordItem?.name ?: "已删除事项",
            itemColorArgb = recordItem?.colorArgb ?: 0xFF9E9E9E.toInt(),
            startAt = record.startAt,
            endAt = end,
            now = now,
            initialNote = record.note,
            fresh = false,
            goalHint = if (end != null && remindAt != null) {
                if (end >= remindAt) "，目标达成 ✓" else "，未到目标"
            } else {
                null
            },
            onDismiss = { noteEditing = null },
            onSave = { text -> vm.updateRecordNote(record.id, text) }
        )
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = { epochDay -= 1 }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "前一天")
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { pickingDate = true },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = TimeFormat.dateLabel(date),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "共 ${dayRecords.size} 段 · ${TimeFormat.human(total)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { epochDay += 1 }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "后一天")
                }
            }

            Spacer(Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (dayRecords.isEmpty()) {
                    item { EmptyHint("这一天还没有记录。\n可以点右下角补录，或回「计时」页开始。") }
                }
                items(dayRecords, key = { it.id }) { record ->
                    RecordRow(
                        record = record,
                        item = itemById[record.itemId],
                        tags = tagIdsByItem[record.itemId].orEmpty().mapNotNull { tagById[it] },
                        now = now,
                        onClick = { editing = record },
                        onDelete = { deleting = record },
                        onNoteClick = { noteEditing = record }
                    )
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("补录") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
}

@Composable
private fun RecordRow(
    record: TimeRecord,
    item: TaskItem?,
    tags: List<Tag>,
    now: Long,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onNoteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(item?.colorArgb ?: 0xFF9E9E9E.toInt()))
            )
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item?.name ?: "已删除事项",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    if (record.isRunning) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "计时中",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (tags.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        tags.take(2).forEach { tag ->
                            TagChip(name = tag.name, colorArgb = tag.colorArgb)
                            Spacer(Modifier.width(4.dp))
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(TimeFormat.hm(record.startAt))
                        append(" - ")
                        append(record.endAt?.let { TimeFormat.hm(it) } ?: "现在")
                        if (record.endAt != null && !TimeFormat.toLocalDate(record.startAt)
                                .equals(TimeFormat.toLocalDate(record.endAt))
                        ) {
                            append("（次日）")
                        }
                        record.remindAt?.let { at ->
                            append(" · ")
                            append(
                                if (record.targetMinutes > 0) "目标 ${record.targetMinutes} 分"
                                else "${TimeFormat.hm(at)} 提醒"
                            )
                            val end = record.endAt
                            if (end != null) {
                                append(if (end >= at) " ✓" else " 未达")
                            }
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 备注单独成块：一眼分清「几点到几点」和「当时在做什么」
                val hasNote = record.note.isNotBlank()
                Spacer(Modifier.height(if (hasNote) 5.dp else 3.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (hasNote) {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            } else {
                                Color.Transparent
                            }
                        )
                        .clickable { onNoteClick() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasNote) {
                        Box(
                            Modifier
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                    Text(
                        text = if (hasNote) record.note else "＋ 加一句备注",
                        modifier = Modifier.padding(
                            start = if (hasNote) 8.dp else 0.dp,
                            end = 4.dp,
                            top = if (hasNote) 5.dp else 0.dp,
                            bottom = if (hasNote) 5.dp else 0.dp
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasNote) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                text = TimeFormat.human(record.millisAt(now)),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
