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
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.SystemUpdate
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerLayoutType
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    modifier: Modifier,
    state: AppState,
    prefs: Prefs,
    onExport: (String) -> Unit,
    onImport: () -> Unit,
    // 二维码传输（2026-10-07 加，见 QrTransfer.kt）：
    //   onQrScan  = 让 Activity 去开相机扫码（相机权限、取景界面都在那边）
    //   onQrImport = 扫到的内容已经解成备份文本了，交给 Activity 导进去
    onQrScan: () -> Unit,
    onQrImport: (String) -> Unit,
    onShare: (String, String) -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenFeedback: () -> Unit,
    onOpenCalculator: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenSponsor: () -> Unit,
) {
    val context = LocalContext.current

    var lockOn by remember { mutableStateOf(prefs.lockEnabled) }
    var reminderOn by remember { mutableStateOf(prefs.reminderEnabled) }
    // 提醒时间（2026-10-05 加，3.5.36）：默认 19:00，用户点「提醒时间」那行自己改
    var reminderHour by remember { mutableIntStateOf(prefs.reminderHour) }
    var reminderMinute by remember { mutableIntStateOf(prefs.reminderMinute) }
    var showTimePicker by remember { mutableStateOf(false) }
    // 二维码传输（2026-10-07 加）：先弹「新手机还是旧手机」，
    // 选了「旧手机」才把码算出来放进 sheetText（见 QrShowSheet 里那段 ⚠️）
    var qrRole by remember { mutableStateOf(false) }
    var qrSheetText by remember { mutableStateOf<String?>(null) }
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
    // 「分享备份」前的隐私提醒（2026-10-05 加，逸风要的）
    var askShare by remember { mutableStateOf(false) }
    // null = 不显示那个框
    var updateStatus by remember { mutableStateOf<UpdateStatus?>(null) }
    val updateScope = rememberCoroutineScope()

    // 彩蛋（2026-10-06 加）：版本号连点了多少下、要不要弹那张小单子。
    // 为什么是七下：安卓查「开发者选项」就是连点版本号七下 —— 借这个老规矩当暗门，
    // 界面上不加任何提示，愿意乱点的人自己会发现。
    var versionTaps by remember { mutableIntStateOf(0) }
    var showEgg by remember { mutableStateOf(false) }
    val eggHaptic = LocalHapticFeedback.current

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
                            icon = Icons.Outlined.SwapHoriz,
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
                            icon = Icons.Outlined.DarkMode,
                            // ⚠️ 2026-10-03 改了三版，别再来回折腾：
                            //   ① 「长说明塞中间那列 + 英文三颗长胶囊」→ 标题被压成「一个字母一行」；
                            //   ② stackTrailing（胶囊另起一行）→ 逸风：「三颗另起一行看着不和谐」；
                            //   ③ stackSubtitle（说明另起一行）→ 逸风：「还是想要（模式那行那样的）排列」。
                            //   终版 = **跟上面「模式」那行完全同一个排列**：
                            //   图标 + 标题说明 + 胶囊，全在同一行，胶囊靠右、垂直居中。
                            //   能装下的前提是两件事一起做：
                            //     a. 胶囊文案短（System / Light / Dark，别再写 Follow System）；
                            //     b. 中间那句说明写短（settings_theme_desc 已压到 ~20 字，
                            //        窄列里自然折三行，跟「模式」那行一样高）。
                            //   哪天真装不下了，问题在文案长度，不是这个排列。
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

                // ①.7 触感（2026-10-07 加）
                //
                // 逸风原话：「我发现 Android 版软件没有震动欸，加上震动，
                // 同样是轻中重三档调节」。
                //
                // ⚠️ 「没震动」的真凶不是没写，是原来那条路（Compose 的
                //    HapticFeedbackType）在 ColorOS 上等于没有 —— 详见 Haptics.kt。
                //    这里只是把档位开关摆出来，实现在那边。
                //
                // 排列跟上面「深浅色」那行完全一致：图标 + 标题说明在左，
                // ⚠️ 2026-10-07 改成**四颗**胶囊（关 / 轻 / 中 / 重）——
                //    四个字都是单字，一行放得下；`HapticLevel.entries` 直接循环，
                //    以后再加档不用动这里（顺序就是 enum 的顺序）。
                Column {
                    SectionLabel(AppCtx.s(R.string.settings_haptic))
                    InsetGroup {
                        GroupRow(
                            title = AppCtx.s(R.string.settings_haptic_title),
                            subtitle = AppCtx.s(R.string.settings_haptic_desc),
                            icon = Icons.Outlined.Vibration,
                            divider = false,
                            trailing = {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    HapticLevel.entries.forEach { l ->
                                        SlimChip(l.label, state.hapticLevel == l) {
                                            // 换档那一瞬间自己就震一下（见 switchHaptic），
                                            // 所以「选中」这件事不用再额外震。
                                            state.switchHaptic(l)
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
                            icon = Icons.Outlined.Language,
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
                            icon = Icons.Outlined.Lock,
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
                    // 系统没给「精确闹钟」资格时下面会多一行（见 3.5.28 的注释）——
                    // 这一行决定的是「谁是这组里的最后一行」，好让分隔线画对
                    val showExactRow = !Reminders.exactAllowed(context)
                    InsetGroup {
                        GroupRow(
                            title = AppCtx.s(R.string.settings_remind_before_due),
                            subtitle = AppCtx.s(R.string.settings_deadline_reminder_desc),
                            icon = Icons.Outlined.NotificationsActive,
                            // 开着提醒时下面还有「提醒时间」那行 → 这行要画分隔线
                            divider = reminderOn,
                            trailing = {
                                Switch(checked = reminderOn, onCheckedChange = { v ->
                                    reminderOn = v
                                    prefs.reminderEnabled = v
                                    Reminders.reschedule(context, state.items, v, reminderHour, reminderMinute)
                                    if (v) {
                                        // ① 先问通知权限（系统弹窗）
                                        askNotif()
                                        // ② 2026-10-05 晚加（3.5.35）：隔 0.9 秒再请一次
                                        //    「允许后台运行」—— 两个系统框前后脚出现，叠一起会看不清。
                                        //    这一下是**能一键授权**的那个（加进电池优化白名单），
                                        //    国产 ROM 的省电策略就不再压着闹钟，提醒才能自己准点响。
                                        //    「允许自启动」没做：安卓没有标准 API，各家的页面都不一样。
                                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                            Reminders.requestRunInBackground(context)
                                        }, 900)
                                    }
                                })
                            },
                        )

                        // 提醒时间（2026-10-05 加，3.5.36）
                        //
                        // 逸风原话：「显示通知的时间让用户自己决定比较好点，加一个时间选择器」。
                        // 之前是写死的 19:00；现在这行点一下弹 M3 的表盘选择器，改完立刻重排闹钟。
                        // ⚠️ 只在开关打开时出现 —— 提醒默认关着，关着的时候这行没有意义，
                        //    设置页也能保持着「一眼看清」的样子。
                        if (reminderOn) {
                            GroupRow(
                                title = AppCtx.s(R.string.settings_reminder_time),
                                subtitle = AppCtx.s(R.string.settings_reminder_time_desc),
                                icon = Icons.Outlined.Schedule,
                                // 长说明另起一行通铺（规矩见 GroupRow 的 stackSubtitle 注释）
                                stackSubtitle = true,
                                divider = showExactRow,
                                trailing = {
                                    Text(
                                        clockText(
                                            reminderHour,
                                            reminderMinute,
                                            android.text.format.DateFormat.is24HourFormat(context),
                                        ),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                onClick = { showTimePicker = true },
                            )
                        }

                        // ⚠️ 2026-10-05 晚加（3.5.28）：系统没给「精确闹钟」资格时，多一行入口。
                        //    起因：逸风那台 OPPO（Android 16）只声明 USE_EXACT_ALARM 不管用，
                        //    闹钟 window=+1h —— 19:00 的提醒最晚能拖到 20:00。
                        //    这行**只在真没资格时出现**，开了以后自己就没了（不用手动摘）。
                        if (showExactRow) {
                            GroupRow(
                                title = AppCtx.s(R.string.settings_exact_alarm_title),
                                subtitle = AppCtx.s(R.string.settings_exact_alarm_desc),
                                icon = Icons.Outlined.Schedule,
                                divider = false,
                                onClick = {
                                    // 跳到系统的「闹钟和提醒」授权页（安卓 12+ 才有这个页面）
                                    runCatching {
                                        context.startActivity(
                                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                                .setData(Uri.fromParts("package", context.packageName, null))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                },
                            )
                        }
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
                            icon = Icons.Outlined.FileDownload,
                            onClick = { onExport(state.toJsonString()) },
                        )
                        GroupRow(
                            title = AppCtx.s(R.string.settings_share_backup),
                            subtitle = AppCtx.s(R.string.settings_share_backup_desc),
                            icon = Icons.Outlined.Share,
                            // 2026-10-05 改：不再直接弹分享面板，先过一道隐私提醒
                            onClick = { askShare = true },
                        )
                        GroupRow(
                            title = AppCtx.s(R.string.settings_restore_from_file),
                            subtitle = AppCtx.s(R.string.settings_restore_ios_note),
                            icon = Icons.Outlined.Restore,
                            onClick = onImport,
                        )
                        // 二维码传输（2026-10-07 加）—— 逸风要的「旧手机出码、新手机扫码」。
                        // 摆在这一节的最后一行：前面三条是「跟文件打交道」，这条是「跟手机打交道」。
                        GroupRow(
                            title = AppCtx.s(R.string.qr_entry),
                            subtitle = AppCtx.s(R.string.qr_entry_desc),
                            icon = Icons.Outlined.QrCode,
                            divider = false,
                            onClick = { qrRole = true },
                        )
                    }
                }

                // 二维码传输用的那两个窗（2026-10-07 加）。
                // ⚠️ 它们俩都是**窗口级**的（Dialog / BottomSheet 自己浮在最上面），
                //    所以摆在哪里都行，别费劲去调位置。
                if (qrRole) {
                    QrRoleDialog(
                        onNew = {
                            qrRole = false
                            onQrScan()
                        },
                        onOld = {
                            qrRole = false
                            // ⚠️ 现算：这里算出来的就是「码里要装的东西」，
                            //    带着参考图，可能好几 MB —— 只在用户真点「旧手机」时才做。
                            qrSheetText = state.toJsonString()
                        },
                        onDismiss = { qrRole = false },
                    )
                }
                qrSheetText?.let { text ->
                    QrShowSheet(
                        backupText = text,
                        onClose = { qrSheetText = null },
                    )
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
                                icon = Icons.Outlined.Calculate,
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
                            icon = Icons.Outlined.HelpOutline,
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
                            icon = Icons.Outlined.SystemUpdate,
                            onClick = {
                                updateScope.launch { doCheck(context, prefs) { updateStatus = it } }
                            },
                        )
                        GroupRow(
                            title = AppCtx.s(R.string.settings_update_auto),
                            subtitle = AppCtx.s(R.string.settings_update_auto_desc),
                            icon = Icons.Outlined.Autorenew,
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
                            icon = Icons.Outlined.Info,
                            trailing = { InfoValue(AppCtx.s(R.string.settings_version_android, versionName)) },
                            // 彩蛋入口：连点七下（2026-10-06 加）。
                            // 每下都轻轻震一次，不然「点着没反应」很容易被当成卡了。
                            onClick = {
                                lightTick(eggHaptic)
                                versionTaps += 1
                                if (versionTaps >= 7) {
                                    versionTaps = 0
                                    showEgg = true
                                }
                            },
                        )
                        // 首次启动已经强制读过一遍，这里留个入口随时能翻回来
                        GroupRow(
                            title = AppCtx.s(R.string.privacy_policy_title),
                            subtitle = AppCtx.s(R.string.settings_offline_note),
                            icon = Icons.Outlined.PrivacyTip,
                            onClick = onOpenPrivacy,
                        )
                        // 反馈渠道（2026-09-29 加）：App 不联网，所以是「帮你写好，你自己发」
                        GroupRow(
                            title = AppCtx.s(R.string.help_feedback_title),
                            subtitle = AppCtx.s(R.string.settings_feedback_desc),
                            icon = Icons.Outlined.Email,
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

                // ⑤ 支持作者（2026-10-06 加，**只有安卓版有**）
                //
                // 逸风要的「加赞助功能」就落成这一行 —— 刻意做成**安静的入口**：
                // 放在设置页最底下、只有一枚小爱心、不做计数、主界面一个字都不出现。
                // 理由：他现在缺的不是钱，是「有人在用」的确认；而收不到的时候，
                // 任何计数都只是在提醒他「没人给」。
                //
                // ⚠️ iOS 版没有这一块（那边必须走 App Store 内购），别以为是漏做了。
                Column {
                    SectionLabel(AppCtx.s(R.string.settings_support_section))
                    InsetGroup {
                        GroupRow(
                            title = AppCtx.s(R.string.settings_support_title),
                            subtitle = AppCtx.s(R.string.settings_support_desc),
                            icon = Icons.Outlined.FavoriteBorder,
                            divider = false,
                            onClick = onOpenSponsor,
                        )
                    }
                }

                // 检查更新那个框（2026-10-03 加）——
                // 「分享备份」的隐私提醒（2026-10-05 加）
                //
                // ⚠️ 备份 JSON 是**明文**：画师名、金额、备注、联系方式、参考图（base64）全在里面，
                //    发出去就等于全交出去。所以分享前先拦一句，别指望人记得。
                //    逸风原话：「分享 json 文件的时候建议弹窗提醒请妥善保管文件，
                //    因为里面保存了隐私信息」。
                if (askShare) {
                    AlertDialog(
                        onDismissRequest = { askShare = false },
                        title = { Text(AppCtx.s(R.string.share_warn_title)) },
                        text = {
                            Text(
                                AppCtx.s(R.string.share_warn_body),
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                askShare = false
                                onShare(state.toJsonString(), Backup.fileName(manual = true))
                            }) { Text(AppCtx.s(R.string.share_warn_continue)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { askShare = false }) {
                                Text(AppCtx.s(R.string.common_cancel))
                            }
                        },
                    )
                }

                // 彩蛋（2026-10-06 加）：连点七下版本号弹的小单子。
                // 跟上面那些框一个规矩：挂在最下面，别挤乱设置项的缩进。
                if (showEgg) {
                    EasterEggSheet(onDismiss = { showEgg = false })
                }

                // 提醒时间选择器（2026-10-05 加，3.5.36）
                // 跟上面那些框一个规矩：挂在最下面，不挤乱设置项的缩进。
                if (showTimePicker) {
                    ReminderTimeDialog(
                        hour = reminderHour,
                        minute = reminderMinute,
                        is24 = android.text.format.DateFormat.is24HourFormat(context),
                        onDismiss = { showTimePicker = false },
                        onConfirm = { h, m ->
                            reminderHour = h
                            reminderMinute = m
                            prefs.reminderHour = h
                            prefs.reminderMinute = m
                            // 改完**立刻**按新时点重排一遍闹钟 ——
                            // 不然得等下次改数据/重开 App 才生效（那个「改时间不重排」的坑见知识库）
                            Reminders.reschedule(context, state.items, reminderOn, h, m)
                            showTimePicker = false
                        },
                    )
                }

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
                        Icon(Icons.Outlined.Check, contentDescription = null,
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

/**
 * 提醒时间选择器（2026-10-05 加，3.5.36）。
 *
 * ⚠️ 为什么自己搭 Dialog 而不是用 AlertDialog：
 *    M3 的表盘 [TimePicker] 自身宽 328dp，而 AlertDialog 左右各留 24dp 内边距，
 *    在 360dp 宽的手机上表盘会被切掉边。自己搭的只留 12dp，什么机器都放得下。
 * ⚠️ 表盘默认是「横排」（表盘 + 右侧输入框并排，给平板/横屏用的），
 *    手机上必须显式给 [TimePickerLayoutType.Vertical]（表盘在上、两个数字框在下）。
 * ⚠️ 点「取消」或点外面 = 没改任何东西（只有 [onConfirm] 才写盘）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(
    hour: Int,
    minute: Int,
    is24: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    val st = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = is24)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                Modifier.padding(horizontal = 12.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    AppCtx.s(R.string.settings_reminder_time),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                TimePicker(state = st, layoutType = TimePickerLayoutType.Vertical)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(AppCtx.s(R.string.common_cancel)) }
                    TextButton(onClick = { onConfirm(st.hour, st.minute) }) {
                        Text(AppCtx.s(R.string.common_done))
                    }
                }
            }
        }
    }
}

/**
 * 彩蛋（2026-10-06 加，3.5.38）。
 *
 * 逸风 2026-10-06 原话：「还是弹一个 Sheet，标题是你真的很闲，内容居中，是"看来你真的很闲了"」——
 * 文案就这两句，别自作聪明往里加东西。
 *
 * ⚠️ 触发方式：设置 → 关于 →「版本」那行**连点七下**（见上面 GroupRow 的 onClick）。
 *    为什么是七下：安卓查「开发者选项」就是连点版本号七下，借这个老规矩当暗门。
 * ⚠️ 文案走词条（easter_title / easter_body），4 个语言包都补了 ——
 *    绝不在代码里写死中文：切语言时那两行会当场露馅（3.5.37 修的就是这一类毛病）。
 * ⚠️ 高度交给自己撑（只有两行字），别给死高 —— 英文那句比中文长，给死了会被裁。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EasterEggSheet(onDismiss: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    ModalBottomSheet(
        onDismissRequest = {
            lightTick(haptic)
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                AppCtx.s(R.string.easter_title),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(30.dp))
            Text(
                AppCtx.s(R.string.easter_body),
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
