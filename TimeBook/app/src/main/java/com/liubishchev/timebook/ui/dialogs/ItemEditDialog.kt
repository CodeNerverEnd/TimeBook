package com.liubishchev.timebook.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liubishchev.timebook.data.Tag
import com.liubishchev.timebook.data.TaskItem
import com.liubishchev.timebook.ui.theme.PresetColors

/**
 * 新建 / 编辑事项。initial 为 null 表示新建。
 *
 * 标签是必填（至少一个）、可多选：一件事可以同时属于「读书」和「通勤」。
 * 名称、颜色、备注、默认目标时长、标签都可在任何时候改回来。
 */
@Composable
fun ItemEditDialog(
    initial: TaskItem?,
    tags: List<Tag>,
    selectedTagIds: List<Long>,
    onDismiss: () -> Unit,
    onCreateTag: (name: String, colorArgb: Int, note: String) -> Unit,
    onConfirm: (name: String, colorArgb: Int, note: String, targetMinutes: Int, tagIds: List<Long>) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var colorArgb by remember { mutableIntStateOf(initial?.colorArgb ?: PresetColors.first()) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var targetText by remember {
        mutableStateOf(
            initial?.targetMinutes?.takeIf { it > 0 }?.toString() ?: ""
        )
    }
    val pickedTagIds = remember {
        mutableStateListOf<Long>().apply { addAll(selectedTagIds) }
    }
    var showTagDialog by remember { mutableStateOf(false) }

    if (showTagDialog) {
        TagEditDialog(
            initial = null,
            onDismiss = { showTagDialog = false },
            onConfirm = { tagName, tagColor, tagNote ->
                onCreateTag(tagName, tagColor, tagNote)
                showTagDialog = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新建事项" else "编辑事项") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("事项名称") },
                    placeholder = { Text("比如：三体") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "标签（可多选）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "＋ 新建标签",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showTagDialog = true }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))

                if (tags.isEmpty()) {
                    Text(
                        text = "还没有标签。先点右上角「＋ 新建标签」，比如建一个「读书」。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        tags.forEach { tag ->
                            val picked = pickedTagIds.contains(tag.id)
                            val c = Color(tag.colorArgb)
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (picked) c.copy(alpha = 0.18f) else Color.Transparent)
                                    .border(
                                        width = if (picked) 1.5.dp else 1.dp,
                                        color = if (picked) c else MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        if (picked) pickedTagIds.remove(tag.id)
                                        else pickedTagIds.add(tag.id)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = tag.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (picked) c else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = targetText,
                    onValueChange = { input -> targetText = input.filter { it.isDigit() }.take(4) },
                    label = { Text("默认目标时长（分钟）") },
                    supportingText = { Text("留空 = 不设目标，计时时不会提醒") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "颜色",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                PresetColors.chunked(4).forEach { rowColors ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowColors.forEach { c ->
                            val selected = c == colorArgb
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(c))
                                    .then(
                                        if (selected) {
                                            Modifier.border(
                                                width = 2.dp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                shape = CircleShape
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable { colorArgb = c }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val target = targetText.trim().toIntOrNull() ?: 0
                onConfirm(
                    name,
                    colorArgb,
                    note,
                    target.coerceAtLeast(0),
                    pickedTagIds.toList()
                )
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
