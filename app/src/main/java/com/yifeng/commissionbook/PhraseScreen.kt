package com.yifeng.commissionbook

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 常用语模板（2026-10-09 加，#7）。
 *
 * 「要透明背景」「先给草稿再上色」「工期一周左右」这种话，每接一单都要重新打一遍。
 * 存成几条短语，写单的时候在备注那一栏点一下「插入常用语」就完事。
 *
 * ⚠️ 纯本地：存 SharedPreferences，**不联网、不要权限**（跟这个 App 的底色一致）。
 */
@Composable
fun PhraseScreen(state: AppState, onBack: () -> Unit) {

    val cs = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current

    BackHandler { onBack() }

    // 正在编辑哪一条：null = 没开弹窗；"" = 新增；其它 = 改这一条原来的文字
    var editing by remember { mutableStateOf<String?>(null) }
    // 正要删哪一条（删除一律先问一句 —— 逸风定的：删除要确认，可逆的归档/放回不弹）
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    val list = state.phrases

    ScreenSurface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {

            // 顶栏：返回 + 标题 + 右上「+」
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, AppCtx.s(R.string.common_back)) {
                    lightTick(haptic)
                    onBack()
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        AppCtx.s(R.string.phrase_title),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = cs.onSurface,
                    )
                    Text(
                        AppCtx.s(R.string.phrase_subtitle),
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                IconButton(onClick = {
                    lightTick(haptic)
                    editing = ""
                }) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = AppCtx.s(R.string.phrase_add_title),
                        tint = cs.primary,
                    )
                }
            }

            if (list.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    EmptyHint(AppCtx.s(R.string.phrase_empty))
                }
            } else {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        AppCtx.s(R.string.phrase_count, list.size),
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                    )
                    list.forEach { p ->
                        SoftCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // 点整条文字 = 改（比只点小铅笔好按，手指粗也不怕）
                                Text(
                                    p,
                                    fontSize = 14.sp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { editing = p }
                                        .padding(vertical = 12.dp),
                                )
                                IconButton(onClick = { editing = p }) {
                                    Icon(
                                        Icons.Outlined.Edit,
                                        contentDescription = AppCtx.s(R.string.common_edit),
                                        tint = cs.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = {
                                    lightTick(haptic)
                                    pendingDelete = p
                                }) {
                                    Icon(
                                        Icons.Outlined.DeleteOutline,
                                        contentDescription = AppCtx.s(R.string.common_delete_2),
                                        tint = cs.error,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }

            Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars))
        }
    }

    // 新增 / 编辑
    editing?.let { original ->
        PhraseEditDialog(
            initial = original,
            onSave = { text ->
                val t = text.trim()
                if (t.isNotEmpty()) {
                    if (original.isEmpty()) {
                        // 新增：一句话存两遍没意义，重复就直接不理会（savePhrases 里还会再去一次重）
                        state.savePhrases(state.phrases + t)
                    } else {
                        state.savePhrases(state.phrases.map { if (it == original) t else it })
                    }
                }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    // 删除确认（逸风定的：删除要问一句）
    pendingDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(AppCtx.s(R.string.phrase_delete_title)) },
            text = { Text(AppCtx.s(R.string.phrase_delete_body, p)) },
            confirmButton = {
                TextButton(onClick = {
                    state.savePhrases(state.phrases.filterNot { it == p })
                    pendingDelete = null
                }) { Text(AppCtx.s(R.string.common_delete_2), color = cs.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(AppCtx.s(R.string.common_cancel)) }
            },
        )
    }
}

/** 新增 / 编辑一条常用语的小弹窗 */
@Composable
private fun PhraseEditDialog(
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial.isEmpty()) AppCtx.s(R.string.phrase_add_title)
                else AppCtx.s(R.string.phrase_edit_title)
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    text, { text = it },
                    // 提示语给两个真例子 —— 比「请输入内容」有用得多
                    placeholder = { Text(AppCtx.s(R.string.phrase_hint_input)) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text) },
                enabled = text.isNotBlank(),
            ) { Text(AppCtx.s(R.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(AppCtx.s(R.string.common_cancel)) }
        },
    )
}
