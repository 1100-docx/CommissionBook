package com.yifeng.commissionbook

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 设置 → 关于 →「崩溃记录」（2026-10-07 加，**安卓通用版 + 兼容版都有**）。
 *
 * 起因：逸风一句「我看 iOS 版设置里没有发送崩溃日志功能啊」——
 * 他说得对。崩溃记录原来只在**崩过之后**自己弹一张单子，设置里翻不到入口，
 * 结果就是「功能写了、用户看不见，等于没写」。三端一起补上这个入口。
 *
 * 这一屏给的是**手动那条路**：本机存着什么记录全摆出来，想发哪条发哪条，
 * 不用等它崩 —— 对陌生用户尤其重要（比如报鸿蒙闪退那位），
 * 她那边崩了可以直接翻出来发我，不用重装、不用等下一次闪退。
 *
 * ⚠️ 三条规矩跟弹窗版完全一致（见 CrashLog.kt 顶上）：
 *    纯本地、不联网、不要权限；内容里没有账本数据；**用户不点就不发**。
 */
@Composable
fun CrashLogScreen(onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    BackHandler { onBack() }

    // 进这一屏扫一次就够。清空之后自己把列表清掉，不用重扫。
    var records by remember { mutableStateOf(CrashLog.allRecords(context)) }
    var askClear by remember { mutableStateOf(false) }

    // 哪几条的「详细内容」展开了 —— 堆栈长，默认折起来
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    fun sendShare(r: CrashLog.Record) {
        val i = shareText(context, r.text)
        if (i == null) {
            toastNow(context, AppCtx.s(R.string.common_share_failed))
        } else {
            context.startActivity(Intent.createChooser(i, AppCtx.s(R.string.crash_title)))
        }
    }

    fun sendMail(r: CrashLog.Record) {
        val started = runCatching { context.startActivity(CrashLog.mailIntent(r.text)) }.isSuccess
        // 手机没装邮件 App → 兜底走系统分享，别让人卡在这儿
        if (!started) {
            try {
                sendShare(r)
            } catch (e: ActivityNotFoundException) {
                toastNow(context, AppCtx.s(R.string.common_share_failed))
            }
        }
    }

    ScreenSurface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {

            // 顶栏：返回 + 标题（跟隐私政策 / 反馈页同一套，别两个页面两种动静）
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, AppCtx.s(R.string.common_back)) {
                    lightTick(haptic)
                    onBack()
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        AppCtx.s(R.string.crash_log_title),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = cs.onSurface,
                    )
                    Text(
                        AppCtx.s(R.string.crash_log_desc),
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                if (records.isEmpty()) {
                    SoftCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.BugReport,
                                contentDescription = null,
                                tint = cs.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                AppCtx.s(R.string.crash_empty_title),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = cs.onSurface,
                            )
                        }
                        Text(
                            AppCtx.s(R.string.crash_empty_desc),
                            fontSize = 12.5.sp,
                            lineHeight = 19.sp,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                } else {
                    records.forEach { r ->
                        val open = expanded[r.file.name] == true
                        SoftCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    r.time,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = cs.onSurface,
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    r.kind.ifBlank { AppCtx.s(R.string.crash_kind_abnormal) },
                                    fontSize = 11.5.sp,
                                    color = cs.onSurfaceVariant,
                                )
                            }

                            // 「最后停在：年度报告」—— 没有堆栈的时候，这一行才是最有用的一条线索，
                            // 所以给它单独一行、颜色重一点。
                            if (r.lastOn.isNotBlank()) {
                                Text(
                                    AppCtx.s(R.string.crash_field_last_on) + "：" + r.lastOn,
                                    fontSize = 12.5.sp,
                                    color = cs.primary,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }

                            // 详细内容：默认折起来，点一下才铺开
                            TextButton(
                                onClick = {
                                    lightTick(haptic)
                                    expanded[r.file.name] = !open
                                },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                modifier = Modifier.padding(top = 4.dp),
                            ) {
                                Text(
                                    AppCtx.s(
                                        if (open) R.string.crash_collapse else R.string.crash_detail
                                    ),
                                    fontSize = 13.sp,
                                )
                            }
                            if (open) {
                                Text(
                                    r.detail,
                                    fontSize = 10.5.sp,
                                    lineHeight = 15.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = cs.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                        .background(
                                            cs.surfaceVariant.copy(alpha = 0.45f),
                                            RoundedCornerShape(12.dp),
                                        )
                                        .padding(10.dp),
                                )
                            }

                            Row(
                                Modifier.padding(top = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Button(
                                    onClick = {
                                        lightTick(haptic)
                                        sendMail(r)
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        Icons.Outlined.Email,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(AppCtx.s(R.string.crash_mail), fontSize = 13.5.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        lightTick(haptic)
                                        sendShare(r)
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        Icons.Outlined.Share,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(AppCtx.s(R.string.crash_send), fontSize = 13.5.sp)
                                }
                            }
                        }
                    }

                    TextButton(
                        onClick = {
                            lightTick(haptic)
                            askClear = true
                        },
                        modifier = Modifier.padding(vertical = 6.dp),
                    ) {
                        Text(
                            AppCtx.s(R.string.crash_clear_all),
                            fontSize = 13.5.sp,
                            color = Color(0xFFD9534F),
                        )
                    }
                }

                // 这一屏最该说清的话：它不联网、不含账本内容、不点就不发
                SoftCard(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp)) {
                    Text(
                        AppCtx.s(R.string.crash_note),
                        fontSize = 12.sp,
                        lineHeight = 18.5.sp,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (askClear) {
        AlertDialog(
            onDismissRequest = { askClear = false },
            title = { Text(AppCtx.s(R.string.crash_clear_ask)) },
            text = { Text(AppCtx.s(R.string.crash_clear_body), fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    lightTick(haptic)
                    CrashLog.clearAll(context)
                    records = emptyList()
                    askClear = false
                }) { Text(AppCtx.s(R.string.crash_clear_ok), color = Color(0xFFD9534F)) }
            },
            dismissButton = {
                TextButton(onClick = { askClear = false }) {
                    Text(AppCtx.s(R.string.common_cancel))
                }
            },
        )
    }
}
