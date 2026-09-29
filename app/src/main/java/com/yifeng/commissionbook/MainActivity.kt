package com.yifeng.commissionbook

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private lateinit var state: AppState
    private lateinit var prefs: Prefs

    /** 锁着的状态：true = 停在锁屏页 */
    private val locked = mutableStateOf(false)

    /** 选完文件回来的那一下不该再验一次锁 */
    private var expectingFileResult = false

    /** 「另存为」按下时，先把要写的文本放这儿，等用户选完位置再写 */
    private var pendingText = ""

    private val createDoc = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        expectingFileResult = false
        if (uri != null) {
            val ok = Backup.writeTo(this, uri, pendingText)
            toast(if (ok) "已保存 ✓" else "保存失败，换个位置再试一次")
        }
    }

    private val openDoc = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        expectingFileResult = false
        if (uri == null) return@registerForActivityResult
        val payload = Backup.readFrom(this, uri)?.let { Backup.decode(it) }
        if (payload == null) {
            toast("这个文件读不出来，可能不是本 App 的备份")
        } else {
            state.replaceAll(payload.first, payload.second)
            toast("已恢复 ${payload.first.size} 条约稿 ✓")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state = AppState(this)
        prefs = Prefs(this)
        applyPrivacyShield(prefs.lockEnabled)
        locked.value = prefs.lockEnabled

        setContent {
            AppTheme {
                if (locked.value) {
                    LockScreen(hasBio = BioLock.available(this)) { unlock() }
                } else {
                    RootScreen(
                        state = state,
                        prefs = prefs,
                        onExport = { text ->
                            pendingText = text
                            expectingFileResult = true
                            createDoc.launch(Backup.fileName(manual = true))
                        },
                        onImport = {
                            expectingFileResult = true
                            openDoc.launch(arrayOf("application/json", "text/plain", "*/*"))
                        },
                        onShare = { text, name ->
                            val intent = Backup.share(this, name, text)
                            if (intent == null) toast("分享失败") else startActivity(intent)
                        },
                    )
                }
            }
        }

        if (locked.value) unlock()
    }

    override fun onStop() {
        super.onStop()
        // 切后台 = 重新上锁；只是去选个文件的就不锁，免得回来又弹一次
        if (!expectingFileResult && prefs.lockEnabled) locked.value = true
    }

    private fun unlock() {
        if (!prefs.lockEnabled) {
            locked.value = false
            return
        }
        if (!BioLock.available(this)) {
            // 这台手机连锁屏密码都没设 —— 保护开不了，别把他锁在外面
            locked.value = false
            toast("这台手机还没设锁屏密码，得先设一个才能用保护")
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

private enum class Tab(val label: String) {
    Ledger("账本"), Artists("画师"), Stats("统计"), Settings("设置")
}

@Composable
private fun RootScreen(
    state: AppState,
    prefs: Prefs,
    onExport: (String) -> Unit,
    onImport: () -> Unit,
    onShare: (String, String) -> Unit,
) {
    val tabs = Tab.entries
    // 「像滑卡片一样切页」：用 HorizontalPager。
    // 点页签 → 整页滑过去；手指直接左右划也行（不用点导航栏）。
    // 这不是自绘皮肤 —— 页面本身还是原来那四个 Screen，只是换了个装它们的容器。
    val pager = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            BottomBar(
                tabs = tabs,
                currentPage = pager.currentPage,
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
                Tab.Stats -> StatsScreen(m, state)
                Tab.Settings -> SettingsScreen(
                    modifier = m,
                    state = state,
                    prefs = prefs,
                    onExport = onExport,
                    onImport = onImport,
                    onShare = onShare,
                )
            }
        }
    }
}

private fun iconOf(t: Tab): ImageVector = when (t) {
    Tab.Ledger -> Icons.Filled.ReceiptLong
    Tab.Artists -> Icons.Filled.Group
    Tab.Stats -> Icons.Filled.PieChart
    Tab.Settings -> Icons.Filled.Settings
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
                    TabItem(t, selected, onSelect = { onSelect(i) })
                }
            }
        }
    }
}

@Composable
private fun RowScope.TabItem(
    t: Tab,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
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
        Icon(
            iconOf(t),
            contentDescription = t.label,
            tint = tint,
            modifier = Modifier.size(23.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        )
        Spacer(Modifier.height(3.dp))
        Text(
            t.label,
            fontSize = 11.sp,
            color = tint,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
