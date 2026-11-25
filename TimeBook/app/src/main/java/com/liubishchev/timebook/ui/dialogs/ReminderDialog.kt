package com.liubishchev.timebook.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.liubishchev.timebook.data.TimeRecord
import com.liubishchev.timebook.util.TimeFormat
import java.time.LocalTime
import java.util.Locale

private const val MILLIS_PER_DAY = 86_400_000L

/**
 * 本次提醒设置：
 *  - 做满时长：record.startAt + N 分钟
 *  - 到某时刻：今天的 HH:mm（已过则按次日）
 *  - 都清空 = 不提醒
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDialog(
    record: TimeRecord,
    onDismiss: () -> Unit,
    onConfirm: (targetMinutes: Int, remindAt: Long?) -> Unit
) {
    var clockMode by remember {
        mutableStateOf(record.remindAt != null && record.targetMinutes == 0)
    }
    var minutesText by remember {
        mutableStateOf(if (record.targetMinutes > 0) record.targetMinutes.toString() else "")
    }
    val initialClock: LocalTime = record.remindAt
        ?.takeIf { record.targetMinutes == 0 }
        ?.let { TimeFormat.toLocalTime(it) }
        ?: LocalTime.of(15, 0)
    var hour by remember { mutableIntStateOf(initialClock.hour) }
    var minute by remember { mutableIntStateOf(initialClock.minute) }
    var pickingTime by remember { mutableStateOf(false) }

    if (pickingTime) {
        PickTimeDialog(
            title = "提醒时刻",
            hour = hour,
            minute = minute,
            onDismiss = { pickingTime = false },
            onConfirm = { h, m ->
                hour = h
                minute = m
            }
        )
    }

    val minutes = minutesText.trim().toIntOrNull() ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("本次提醒") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !clockMode,
                        onClick = { clockMode = false },
                        label = { Text("做满时长") }
                    )
                    FilterChip(
                        selected = clockMode,
                        onClick = { clockMode = true },
                        label = { Text("到某时刻") }
                    )
                }

                Spacer(Modifier.height(14.dp))

                if (!clockMode) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 25, 45).forEach { preset ->
                            FilterChip(
                                selected = minutes == preset,
                                onClick = { minutesText = preset.toString() },
                                label = { Text("$preset 分") }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(60, 90).forEach { preset ->
                            FilterChip(
                                selected = minutes == preset,
                                onClick = { minutesText = preset.toString() },
                                label = { Text("$preset 分") }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = minutesText,
                        onValueChange = { input -> minutesText = input.filter { it.isDigit() }.take(4) },
                        label = { Text("自定义分钟数") },
                        supportingText = { Text("留空 = 不提醒") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedButton(
                        onClick = { pickingTime = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(String.format(Locale.US, "%02d:%02d", hour, minute))
                        Spacer(Modifier.width(8.dp))
                        Text("点击修改", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "如果这个时刻今天已经过了，按次日算。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = when {
                        clockMode -> {
                            val date = TimeFormat.toLocalDate(record.startAt)
                            var at = TimeFormat.withTime(date, hour, minute)
                            if (at <= System.currentTimeMillis()) at += MILLIS_PER_DAY
                            "将在 ${TimeFormat.hm(at)} 提醒，计时不会停止"
                        }

                        minutes > 0 -> "做满 $minutes 分钟（约 ${TimeFormat.hm(record.startAt + minutes * 60_000L)}）提醒"
                        else -> "不设提醒"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { onConfirm(0, null) }) { Text("清除") }
                TextButton(
                    onClick = {
                        if (clockMode) {
                            val date = TimeFormat.toLocalDate(record.startAt)
                            var at = TimeFormat.withTime(date, hour, minute)
                            if (at <= System.currentTimeMillis()) at += MILLIS_PER_DAY
                            onConfirm(0, at)
                        } else if (minutes > 0) {
                            onConfirm(minutes, record.startAt + minutes * 60_000L)
                        } else {
                            onConfirm(0, null)
                        }
                    }
                ) { Text("保存") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
