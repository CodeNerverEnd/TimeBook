package com.liubishchev.timebook.ui.dialogs

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liubishchev.timebook.ui.AppViewModel
import com.liubishchev.timebook.util.AutoBackup
import com.liubishchev.timebook.util.BackupJson
import com.liubishchev.timebook.util.BackupPrefs
import com.liubishchev.timebook.util.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * 备份与恢复。
 *
 * 这里把「备份里到底有什么」直接列出来 —— 之前入口藏在两个小按钮后面，
 * 看不到备份内容，会让人担心计划（尤其每期总结）到底有没有被备份。
 */
@Composable
fun BackupDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val tags by vm.tags.collectAsState()
    val items by vm.items.collectAsState()
    val records by vm.records.collectAsState()
    val links by vm.tagIdsByItem.collectAsState()
    val planTargets by vm.planTargets.collectAsState()
    val planDocs by vm.planDocs.collectAsState()

    var lastAt by remember { mutableLongStateOf(BackupPrefs.lastBackupAt(context)) }
    var auto by remember { mutableStateOf(BackupPrefs.autoEnabled(context)) }
    var tree by remember { mutableStateOf(BackupPrefs.treeUri(context)) }
    var pendingImport by remember { mutableStateOf<String?>(null) }

    val planTagCount = planTargets.count { it.targetType == com.liubishchev.timebook.data.PlanTarget.TYPE_TAG }
    val planItemCount = planTargets.size - planTagCount
    val monthCount = planTargets.count { it.periodType == "MONTH" }
    val yearCount = planTargets.count { it.periodType == "YEAR" }
    val fiveCount = planTargets.count { it.periodType == "FIVE" }
    val docCount = planDocs.count { it.isClosed }
    val docWithSummary = planDocs.count { it.isClosed && it.summary.isNotBlank() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val json = vm.exportJson()
                        context.contentResolver.openOutputStream(uri)?.use { os ->
                            os.write(json.toByteArray(Charsets.UTF_8))
                        }
                    }
                    val now = System.currentTimeMillis()
                    BackupPrefs.setLastBackupAt(context, now)
                    lastAt = now
                    vm.notify("已导出备份")
                } catch (e: Exception) {
                    vm.notify("导出失败：${e.message ?: "未知错误"}")
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    }.getOrNull()
                }
                if (text.isNullOrBlank()) vm.notify("读取文件失败") else pendingImport = text
            }
        }
    }

    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            BackupPrefs.setTreeUri(context, uri.toString())
            BackupPrefs.setAutoEnabled(context, true)
            tree = uri.toString()
            auto = true
            scope.launch {
                runCatching { AutoBackup.writeBackup(context, uri) }
                    .onSuccess {
                        lastAt = BackupPrefs.lastBackupAt(context)
                        vm.notify("已开启自动备份，并已写入一份：$it")
                    }
                    .onFailure { vm.notify("自动备份写入失败：${it.message ?: "无法写入所选文件夹"}") }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("备份与恢复") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.padding(14.dp)) {
                        BackupLine("标签", "${tags.size} 个")
                        BackupLine("事项", "${items.size} 个")
                        BackupLine("标签关联", "${links.values.sumOf { it.size }} 条")
                        BackupLine("记录", "${records.size} 条")
                        Spacer(Modifier.height(6.dp))
                        BackupLine(
                            text = "计划目标",
                            value = "${planTargets.size} 条 · 月$monthCount / 年$yearCount / 五年$fiveCount",
                            strong = true
                        )
                        BackupLine(
                            text = "计划档案与总结",
                            value = "$docCount 份 · $docWithSummary 份有总结",
                            strong = true
                        )
                        if (planItemCount > 0) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "其中标签目标 $planTagCount 条 / 事项目标 $planItemCount 条",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "上次备份：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (lastAt > 0L) {
                            val d = TimeFormat.toLocalDate(lastAt)
                            val t = TimeFormat.toLocalTime(lastAt)
                            "%d-%02d-%02d %02d:%02d".format(
                                d.year, d.monthValue, d.dayOfMonth, t.hour, t.minute
                            )
                        } else "从未备份过",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (lastAt > 0L) MaterialTheme.colorScheme.onSurface else Color(0xFFA3601A)
                    )
                }

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionPill("导出备份文件", filled = true) {
                        exportLauncher.launch("TimeBook-备份-${LocalDate.now()}.json")
                    }
                    ActionPill("从文件恢复", filled = false) {
                        importLauncher.launch(arrayOf("*/*"))
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("自动备份", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            text = if (auto && tree != null) "每天一次，保留最近 5 份" else "默认关闭，开启后每天自动存一份",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = auto,
                        onCheckedChange = { on ->
                            if (on) {
                                treeLauncher.launch(tree?.let { android.net.Uri.parse(it) })
                            } else {
                                BackupPrefs.setAutoEnabled(context, false)
                                auto = false
                                vm.notify("已关闭自动备份")
                            }
                        }
                    )
                }
                if (auto && tree != null) {
                    Text(
                        text = "备份文件夹：${tree}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    TextButton(onClick = {
                        BackupPrefs.setAutoEnabled(context, false)
                        auto = false
                        vm.notify("已关闭自动备份")
                    }) { Text("关闭自动备份") }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = "导出的是一个 JSON 文件，里面包含标签、事项、记录、计划目标、每期总结和归档快照。" +
                        "换手机前把文件发到微信或网盘，再在新手机上「从文件恢复」即可；" +
                        "卸载 App 会连备份一起删掉，注意先转出去。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                pendingImport?.let { text ->
                    Spacer(Modifier.height(10.dp))
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFBF0D9))
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                text = "导入会整体覆盖本机现有的标签、事项、记录、计划目标和计划总结，当前数据不可恢复。建议先导出一份。",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF7A5410)
                            )
                            Spacer(Modifier.height(6.dp))
                            Row {
                                TextButton(onClick = {
                                    vm.importJson(text)
                                    pendingImport = null
                                }) { Text("覆盖导入") }
                                TextButton(onClick = { pendingImport = null }) { Text("取消") }
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
private fun BackupLine(text: String, value: String, strong: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
            color = if (strong) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
            color = if (strong) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActionPill(text: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (filled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        )
    }
}
