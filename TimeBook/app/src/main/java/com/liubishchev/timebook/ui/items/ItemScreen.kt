package com.liubishchev.timebook.ui.items

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Unarchive
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liubishchev.timebook.data.Tag
import com.liubishchev.timebook.data.TaskItem
import com.liubishchev.timebook.ui.AppViewModel
import com.liubishchev.timebook.ui.components.SectionHeader
import com.liubishchev.timebook.ui.components.TagChip
import com.liubishchev.timebook.ui.dialogs.BackupDialog
import com.liubishchev.timebook.ui.dialogs.ItemEditDialog
import com.liubishchev.timebook.ui.dialogs.TagEditDialog
import com.liubishchev.timebook.util.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun ItemScreen(vm: AppViewModel) {
    val items by vm.items.collectAsState()
    val records by vm.records.collectAsState()
    val now by vm.now.collectAsState()
    val tags by vm.tags.collectAsState()
    val tagIdsByItem by vm.tagIdsByItem.collectAsState()

    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TaskItem?>(null) }
    var deleting by remember { mutableStateOf<TaskItem?>(null) }
    var showBackup by remember { mutableStateOf(false) }
    var creatingTag by remember { mutableStateOf(false) }
    var editingTag by remember { mutableStateOf<Tag?>(null) }
    var deletingTag by remember { mutableStateOf<Tag?>(null) }
    // 0 = 全部标签
    var filterTagId by rememberSaveable { mutableStateOf(0L) }

    val tagById = tags.associateBy { it.id }

    if (creating) {
        ItemEditDialog(
            initial = null,
            tags = tags,
            selectedTagIds = if (filterTagId != 0L) listOf(filterTagId) else emptyList(),
            onDismiss = { creating = false },
            onCreateTag = { name, color, note -> vm.saveTag(null, name, color, note) },
            onConfirm = { name, color, note, target, tagIds ->
                vm.saveItem(null, name, color, note, target, tagIds)
                creating = false
            }
        )
    }

    editing?.let { item ->
        ItemEditDialog(
            initial = item,
            tags = tags,
            selectedTagIds = tagIdsByItem[item.id].orEmpty(),
            onDismiss = { editing = null },
            onCreateTag = { name, color, note -> vm.saveTag(null, name, color, note) },
            onConfirm = { name, color, note, target, tagIds ->
                vm.saveItem(item, name, color, note, target, tagIds)
                editing = null
            }
        )
    }

    if (creatingTag) {
        TagEditDialog(
            initial = null,
            onDismiss = { creatingTag = false },
            onConfirm = { name, color, note ->
                vm.saveTag(null, name, color, note)
                creatingTag = false
            }
        )
    }

    editingTag?.let { tag ->
        TagEditDialog(
            initial = tag,
            onDismiss = { editingTag = null },
            onConfirm = { name, color, note ->
                vm.saveTag(tag, name, color, note)
                editingTag = null
            }
        )
    }

    deleting?.let { item ->
        val related = records.count { it.itemId == item.id }
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除事项「${item.name}」？") },
            text = {
                Text(
                    if (related > 0) {
                        "该事项下有 $related 条记录，会一并删除，且不可恢复。\n如果只是不想再计时，建议用「归档」，历史统计仍然保留。"
                    } else {
                        "该事项还没有任何记录，可以安全删除。"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteItem(item)
                    deleting = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消") }
            }
        )
    }

    deletingTag?.let { tag ->
        val count = items.count { tagIdsByItem[it.id].orEmpty().contains(tag.id) }
        AlertDialog(
            onDismissRequest = { deletingTag = null },
            title = { Text("删除标签「${tag.name}」？") },
            text = {
                Text(
                    if (count > 0) {
                        "有 $count 个事项挂着这个标签。删除标签不会删事项和记录，只是它们不再属于这个标签。"
                    } else {
                        "这个标签下还没有事项，可以安全删除。"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteTag(tag, count)
                    if (filterTagId == tag.id) filterTagId = 0L
                    deletingTag = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deletingTag = null }) { Text("取消") }
            }
        )
    }

    if (showBackup) {
        BackupDialog(vm = vm, onDismiss = { showBackup = false })
    }

    val visible = if (filterTagId == 0L) {
        items
    } else {
        items.filter { tagIdsByItem[it.id].orEmpty().contains(filterTagId) }
    }
    val active = visible.filter { !it.archived }
    val archived = visible.filter { it.archived }
    val currentTag = tagById[filterTagId]

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "事项",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "标签归类，事项在标签下面；一件事可以挂多个标签",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { showBackup = true }) { Text("备份与恢复") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { creatingTag = true }) { Text("＋ 标签") }
            }

            // 标签筛选条
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                item {
                    FilterChipBox(
                        text = "全部",
                        color = MaterialTheme.colorScheme.primary,
                        selected = filterTagId == 0L,
                        onClick = { filterTagId = 0L }
                    )
                }
                items(tags, key = { "t${it.id}" }) { tag ->
                    FilterChipBox(
                        text = tag.name,
                        color = Color(tag.colorArgb),
                        selected = filterTagId == tag.id,
                        onClick = { filterTagId = if (filterTagId == tag.id) 0L else tag.id }
                    )
                }
            }

            if (currentTag != null) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "标签「${currentTag.name}」",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { editingTag = currentTag }) { Text("改名/改色") }
                    TextButton(onClick = { deletingTag = currentTag }) { Text("删除标签") }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (items.isEmpty() || (tags.isEmpty() && items.isEmpty())) {
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(Modifier.padding(20.dp)) {
                                Text("先建标签，再建事项", fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "标签是大类（读书 / 工作 / 运动），事项是具体的事（三体 / 周报 / 晨跑）。\n" +
                                        "先点右上角「＋ 标签」建一个，再点右下角「新建事项」把具体的事挂上去。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (tags.isEmpty() && items.isNotEmpty()) {
                    item {
                        Text(
                            text = "还没有标签，下面的事项都没归类。点右上角「＋ 标签」补一个吧。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                if (active.isNotEmpty()) {
                    item { SectionHeader("使用中", "${active.size} 项") }
                    items(active, key = { "a${it.id}" }) { item ->
                        ItemRow(
                            item = item,
                            tags = tagIdsByItem[item.id].orEmpty().mapNotNull { tagById[it] },
                            totalMillis = totalFor(item, records, now),
                            onEdit = { editing = item },
                            onToggleArchive = { vm.setArchived(item, true) },
                            onDelete = { deleting = item }
                        )
                    }
                }

                if (archived.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        SectionHeader("已归档", "${archived.size} 项")
                    }
                    items(archived, key = { "b${it.id}" }) { item ->
                        ItemRow(
                            item = item,
                            tags = tagIdsByItem[item.id].orEmpty().mapNotNull { tagById[it] },
                            totalMillis = totalFor(item, records, now),
                            onEdit = { editing = item },
                            onToggleArchive = { vm.setArchived(item, false) },
                            onDelete = { deleting = item }
                        )
                    }
                }

                if (visible.isEmpty() && (items.isNotEmpty() || tags.isNotEmpty())) {
                    item {
                        Text(
                            text = if (currentTag != null) {
                                "这个标签下还没有事项。点右下角「新建事项」，会自动挂到「${currentTag.name}」。"
                            } else {
                                "这段时间没有可显示的事项。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { creating = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("新建事项") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
}

@Composable
private fun FilterChipBox(
    text: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun totalFor(
    item: TaskItem,
    records: List<com.liubishchev.timebook.data.TimeRecord>,
    now: Long
): Long = records.filter { it.itemId == item.id }.sumOf { it.millisAt(now) }

@Composable
private fun ItemRow(
    item: TaskItem,
    tags: List<Tag>,
    totalMillis: Long,
    onEdit: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(item.colorArgb))
            )
            Spacer(Modifier.width(12.dp))
            Column(
                Modifier
                    .weight(1f)
                    .clickable { onEdit() }
            ) {
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
                        if (tags.size > 3) {
                            TagChip(name = "+${tags.size - 3}", colorArgb = 0xFF6B7280.toInt())
                        }
                    }
                }
                Text(
                    text = buildString {
                        append("累计 ${TimeFormat.human(totalMillis)}")
                        if (item.targetMinutes > 0) {
                            append(" · 目标 ${item.targetMinutes} 分")
                        }
                        if (item.note.isNotBlank()) {
                            append(" · ")
                            append(item.note)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "编辑",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onToggleArchive) {
                Icon(
                    imageVector = if (item.archived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                    contentDescription = if (item.archived) "恢复" else "归档",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
