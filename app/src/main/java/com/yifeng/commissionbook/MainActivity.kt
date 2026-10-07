package com.yifeng.commissionbook

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private lateinit var state: AppState
    private lateinit var prefs: Prefs

    /** 锁着的状态：true = 停在锁屏页 */
    private val locked = mutableStateOf(false)

    /**
     * 隐私政策同意过没（2026-09-29 加）。
     *
     * 第一次装完打开 App，先停在这一页：**必须划到最底下**，同意按钮才点得动。
     * 存的是「同意时那一版政策的版本号」—— 以后政策改了，这里对不上，会再弹一次。
     */
    private val agreed = mutableStateOf(false)

    /** 选完文件回来的那一下不该再验一次锁 */
    private var expectingFileResult = false

    /**
     * 导出 JSON 备份。
     *
     * ⚠️ 内容要在**回调里现算**（2026-10-07 改，原因见下面 createXlsx 那段长注释）：
     *    老写法是先把文字存进一个字段，中间 App 一旦被系统回收重建，字段就没了 ——
     *    回调照样跑、手里却是空的 → 盘上留下一个 0 字节的「备份」。
     */
    private val createDoc = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        expectingFileResult = false
        if (uri == null) return@registerForActivityResult
        val n = Backup.writeBytes(this, uri, state.toJsonString().toByteArray())
        toast(
            if (n != null) {
                AppCtx.s(R.string.common_saved)
            } else {
                AppCtx.s(R.string.common_save_failed)
            }
        )
    }

    // 导出 Excel 报表（2026-10-07 加）→ **2026-10-07 当天就撤了**：
    // 逸风原话「算了，撤掉这个功能，双端」。原来那套 createXlsx / Report / XlsxWriter
    // 已经删掉（在 git 历史里），捡回来的清单见 tools/已撤掉-导出Excel报表-2026-10-07.md。

    private val openDoc = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        expectingFileResult = false
        if (uri == null) return@registerForActivityResult
        // 2026-10-03：恢复时把备份里的参考图也写回本机（备份带图，见 Backup.encode）。
        // ⚠️ 图要**先落盘再 replaceAll** —— 反过来的话，replaceAll 里那次「清孤儿图」
        //    会把刚恢复进来的图当成没人要的，当场删掉。
        val payload = Backup.readFrom(this, uri)?.let { text ->
            Backup.decode(text) { name, bytes -> state.writePhotoBytes(name, bytes) }
        }
        if (payload == null) {
            toast(AppCtx.s(R.string.common_backup_unreadable))
        } else {
            state.replaceAll(payload.first, payload.second)
            toast(AppCtx.s(R.string.common_restored_count, payload.first.size))
        }
    }

    /**
     * 把用户选的语言真正「装」进这个 Activity（2026-10-03 加，多语言）。
     *
     * ⚠️ **为什么非得自己写这一步**：
     *    Activity 的 `resources` 是**启动时**按 Configuration 加载的。
     *    这里 `createConfigurationContext` 把语言塞进配置，再拿这个包好的 context
     *    去做 base —— 界面里的资源（含 `AppCtx.s()` 走的那些）才是新语言。
     *
     * ⚠️ **别改回 `AppCompatDelegate.getApplicationLocales()`**：
     *    试过了，拿回来**永远是空的** —— 那套存储挂在 AppCompatActivity 的语言委托上，
     *    而 MainActivity 继承的是 `FragmentActivity`（没有委托）。
     *    真机表现：点了「现在重启」、页面也重建了，一个字都没变。
     *    现在读的是自己存的 [Prefs.appLanguage]，稳。
     */
    override fun attachBaseContext(newBase: Context) {
        val lang = Prefs.languageOf(newBase)
        val ctx = if (lang.isNotEmpty()) {
            val cfg = Configuration(newBase.resources.configuration)
            cfg.setLocales(android.os.LocaleList.forLanguageTags(lang))
            newBase.createConfigurationContext(cfg)
        } else {
            newBase
        }
        super.attachBaseContext(ctx)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        // 全局取字符串的入口（多语言）。**必须在 setContent 之前** ——
        // 界面一画出来就会去取文案，晚一步就是满屏空白。
        // ⚠️ 语言要一起喂进去：AppCtx 存的是 applicationContext，
        //    它的资源不跟着 Activity 走，必须在这里也包一层（见 AppCtx 的注释）。
        AppCtx.init(this, prefs.appLanguage)
        state = AppState(this)
        applyPrivacyShield(prefs.lockEnabled)
        locked.value = prefs.lockEnabled
        // 这一版政策同意过没有？同意过才放行；没同意过 = 停同意页
        agreed.value = prefs.privacyAgreedVersion == POLICY_VERSION

        setContent {
            // 深浅色（2026-10-03 加）：最终那个布尔值在这儿算好再喂进去。
            // 「跟随系统」才去看系统那档；「浅色 / 暗色」直接无视系统。
            // 用 state.appearance（Compose 状态）而不是 prefs —— 设置页一拨，
            // 整个 App 当场重画，不用重启。
            AppTheme(dark = state.appearance.isDark(isSystemInDarkTheme())) {
                // 底栏页号 + 帮助页盖层放在**最外这一层**（2026-10-01 加）。
                // 原来这两样都藏在 RootScreen 里，外面够不着 —— 挪上来就为了
                // 首启引导里那句「现在去看」能**一步跳到设置页并掀开帮助页**。
                val tabs = Tab.entries
                val pager = rememberPagerState(pageCount = { tabs.size })
                var showHelp by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()

                // ── 通知权限：安卓 13（API 33）起，能不能弹通知要用户点头 ──────────
                //
                // ⚠️ 2026-10-05 修的第二个坑（跟「冷启动崩溃」是两回事）：
                //    这个申请原来**只挂在「设置 → 提醒」那个开关上**，而那个开关
                //    `reminderEnabled` 默认就是 **true** —— 也就是说：装完打开，
                //    开关已经是开着的，没人会去拨它，`askNotif()` 一辈子没被调用过，
                //    于是**通知权限从来没申请过**（安卓 13+ 默认拒绝）→
                //    闹钟照响、接收器照跑、`notify()` 照调用，系统默默丢掉 ——
                //    **手机上一点动静都没有**，而且连个报错都看不到。
                //
                //    逸风在真机上「还是没响」，就是这一条。
                //
                // 现在跟 iOS 对齐（iOS 的顺序是「政策 → 权限 → 模式」）：
                // ① 同意政策后立刻问一次；② 老用户进主界面时补问一次。
                // 只自动问一次（`notifAsked`），拒了就不再骚扰。
                val notifPerm = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                ) { /* 给不给都放行，不拦人 */ }
                val askNotifOnce: () -> Unit = {
                    val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        android.Manifest.permission.POST_NOTIFICATIONS,
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (android.os.Build.VERSION.SDK_INT >= 33 && !prefs.notifAsked && !granted) {
                        prefs.notifAsked = true      // 先落旗子，免得同一轮问两遍
                        notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                LaunchedEffect(agreed.value, locked.value) {
                    if (agreed.value && !locked.value) {
                        delay(600)                   // 让模式弹窗先站稳，别两个框叠一起
                        askNotifOnce()
                    }
                }

                // 引导晚 0.4 秒再冒出来 —— 紧跟在模式弹窗后面，别两个框「啪」一下叠一起
                var guideReady by remember { mutableStateOf(false) }
                LaunchedEffect(state.modeChosen) {
                    if (state.modeChosen) {
                        delay(400)
                        guideReady = true
                    }
                }

                when {
                    // ① 没同意过 —— 先读政策，划到底才点得动同意（应用市场要的合规姿势）
                    !agreed.value -> PrivacyConsentScreen(
                        onAgree = {
                            prefs.privacyAgreedVersion = POLICY_VERSION
                            agreed.value = true
                            // 2026-10-05：同意完就顺手问一次通知权限（跟 iOS 的「政策 → 权限」对齐）
                            askNotifOnce()
                            // 万一他还开着保护，同意完顺手把脸也验了
                            if (locked.value) unlock()
                        },
                    )

                    // ② 锁着 —— 先验本人
                    locked.value -> {
                        LockScreen(hasBio = BioLock.available(this)) { unlock() }
                    }

                    // ③ 正常进 App
                    else -> {
                        RootScreen(
                            state = state,
                            prefs = prefs,
                            pager = pager,
                            showHelp = showHelp,
                            onShowHelp = { showHelp = it },
                            onExport = { _ ->
                                // ⚠️ 内容不在这儿拼了（2026-10-07 改）：存到哪是系统弹窗问的，
                                //    回来的时候 App 可能已被回收重建 —— 见 createDoc 的注释。
                                expectingFileResult = true
                                createDoc.launch(Backup.fileName(manual = true))
                            },
                            // onExportReport（导出 Excel 报表）2026-10-07 撤掉，见类顶部那行注释

                            onImport = {
                                expectingFileResult = true
                                openDoc.launch(arrayOf("application/json", "text/plain", "*/*"))
                            },
                            onShare = { text, name ->
                                val intent = Backup.share(this, name, text)
                                if (intent == null) toast(AppCtx.s(R.string.common_share_failed_2)) else startActivity(intent)
                            },
                            // ① 年度报告那张图：生成好的位图落到 cache，再用系统分享面板发出去
                            // （不走相册那套，所以**不用任何存储权限**）
                            onSharePng = { bmp ->
                                val intent = sharePng(this, bmp, AppCtx.s(R.string.stats_annual_report_file))
                                if (intent == null) {
                                    toast(AppCtx.s(R.string.common_share_failed))
                                } else {
                                    startActivity(Intent.createChooser(intent, AppCtx.s(R.string.stats_share_annual_report)))
                                }
                            },
                            onCopied = { msg -> toast(msg) },
                        )
                    }
                }

                // 首启两层（2026-09-30 / 2026-10-01 加）：
                // 政策读完 + 人没被锁屏拦着 → ① 先问「你是买家还是画师」
                //                            → ② 再指一句「用法都收在设置 → 帮助里」
                // 两个都只弹一次：选完 / 点「稍后再说」都算问过，之后在设置里随时能改。
                // （Dialog 是另一个窗口，挂在这儿不影响底下的布局）
                if (agreed.value && !locked.value && !state.modeChosen) {
                    ModePickerDialog(
                        onPick = { m ->
                            state.chooseMode(m)
                            toast(if (m == AppMode.BUYER) AppCtx.s(R.string.settings_switched_buyer) else AppCtx.s(R.string.settings_switched_artist))
                        },
                        onLater = { state.skipModePicker() },
                    )
                } else if (agreed.value && !locked.value && state.modeChosen && guideReady && !state.guideShown) {
                    // A（2026-10-01 加）：模式问完，补一句指路 ——
                    // 光有帮助页不够，新装的人压根不知道它在哪儿。
                    // 「现在去看」= 跳到设置页 + 直接掀开帮助页（顺手把底栏那枚角标摘了）
                    GuideDialog(
                        onOpenHelp = {
                            state.markGuideShown()
                            state.markHelpSeen()
                            scope.launch { pager.animateScrollToPage(Tab.Settings.ordinal) }
                            showHelp = true
                        },
                        onDone = { state.markGuideShown() },
                    )
                }
            }

            // 启动时静默检查一次更新（2026-10-03 加，**只有安卓版有**）
            //
            // 「静默」= 只在**真的发现新版本**时才冒出来。检查中、已是最新、
            // 网络不通这几种情况一律不出声 —— 一开 App 就闪个「正在检查」太吵。
            //
            // ⚠️ 12 小时限流 + 失败也记时间戳：不这么做的话，网络不通时
            //    每次冷启动都要对着一个 6 秒超时干等一遍。
            var startupUpdate by remember { mutableStateOf<UpdateStatus?>(null) }
            LaunchedEffect(agreed.value, locked.value) {
                if (!agreed.value || locked.value) return@LaunchedEffect
                if (!prefs.updateAutoCheck) return@LaunchedEffect
                if (System.currentTimeMillis() - prefs.lastUpdateCheckAt < 12L * 60 * 60 * 1000) {
                    return@LaunchedEffect
                }
                val r = doCheck(this@MainActivity, prefs) { /* 启动这次不要中间态 */ }
                if (r is UpdateStatus.Available) startupUpdate = r
            }
            UpdateDialog(startupUpdate, prefs) { startupUpdate = it }
        }

        // 同意过、又开着保护 → 立刻弹一次验证（没同意过就先别弹，人还没读政策呢）
        if (agreed.value && locked.value) unlock()
    }

    override fun onStop() {
        super.onStop()
        // 切后台 = 重新上锁；只是去选个文件的就不锁，免得回来又弹一次
        if (!expectingFileResult && prefs.lockEnabled) locked.value = true
    }

    // ---------- 全局触感（2026-10-07 加）----------

    /** 这一根手指按下时的位置 */
    private var touchDownX = 0f
    private var touchDownY = 0f

    /** 这一根手指动过没有（动过 = 滑动，不算点击） */
    private var touchMoved = false

    /** 系统的滑动判定阈值 */
    private val touchSlop: Int by lazy { ViewConfiguration.get(this).scaledTouchSlop }

    /**
     * 全局触感 —— 跟 iOS 那只「装在窗口上的耳朵」对齐（iOS 侧见 Haptics.swift）。
     *
     * 逸风原话：「Android 版本不是全局震动，建议改成跟 iOS 一样的」。
     * iOS 是给**窗口**挂一个 UITapGestureRecognizer，所以窗口底下的一切都震
     * （sheet、弹窗、盖在最上面的锁屏页）。这边走 Activity 的触摸分发，
     * 效果一样：整个窗口里每一次**点击**都算。
     *
     * ⚠️ 三条讲究，别简化：
     *  ① **抬手（ACTION_UP）才震，不是按下去就震。** iOS 那条路是"手势被识别"才回调，
     *     而 UITapGestureRecognizer 要等抬手才认。按下就震的话，手指划着列表走一路
     *     会震一路 —— 那不是点击反馈，是骚扰。
     *  ② **动了就不算点击**：超过系统 touchSlop 当滑动处理，不震。跟 ① 同一个道理。
     *  ③ Compose 的 Dialog / ModalBottomSheet 活在**另一个 window** 里，
     *     走不到这儿 —— 它们由各自的 lightTick() 兜着。两边可能同时触发，
     *     所以 [Haptics.tick] 里有 60ms 去重，撞一起也只震一声。
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = ev.rawX
                touchDownY = ev.rawY
                touchMoved = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(ev.rawX - touchDownX) > touchSlop || abs(ev.rawY - touchDownY) > touchSlop) {
                    touchMoved = true
                }
            }
            MotionEvent.ACTION_UP -> if (!touchMoved) Haptics.tick()
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun unlock() {
        if (!prefs.lockEnabled) {
            locked.value = false
            return
        }
        if (!BioLock.available(this)) {
            // 这台手机连锁屏密码都没设 —— 保护开不了，别把他锁在外面
            locked.value = false
            toast(AppCtx.s(R.string.settings_no_passcode))
            return
        }
        BioLock.authenticate(
            activity = this,
            onOk = { locked.value = false },
            onNo = { /* 取消了就停在锁屏页，他再点一次 */ },
        )
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

// MARK: - 四个标签页

private enum class Tab(val labelRes: Int) {
    Ledger(R.string.common_ledger),
    Artists(R.string.common_artist),
    Stats(R.string.stats_title),
    Settings(R.string.settings_title);

    // ⚠️ 别写成构造参数 `Ledger(AppCtx.s(...))` —— enum 常量在类初始化时求值一次，
    //    那时 AppCtx 还没 init，底栏四个名字会全变成空白。
    val label: String get() = AppCtx.s(labelRes)
}

@Composable
private fun RootScreen(
    state: AppState,
    prefs: Prefs,
    // ⚠️ pager 和 showHelp 由外面（MainActivity）拿着 —— 见那边的注释：
    //    首启引导要「一步跳到设置并打开帮助」，就得够得着这两样。
    pager: PagerState,
    showHelp: Boolean,
    onShowHelp: (Boolean) -> Unit,
    onExport: (String) -> Unit,
    onImport: () -> Unit,
    onShare: (String, String) -> Unit,
    onSharePng: (android.graphics.Bitmap) -> Unit,
    onCopied: (String) -> Unit,
) {
    val tabs = Tab.entries
    // 「像滑卡片一样切页」：用 HorizontalPager。
    // 点页签 → 整页滑过去；手指直接左右划也行（不用点导航栏）。
    // 这不是自绘皮肤 —— 页面本身还是原来那四个 Screen，只是换了个装它们的容器。
    val scope = rememberCoroutineScope()

    // 设置页 →「隐私政策」：整页盖上来（连底部导航栏一起盖住）。
    // 放在 Box 里、排在 Scaffold 后面 = 画在最上层。
    var showPrivacy by remember { mutableStateOf(false) }
    // 反馈页（2026-09-29 加）：跟隐私政策一样，盖一层，不切页签
    var showFeedback by remember { mutableStateOf(false) }
    // 2026-09-30 第三批：① 年度报告 / ⑥ 报价计算器（同样盖一层，连底栏一起盖住）
    var showReport by remember { mutableStateOf(false) }
    var showCalculator by remember { mutableStateOf(false) }
    // 2026-10-06（只有安卓有）：支持作者页，同样盖一层
    var showSponsor by remember { mutableStateOf(false) }
    // 2026-10-01：帮助页（同样盖一层）—— 状态在 MainActivity 那边（首启引导要够得着它）

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                BottomBar(
                    tabs = tabs,
                    currentPage = pager.currentPage,
                    // 模式带着走：底栏第三格「画师」在画师模式下要显示成「客户」
                    mode = state.appMode,
                    // B（2026-10-01 加）：帮助页没看过 → 设置那格挂一枚小角标「新」，
                    // 进过帮助页就摘掉。功能越加越多，得给人留个「这儿有东西没看」的记号。
                    badgeOnSettings = !state.helpSeen,
                    // 「已经到第几页 + 手指划到哪儿了」：
                    // 那枚药丸跟着页面实时滑，而不是等切完了才「啪」跳过去
                    offsetFraction = pager.currentPageOffsetFraction,
                    onSelect = { i -> scope.launch { pager.animateScrollToPage(i) } },
                )
            }
        ) { pad ->
            HorizontalPager(
                state = pager,
                modifier = Modifier.padding(pad).fillMaxSize(),
            ) { page ->
                val m = Modifier.fillMaxSize()
                when (tabs[page]) {
                    Tab.Ledger -> LedgerScreen(m, state)
                    Tab.Artists -> ArtistsScreen(m, state)
                    Tab.Stats -> StatsScreen(m, state, onOpenReport = { showReport = true })
                    Tab.Settings -> SettingsScreen(
                        modifier = m,
                        state = state,
                        prefs = prefs,
                        onExport = onExport,
                        onImport = onImport,
                        onShare = onShare,
                        onOpenPrivacy = { showPrivacy = true },
                        onOpenFeedback = { showFeedback = true },
                        onOpenCalculator = { showCalculator = true },
                        onOpenHelp = {
                            // 从设置里进来也算「看过了」→ 摘掉底栏那枚角标（2026-10-01）
                            state.markHelpSeen()
                            onShowHelp(true)
                        },
                        onOpenSponsor = { showSponsor = true },
                    )
                }
            }
        }

        // 淡入 + 极轻微上推 —— 不硬切（逸风最在意这点）
        AnimatedVisibility(
            visible = showPrivacy,
            enter = fadeIn(tween(200)) + slideInVertically(tween(260)) { it / 14 },
            exit = fadeOut(tween(150)),
        ) {
            PrivacyScreen(onBack = { showPrivacy = false })
        }

        // 反馈页：同一套出场方式，别两个页面两种动静
        AnimatedVisibility(
            visible = showFeedback,
            enter = fadeIn(tween(200)) + slideInVertically(tween(260)) { it / 14 },
            exit = fadeOut(tween(150)),
        ) {
            FeedbackScreen(onBack = { showFeedback = false })
        }

        // ① 年度报告（2026-09-30 第三批）：同一套出场方式
        AnimatedVisibility(
            visible = showReport,
            enter = fadeIn(tween(200)) + slideInVertically(tween(260)) { it / 14 },
            exit = fadeOut(tween(150)),
        ) {
            YearReportScreen(
                items = state.modeItems,
                mode = state.appMode,
                onBack = { showReport = false },
                onSharePng = onSharePng,
                onCopied = onCopied,
            )
        }

        // ⑥ 报价计算器
        AnimatedVisibility(
            visible = showCalculator,
            enter = fadeIn(tween(200)) + slideInVertically(tween(260)) { it / 14 },
            exit = fadeOut(tween(150)),
        ) {
            QuoteCalculatorScreen(
                onBack = { showCalculator = false },
                onCopied = onCopied,
            )
        }

        // 帮助页（2026-10-01 加）：同一套出场方式，别两个页面两种动静
        AnimatedVisibility(
            visible = showHelp,
            enter = fadeIn(tween(200)) + slideInVertically(tween(260)) { it / 14 },
            exit = fadeOut(tween(150)),
        ) {
            HelpScreen(
                mode = state.appMode,
                onBack = { onShowHelp(false) },
            )
        }

        // 支持作者页（2026-10-06 加）：同一套出场方式，别两个页面两种动静
        AnimatedVisibility(
            visible = showSponsor,
            enter = fadeIn(tween(200)) + slideInVertically(tween(260)) { it / 14 },
            exit = fadeOut(tween(150)),
        ) {
            SponsorScreen(onBack = { showSponsor = false })
        }
    }
}

private fun iconOf(t: Tab): ImageVector = when (t) {
    Tab.Ledger -> Icons.Outlined.ReceiptLong
    Tab.Artists -> Icons.Outlined.Group
    Tab.Stats -> Icons.Outlined.PieChart
    Tab.Settings -> Icons.Outlined.Settings
}

/**
 * 底部导航栏（2026-09-29 第 2 版：悬浮胶囊 + 会滑的药丸 + 躲开系统三大金刚键）。
 *
 * 两个必须踩住的点：
 *
 * ① **让开系统导航栏**。安卓 15 起系统强制「内容画到系统栏底下」（边到边），
 *    系统自带的 NavigationBar 会自己躲，我这枚自己搭的不会 ——
 *    1.7 就是漏了这一步，逸风报「跟三大金刚键重合了」。
 *    解法：`windowInsetsPadding(WindowInsets.navigationBars)`，
 *    胶囊被抬到金刚键上方，金刚键那一带留给系统（它自己会画底）。
 *
 * ② **药丸是一枚会滑的东西**，不是四个各自亮灭。
 *    所以没用 material3 的 NavigationBar（它每个 item 各管自己那枚）。
 */
@Composable
private fun BottomBar(
    tabs: List<Tab>,
    currentPage: Int,
    offsetFraction: Float,
    mode: AppMode,
    badgeOnSettings: Boolean,
    onSelect: (Int) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val dark = isDarkUi()
    val pillH = 40.dp
    val pillW = 56.dp
    val barH = 64.dp
    val shape = RoundedCornerShape(24.dp)

    Box(
        Modifier
            .fillMaxWidth()
            // ① 关键的一行：给系统导航栏（三大金刚键 / 手势条）留出它们的高度
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp)
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(barH)
                .then(
                    if (dark) Modifier
                    else Modifier.shadow(
                        elevation = 16.dp,
                        shape = shape,
                        clip = false,
                        ambientColor = Color(0x1A1B3A5E),
                        spotColor = Color(0x26213C5E),
                    )
                )
                .clip(shape)
                .background(if (dark) cs.surface.copy(alpha = 0.97f) else cs.surface.copy(alpha = 0.97f))
                // 顶边一道很淡的高光 —— 玻璃/亚克力的边缘那一下，让它不像一块塑料贴片
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (dark) 0.10f else 0.85f),
                            Color.Transparent,
                        )
                    ),
                    shape = shape,
                )
        ) {
            val slotW = maxWidth / tabs.size
            // 位置 = 页号 + 拖动比例 → 手指划到一半，药丸就滑到一半
            val pos = (currentPage + offsetFraction).coerceIn(0f, (tabs.size - 1).toFloat())
            val pillX = slotW * pos + (slotW - pillW) / 2f
            val pillY = (barH - pillH) / 2f

            // ① 底层：一枚带投影的药丸，整枚跟着页面滑（不是四枚各自亮灭）
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset(x = pillX, y = pillY)
                    .size(pillW, pillH)
                    .shadow(
                        elevation = 5.dp,
                        shape = RoundedCornerShape(15.dp),
                        clip = false,
                        ambientColor = Color(0x1F2E74CA),
                        spotColor = Color(0x332E74CA),
                    )
                    .background(cs.primaryContainer, RoundedCornerShape(15.dp))
            )

            // ② 上层：四个图标 + 文字（药丸从它们背后滑过）
            Row(Modifier.fillMaxSize()) {
                tabs.forEachIndexed { i, t ->
                    val selected = i == currentPage
                    TabItem(
                        t = t,
                        selected = selected,
                        mode = mode,
                        // 「新」角标只挂在设置那格，而且只有帮助页没看过时才挂
                        badge = badgeOnSettings && t == Tab.Settings,
                        onSelect = { onSelect(i) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.TabItem(
    t: Tab,
    selected: Boolean,
    mode: AppMode,
    badge: Boolean,
    onSelect: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    // 「画师」那一格在画师模式下叫「客户」—— 底栏也跟着模式走，不然两个页面两套词
    val tabLabel = if (t == Tab.Artists) Terms.other(mode) else t.label
    val tint by animateColorAsState(
        targetValue = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant,
        animationSpec = tween(180),
        label = "tint",
    )
    val interaction = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 1200f),
        label = "tabScale",
    )
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(
                selected = selected,
                role = Role.Tab,
                interactionSource = interaction,
                indication = null,
                onClick = { lightTick(haptic); onSelect() },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 图标 + 右上角那枚小角标（2026-10-01 加）。
        // 角标只写一个字「新」—— 跟 iOS 那边 TabView 的 .badge(Text("新")) 对齐，
        // 两端看到的是同一件事，别一边红点一边数字。
        Box(Modifier.size(23.dp)) {
            Icon(
                iconOf(t),
                contentDescription = tabLabel,
                tint = tint,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = scale; scaleY = scale },
            )
            if (badge) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        // 往外挪一点 + 一圈底色描边（2026-10-01 修）：
                        // 第一版贴在 (8, -5) 又没有描边，实拍下来红标压在齿轮右上角的齿上，
                        // 挤成一团。加一圈同底色（surface）的细边，红标就「浮」在图标外面了。
                        .offset(x = 9.dp, y = (-6).dp)
                        .background(Color(0xFFFF3B30), RoundedCornerShape(7.dp))
                        .border(1.5.dp, cs.surface, RoundedCornerShape(7.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text(
                        AppCtx.s(R.string.common_new),
                        fontSize = 8.5.sp,
                        lineHeight = 10.sp,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            tabLabel,
            fontSize = 11.sp,
            color = tint,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
