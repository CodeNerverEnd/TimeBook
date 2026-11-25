package com.liubishchev.timebook.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liubishchev.timebook.data.TaskItem
import com.liubishchev.timebook.data.TimeRecord
import com.liubishchev.timebook.util.TimeFormat
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

private const val MILLIS_PER_DAY = 86_400_000L

private fun hhmm(hour: Int, minute: Int): String =
    String.format(Locale.US, "%02d:%02d", hour, minute)

/**
 * 补录 / 编辑一条时间记录。
 * initial 为 null 表示补录（此时用 defaultItemId 预选事项）。
 * 结束时刻早于开始时刻时，自动理解为「次日结束」，方便跨零点记录。
 */
@Composable
fun RecordEditDialog(
    title: String,
    initial: TimeRecord?,
    items: List<TaskItem>,
    defaultItemId: Long? = null,
    onDismiss: () -> Unit,
    onConfirm: (itemId: Long, startAt: Long, endAt: Long, note: String) -> Unit
) {
    val startRef = initial?.let { TimeFormat.toLocalTime(it.startAt) } ?: LocalTime.of(8, 0)
    val endRef = initial?.endAt?.let { TimeFormat.toLocalTime(it) } ?: LocalTime.of(9, 0)

    var selectedItemId by remember {
        mutableStateOf(initial?.itemId ?: defaultItemId ?: items.firstOrNull()?.id ?: -1L)
    }
    var dateEpochDay by remember {
        mutableLongStateOf(
            initial?.let { TimeFormat.toLocalDate(it.startAt).toEpochDay() } ?: LocalDate.now().toEpochDay()
        )
    }
    var startHour by remember { mutableIntStateOf(startRef.hour) }
    var startMinute by remember { mutableIntStateOf(startRef.minute) }
    var endHour by remember { mutableIntStateOf(endRef.hour) }
    var endMinute by remember { mutableIntStateOf(endRef.minute) }
    var note by remember { mutableStateOf(initial?.note ?: "") }

    var itemMenuOpen by remember { mutableStateOf(false) }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }

    val date = LocalDate.ofEpochDay(dateEpochDay)
    val startAt = TimeFormat.withTime(date, startHour, startMinute)
    var endAt = TimeFormat.withTime(date, endHour, endMinute)
    val crossesMidnight = endAt < startAt
    if (crossesMidnight) endAt += MILLIS_PER_DAY
    val duration = endAt - startAt
    val selectedItem = items.firstOrNull { it.id == selectedItemId }

    if (pickingDate) {
        PickDateDialog(
            initial = date,
            onDismiss = { pickingDate = false },
            onConfirm = { dateEpochDay = it.toEpochDay() }
        )
    }
    if (pickingStart) {
        PickTimeDialog(
            title = "开始时间",
            hour = startHour,
            minute = startMinute,
            onDismiss = { pickingStart = false },
            onConfirm = { h, m -> startHour = h; startMinute = m }
        )
    }
    if (pickingEnd) {
        PickTimeDialog(
            title = "结束时间",
            hour = endHour,
            minute = endMinute,
            onDismiss = { pickingEnd = false },
            onConfirm = { h, m -> endHour = h; endMinute = m }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {

                // 事项
                Box {
                    OutlinedButton(
                        onClick = { itemMenuOpen = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = selectedItem?.name ?: "请选择事项",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    DropdownMenu(
                        expanded = itemMenuOpen,
                        onDismissRequest = { itemMenuOpen = false }
                    ) {
                        items.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.name) },
                                onClick = { selectedItemId = item.id; itemMenuOpen = false }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1.2f)) {
                        Text(
                            "日期",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { pickingDate = true },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)
                        ) {
                            Text(TimeFormat.shortDate(date), maxLines = 1)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            "开始",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { pickingStart = true },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)
                        ) {
                            Text(hhmm(startHour, startMinute), maxLines = 1)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            "结束",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { pickingEnd = true },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)
                        ) {
                            Text(hhmm(endHour, endMinute), maxLines = 1)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    text = when {
                        duration <= 0L -> "结束时间必须晚于开始时间"
                        else -> "时长 ${TimeFormat.human(duration)}" + if (crossesMidnight) "（次日结束）" else ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (duration <= 0L) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )

                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选，建议记一句当时的情况）") },
                    placeholder = { Text("比如：和客户对需求，改了三版") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedItemId, startAt, endAt, note) },
                enabled = duration > 0L && selectedItemId > 0L
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
