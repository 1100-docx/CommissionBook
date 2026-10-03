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
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.SystemUpdate
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    modifier: Modifier,
    state: AppState,
    prefs: Prefs,
    onExport: (String) -> Unit,
    onImport: () -> Unit,
    onShare: (String, String) -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenFeedback: () -> Unit,
    onOpenCalculator: () -> Unit,
    onOpenHelp: () -> Unit,
) {
    val context = LocalContext.current

    var lockOn by remember { mutableStateOf(prefs.lockEnabled) }
    var reminderOn by remember { mutableStateOf(prefs.reminderEnabled) }
    // 语言选择那个 Sheet（2026-10-03 加）
    var showLangSheet by remember { mutableStateOf(false) }
    // 选完语言后的「要现在重启吗」确认框（2026-10-03 加）
    //
    // ⚠️ 为什么不直接重启：直接闪一下重启，界面「刷」地没了，太难看。
    //    所以选完先弹这个框问一句 —— 选「稍后」也不亏：
    //    locale 已经存进系统了（AppCompatDelegate.setApplicationLocales），
    //    下次自然打开 App 就是新语言，数据一点不受影响。
    var askRestart by remember { mutableStateOf(false) }
    // 设置页这一行要显示「当前语言」，选完当场刷新（不用等重启）
    var langNow by remember { mutableStateOf(prefs.appLanguage) }

    // 检查更新（2026-10-03 加，**只有安卓版有**）
    var autoUpdate by remember { mutableStateOf(prefs.updateAutoCheck) }
    // null = 不显示那个框
    var updateStatus by remember { mutableStateOf<UpdateStatus?>(null) }
    val updateScope = rememberCoroutineScope()

    // 版本号从安装包里读，不写死 —— 免得包升了、设置页还写着旧号
    // （iOS 版那边栽过一次：两个人对着两份包争论到底装没装上）
    val versionName = remember {
        runCatching {
            val pm = context.packageManager
            val info = if (android.os.Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(context.packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            info.versionName ?: "?"
        }.getOrDefault("?")
    }

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
                .makeText(context, AppCtx.s(R.string.notify_no_permission), android.widget.Toast.LENGTH_LONG)
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
                title = AppCtx.s(R.string.settings_title),
                collapse = collapse,
                caption = AppCtx.s(R.string.common_data_local_only),
            )

            Column(
                Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {

                // ① 使用模式（2026-09-30 加）
                // 同一套数据模型、两套叫法。切一下：界面用词、列表、底栏、统计全都跟着换。
                // 两边账本各记各的 —— 当买家约的稿、当画师接的单，是两摞。
                Column {
                    SectionLabel(AppCtx.s(R.string.settings_usage_mode))
                    InsetGroup {
                        GroupRow(
                            title = if (state.appMode == AppMode.BUYER) AppCtx.s(R.string.common_buyer) else AppCtx.s(R.string.common_artist),
                            subtitle = if (state.appMode == AppMode.BUYER)
                                AppCtx.s(R.string.settings_mode_buyer_desc)
                            else
                                AppCtx.s(R.string.settings_mode_client_desc),
                            icon = Icons.Filled.SwapHoriz,
                            divider = false,
                            trailing = {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    AppMode.entries.forEach { m ->
                                        SlimChip(m.label, state.appMode == m) { state.switchMode(m) }
                                    }
                                }
                            },
                        )
                    }
                    Text(
                        AppCtx.s(R.string.settings_mode_separate),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp),
                    )
                }

                // ①.5 外观（2026-10-03 加）
                //
                // 深浅色三选一。**为什么不跳系统设置**：深浅色是 App 自己能定的事，
                // 改完当场生效；语言才必须跳出去（系统规定，见下面「语言」一节）。
                Column {
                    SectionLabel(AppCtx.s(R.string.settings_appearance))
                    InsetGroup {
                        GroupRow(
                            title = AppCtx.s(R.string.settings_theme),
                            subtitle = AppCtx.s(R.string.settings_theme_desc),
                            icon = Icons.Filled.DarkMode,
                            // ⚠️ 2026-10-03：**必须另起一行**。
                            //    英文那三颗胶囊是「Follow System / Light / Dark」，
                            //    塞在右边会把标题和副标题挤到几乎没有宽度 ——
                            //    逸风截图里就是「一个字母一行」，整页被撑成一列竖排字。
                            //    中文时它俩宽度差不多、侥幸没露；一换英文就现原形。
                            stackTrailing = true,
                            divider = false,
                            trailing = {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    AppAppearance.entries.forEach { a ->
                                        SlimChip(a.label, state.appearance == a) {
                                            state.switchAppearance(a)
                                        }
                                    }
                                }
                            },
                        )
                    }
                }

                // ①.6 语言（2026-10-03 加）
                //
                // ⚠️ 跟 iOS 版走的是**两条路**，别以为是没对齐：
                //    iOS 规定 per-App 语言只能由系统改，所以 iOS 那一行是「跳系统设置」；
                //    安卓 App 自己就能改（AppCompatDelegate），所以是弹 Sheet 选。
                Column {
                    SectionLabel(AppCtx.s(R.string.settings_language))
                    InsetGroup {
                        GroupRow(
                            title = AppCtx.s(R.string.settings_language),
                            // 显示**当前这门语言**，比干列一串支持的语言有用
                            subtitle = if (langNow.isEmpty())
                                AppCtx.s(R.string.settings_language_system)
                            else
                                LANGUAGES.firstOrNull { it.first == langNow }?.second
                                    ?: AppCtx.s(R.string.settings_language_system),
                            icon = Icons.Filled.Language,
                            divider = false,
                            onClick = { showLangSheet = true },
                        )
                    }
                    Text(
                        AppCtx.s(R.string.settings_language_note),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp),
                    )
                }

                if (showLangSheet) {
                    LanguagePickerSheet(
                        prefs = prefs,
                        onDismiss = { showLangSheet = false },
                        onPicked = {
                            langNow = prefs.appLanguage     // 这一行当场显示新语言
                            showLangSheet = false
                            askRestart = true               // 不直接重启，先问一句
                        },
                    )
                }

                // 「要现在重启吗」—— 选语言之后才出现（2026-10-03 加）
                //
                // ⚠️ 这个 AlertDialog 必须挂在 Sheet **外面**：
                //    选完那一瞬间 Sheet 就被关掉了（showLangSheet = false），
                //    挂在里面的话它会跟着 Sheet 一起消失，用户根本看不见。
                if (askRestart) {
                    AlertDialog(
                        onDismissRequest = { askRestart = false },
                        title = { Text(AppCtx.s(R.string.settings_language_restart_title)) },
                        text = { Text(AppCtx.s(R.string.settings_language_restart_msg)) },
                        confirmButton = {
                            TextButton(onClick = {
                                askRestart = false
                                applyLocaleNow(context)
                            }) { Text(AppCtx.s(R.string.settings_language_restart_now)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { askRestart = false }) {
                                Text(AppCtx.s(R.string.settings_language_restart_later))
                            }
                        },
                    )
                }

                // ② 保护
                Column {
                    SectionLabel(AppCtx.s(R.string.settings_security))
                    InsetGroup {
                        GroupRow(
                            title = AppCtx.s(R.string.settings_protection),
                            subtitle = AppCtx.s(R.string.settings_lock_desc),
                            icon = Icons.Filled.Lock,
                            divider = false,
                            trailing = {
                                Switch(checked = lockOn, onCheckedChange = { v ->
                                    if (v && !BioLock.available(context)) {
                                        android.widget.Toast
                                            .makeText(context, AppCtx.s(R.string.settings_no_passcode_2), android.widget.Toast.LENGTH_LONG)
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
                    SectionLabel(AppCtx.s(R.string.settings_reminders))
                    InsetGroup {
                        GroupRow(
                            title = AppCtx.s(R.string.settings_remind_before_due),
                            subtitle = AppCtx.s(R.string.settings_deadline_reminder_desc),
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
                        AppCtx.s(R.string.settings_notification_hint),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp),
                    )
                }

                // ③ 备份与恢复
                Column {
                    SectionLabel(AppCtx.s(R.string.settings_backup_restore_2))
                    InsetGroup {
                        GroupRow(
                            title = AppCtx.s(R.string.settings_export_backup),
                            subtitle = AppCtx.s(R.string.settings_export_backup_desc),
                            icon = Icons.Filled.FileDownload,
                            onClick = { onExport(state.toJsonString()) },
                        )
                        GroupRow(
                            title = AppCtx.s(R.string.settings_share_backup),
                            subtitle = AppCtx.s(R.string.settings_share_backup_desc),
                            icon = Icons.Filled.Share,
                            onClick = { onShare(state.toJsonString(), Backup.fileName(manual = true)) },
                        )
                        GroupRow(
                            title = AppCtx.s(R.string.settings_restore_from_file),
                            subtitle = AppCtx.s(R.string.settings_restore_ios_note),
                            icon = Icons.Filled.Restore,
                            divider = false,
                            onClick = onImport,
                        )
                    }
                }

                // ③.5 小工具（2026-09-30 第三批）
                // ⚠️ 只在**画师模式**出现：报价计算器是画师向的（算价、报给客户）。
                //    2026-10-01 逸风：「买家模式不应出现这个」。
                if (state.appMode == AppMode.ARTIST) {
                    Column {
                        SectionLabel(AppCtx.s(R.string.common_tools))
                        InsetGroup {
                            GroupRow(
                                title = AppCtx.s(R.string.artist_quote_calculator),
                                subtitle = AppCtx.s(R.string.common_tools_desc),
                                icon = Icons.Filled.Calculate,
                                divider = false,
                                onClick = onOpenCalculator,
                            )
                        }
                    }
                }

                // ③.6 帮助（2026-10-01 加）
                // 逸风：「建议出帮助选项，因为后续功能越多，用户越难以自己摸索」。
                // 放这儿（小工具之后、关于之前）—— 顺序上刚好是「用的东西 → 不会就查 → 别的」。
                Column {
                    SectionLabel(AppCtx.s(R.string.help_title))
                    InsetGroup {
                        GroupRow(
                            title = AppCtx.s(R.string.help_how_to_use_2),
                            subtitle = AppCtx.s(R.string.help_how_to_use_desc_2),
                            icon = Icons.Filled.HelpOutline,
                            divider = false,
                            onClick = onOpenHelp,
                        )
                    }
                }

                // ③.7 更新（2026-10-03 加，**只有安卓版有**）
                //
                // 逸风要的「自动更新功能仅限于 Android」——
                // iOS 那边沙盒里本来就不让自装、只能走 App Store，所以那边没有这一块，
                // 别以为是漏做了。
                //
                // ⚠️ 这一块给 App 带来了**第一个网络权限**，隐私政策里那几条
                //    「完全不联网」的文案全跟着改了（见 PrivacyScreen.kt 顶部注释）。
                Column {
                    SectionLabel(AppCtx.s(R.string.settings_update_section))
                    InsetGroup {
                        GroupRow(
                            title = AppCtx.s(R.string.settings_update_check),
                            subtitle = AppCtx.s(R.string.settings_update_check_desc, versionName),
                            icon = Icons.Filled.SystemUpdate,
                            onClick = {
                                updateScope.launch { doCheck(context, prefs) { updateStatus = it } }
                            },
                        )
                        GroupRow(
                            title = AppCtx.s(R.string.settings_update_auto),
                            subtitle = AppCtx.s(R.string.settings_update_auto_desc),
                            icon = Icons.Filled.Autorenew,
                            divider = false,
                            trailing = {
                                Switch(checked = autoUpdate, onCheckedChange = { v ->
                                    autoUpdate = v
                                    prefs.updateAutoCheck = v
                                })
                            },
                        )
                    }
                }

                // ④ 关于
                Column {
                    SectionLabel(AppCtx.s(R.string.settings_about))
                    InsetGroup {
                        GroupRow(
                            title = AppCtx.s(R.string.settings_version),
                            icon = Icons.Filled.Info,
                            trailing = { InfoValue(AppCtx.s(R.string.settings_version_android, versionName)) },
                        )
                        // 首次启动已经强制读过一遍，这里留个入口随时能翻回来
                        GroupRow(
                            title = AppCtx.s(R.string.privacy_policy_title),
                            subtitle = AppCtx.s(R.string.settings_offline_note),
                            icon = Icons.Filled.PrivacyTip,
                            onClick = onOpenPrivacy,
                        )
                        // 反馈渠道（2026-09-29 加）：App 不联网，所以是「帮你写好，你自己发」
                        GroupRow(
                            title = AppCtx.s(R.string.help_feedback_title),
                            subtitle = AppCtx.s(R.string.settings_feedback_desc),
                            icon = Icons.Filled.Email,
                            onClick = onOpenFeedback,
                        )
                        GroupRow(title = AppCtx.s(R.string.common_count), subtitle = AppCtx.s(R.string.common_current_mode_all), trailing = { InfoValue(AppCtx.s(R.string.common_count_of_total, state.modeItems.size, state.items.size)) })
                        GroupRow(title = AppCtx.s(R.string.common_archived), trailing = { InfoValue("${state.modeItems.count { it.archived }} 条") })
                        GroupRow(
                            title = AppCtx.s(R.string.settings_data_storage),
                            divider = false,
                            trailing = { InfoValue(AppCtx.s(R.string.settings_local_storage)) },
                        )
                    }
                }

                // 检查更新那个框（2026-10-03 加）——
                // 挂在最下面而不是上面那块里：Dialog 是另一个窗口，放哪儿都行，
                // 放这儿是为了别把上面那串设置项的缩进搞乱。
                UpdateDialog(updateStatus, prefs) { updateStatus = it }

                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

// MARK: - 选语言（2026-10-03 加）
//
// ⚠️ 走的路子跟 iOS 版**不一样**，别以为是没对齐：
//    iOS 规定 per-App 语言只能由系统改，所以那边是「跳系统设置」；
//    安卓 App 自己就能改（AppCompatDelegate.setApplicationLocales），所以是弹 Sheet。
//
// ⚠️ 还有个容易误会「按钮点了没用」的地方：
//    光把选择存下来，界面**不会**自己变 —— 资源是启动时按语言加载好的。
//    真正换语言靠三件事一起  ——
//      ① 存进 [Prefs.appLanguage]（自己存，不用 AppCompatDelegate，原因见那边的注释）
//      ② MainActivity.attachBaseContext 把语言塞进 Configuration
//      ③ MainActivity.onCreate 里 AppCtx.init(...) 连 applicationContext 一起包上
//          （少这一步：App 自己的字一个都不会变，只有系统弹的字变）
//    ⚠️ 但**不在这里直接重启**（2026-10-03 改）：直接闪重启太难看，
//    这里只负责存选择 + 回调出去，由设置页弹一个确认框问「要现在重启吗」。

/** 语言选项：tag 为空 = 跟随系统 */
private val LANGUAGES: List<Pair<String, String>> = listOf(
    "" to "",                          // 显示名运行时再取（要跟着语言走）
    "zh-CN" to "简体中文",
    "zh-HK" to "繁體中文（香港）",
    "zh-TW" to "繁體中文（台灣）",
    "en-GB" to "English (UK)",
    "en-US" to "English (US)",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePickerSheet(
    prefs: Prefs,
    onDismiss: () -> Unit,
    onPicked: () -> Unit,
) {
    val current = remember { prefs.appLanguage }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 28.dp)) {
            Text(
                AppCtx.s(R.string.settings_language),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 20.dp, bottom = 6.dp),
            )
            LANGUAGES.forEach { (tag, name) ->
                val label = if (tag.isEmpty()) AppCtx.s(R.string.settings_language_system) else name
                val picked = current == tag
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            prefs.appLanguage = tag
                            // 点的是当前这门语言 → 无事发生，别拿重启框烦他
                            if (picked) onDismiss() else onPicked()
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    if (picked) {
                        Icon(Icons.Filled.Check, contentDescription = null,
                             tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/**
 * 让新语言真正生效。**用户点了「现在重启」才走这里。**
 *
 * 优先 `recreate()`：只把当前 Activity 重建一遍 ——
 *   `attachBaseContext` 会重新塞一次 locale，界面原地切过来，
 *   不会「唰」地跳出 App 再跳回来（逸风 2026-10-03 说那样太难看）。
 * 万一 context 拿不到 Activity，退回整进程重启兜底。
 *
 * ⚠️ 为什么不能只调 setApplicationLocales 就完事：
 *    AppCompat 那套「自动重建界面」只对 AppCompatActivity 生效，
 *    而 MainActivity 继承的是 FragmentActivity（要用它自带的下滑返回），
 *    所以得自己动手 —— 见 MainActivity.attachBaseContext 的注释。
 */
private fun applyLocaleNow(context: Context) {
    val activity = context as? android.app.Activity
    if (activity != null) {
        activity.recreate()
    } else {
        restartApp(context)
    }
}

/**
 * 兜底：整进程重启。
 *
 * 为什么需要重启：安卓的资源是**启动时**按当前 locale 加载好的，
 * 不重启的话已经画出来的界面不会自己换（除非整个工程改用 `stringResource()` 走 Compose 重组，
 * 但那样「非界面代码」那 200 多处又够不着了 —— 见 AppCtx 的注释）。
 */
private fun restartApp(context: Context) {
    val i = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(i)
    Runtime.getRuntime().exit(0)
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
