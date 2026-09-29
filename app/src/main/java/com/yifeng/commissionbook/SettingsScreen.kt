package com.yifeng.commissionbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity

@Composable
fun SettingsScreen(
    modifier: Modifier,
    state: AppState,
    prefs: Prefs,
    onExport: (String) -> Unit,
    onImport: () -> Unit,
    onShare: (String, String) -> Unit,
) {
    val context = LocalContext.current

    var lockOn by remember { mutableStateOf(prefs.lockEnabled) }
    var reminderOn by remember { mutableStateOf(prefs.reminderEnabled) }

    // 滚动位置 → 大标题跟着缩
    val scroll = rememberScrollState()
    val collapse = (scroll.value / 150f).coerceIn(0f, 1f)

    // 安卓 13（API 33）起，弹通知要用户点头。
    // 系统弹的那个「允许通知吗」框是原生的，我们不自己画。
    val notifPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            android.widget.Toast
                .makeText(context, "没给通知权限，到点就不会提醒你 —— 想开去手机设置里打开", android.widget.Toast.LENGTH_LONG)
                .show()
        }
    }
    val askNotif = {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    ScreenSurface(modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll),
        ) {
            CollapsibleLargeTitle(
                title = "设置",
                collapse = collapse,
                caption = "数据都在这台手机里，不上传",
            )

            Column(
                Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {

                // ① 保护
                Column {
                    SectionLabel("安全")
                    InsetGroup {
                        GroupRow(
                            title = "保护",
                            subtitle = "打开后进 App 要先按指纹 / 面容 / 密码，切后台回来也会重新锁。",
                            icon = Icons.Filled.Lock,
                            divider = false,
                            trailing = {
                                Switch(checked = lockOn, onCheckedChange = { v ->
                                    if (v && !BioLock.available(context)) {
                                        android.widget.Toast
                                            .makeText(context, "这台手机还没设锁屏密码，先去设置里设一个", android.widget.Toast.LENGTH_LONG)
                                            .show()
                                    } else {
                                        lockOn = v
                                        prefs.lockEnabled = v
                                        (context as? FragmentActivity)?.applyPrivacyShield(v)
                                    }
                                })
                            },
                        )
                    }
                }

                // ② 提醒
                Column {
                    SectionLabel("提醒")
                    InsetGroup {
                        GroupRow(
                            title = "截止日前提醒我",
                            subtitle = "有截止日的单子，到期前 3 天和 1 天各提醒一次（晚上 8 点）。已交付的、归档的不提醒。",
                            icon = Icons.Filled.NotificationsActive,
                            divider = false,
                            trailing = {
                                Switch(checked = reminderOn, onCheckedChange = { v ->
                                    reminderOn = v
                                    prefs.reminderEnabled = v
                                    Reminders.reschedule(context, state.items, v)
                                    if (v) askNotif()
                                })
                            },
                        )
                    }
                    Text(
                        "第一次打开会问你要不要允许通知。万一没收到，去手机的「设置 → 应用 → 约稿账本 → 通知」里看看是不是关掉了。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp),
                    )
                }

                // ③ 备份与恢复
                Column {
                    SectionLabel("备份与恢复")
                    InsetGroup {
                        GroupRow(
                            title = "导出备份",
                            subtitle = "存成 JSON 文件，能保存到手机 / 云盘",
                            icon = Icons.Filled.FileDownload,
                            onClick = { onExport(state.toJsonString()) },
                        )
                        GroupRow(
                            title = "分享备份",
                            subtitle = "直接发给微信 / 发给自己",
                            icon = Icons.Filled.Share,
                            onClick = { onShare(state.toJsonString(), Backup.fileName(manual = true)) },
                        )
                        GroupRow(
                            title = "从文件恢复",
                            subtitle = "跟 iOS 版的备份通用，两边都认",
                            icon = Icons.Filled.Restore,
                            divider = false,
                            onClick = onImport,
                        )
                    }
                }

                // ④ 关于
                Column {
                    SectionLabel("关于")
                    InsetGroup {
                        GroupRow(
                            title = "版本",
                            icon = Icons.Filled.Info,
                            trailing = { InfoValue("2.1（安卓版）") },
                        )
                        GroupRow(title = "约稿条数", trailing = { InfoValue("${state.items.size} 条") })
                        GroupRow(title = "已归档", trailing = { InfoValue("${state.items.count { it.archived }} 条") })
                        GroupRow(
                            title = "数据存放",
                            divider = false,
                            trailing = { InfoValue("手机本地") },
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun InfoValue(v: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            v,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
