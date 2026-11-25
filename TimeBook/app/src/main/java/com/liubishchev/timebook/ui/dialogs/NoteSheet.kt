package com.liubishchev.timebook.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.liubishchev.timebook.util.TimeFormat
import kotlinx.coroutines.delay

/** 快捷词：点一下以「，」追加；已经出现过的词不再重复加。 */
private val NOTE_WORDS = listOf("顺利", "被打断", "走神了", "状态不错", "有点累")

/**
 * 「这次做了什么」备注面板。
 *
 * 停在停止计时之后自动弹出，也可以从计时卡片、记录列表随时打开。
 * 备注非必填：留空保存等于清空，直接关掉则什么都不改。
 *
 * @param fresh true = 刚结束计时的引导态（按钮文案为「先不写」）
 * @param goalHint 结束后接在时长后面的后缀，如「，目标达成 ✓」
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteSheet(
    itemName: String,
    itemColorArgb: Int,
    startAt: Long,
    endAt: Long?,
    now: Long,
    initialNote: String,
    fresh: Boolean,
    goalHint: String? = null,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember(initialNote) { mutableStateOf(initialNote) }
    val focusRequester = remember { FocusRequester() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 等展开动画走完再要焦点，否则键盘会被动画打断
    LaunchedEffect(Unit) {
        delay(220)
        runCatching { focusRequester.requestFocus() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(itemColorArgb))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = itemName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = TimeFormat.hm(startAt) + " - " +
                        (endAt?.let { TimeFormat.hm(it) } ?: "现在"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    append(if (endAt == null) "计时中 · 已用 " else "已记录 ")
                    append(TimeFormat.human((endAt ?: now) - startAt))
                    if (!goalHint.isNullOrBlank()) append(goalHint)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = if (fresh) "这一段时间在做什么？" else "备注",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "一句就够，之后回看时更容易想起当时的情况。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 500) text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                placeholder = { Text("比如：改支付回调的 bug，中途被会议打断 20 分钟") },
                minLines = 2,
                maxLines = 4,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                )
            )

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NOTE_WORDS.forEach { word ->
                    AssistChip(
                        onClick = {
                            val current = text.trim()
                            if (!current.contains(word)) {
                                text = if (current.isEmpty()) word else "$current，$word"
                            }
                        },
                        label = { Text(word) }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        onSave(text)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("保存备注")
                }
                OutlinedButton(onClick = onDismiss) {
                    Text(if (fresh) "先不写" else "取消")
                }
            }
        }
    }
}
