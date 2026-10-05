package com.yifeng.commissionbook

import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.content.Intent
import kotlinx.coroutines.launch
import java.util.Calendar

private enum class SortMode(val labelRes: Int) {
    ByDate(R.string.ledger_sort_by_order_date),
    ByMoney(R.string.ledger_sort_by_amount),
    ByDeadline(R.string.ledger_sort_by_deadline);

    // ⚠️ 不能写成构造参数 `ByDate(AppCtx.s(...))`：
    //    enum 常量在**类初始化时**就求值一次，那会儿 AppCtx 还没 init，取到的是空串；
    //    而且求值一次之后再也不更新，切了语言也不变。必须做成计算属性。
    val label: String get() = AppCtx.s(labelRes)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(modifier: Modifier, state: AppState) {

    var showArchived by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf<CommissionStatus?>(null) }
    var sort by remember { mutableStateOf(SortMode.ByDate) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Commission?>(null) }
    var creating by remember { mutableStateOf(false) }
    // 2026-09-30 第三批：⑤ 话术 Sheet / ② 交付小庆祝
    var nudgeItem by remember { mutableStateOf<Commission?>(null) }
    var celebrate by remember { mutableStateOf(false) }
    // 2026-10-01：② 分享这一单 / ④ 多选批量 / ① 示例提示
    var shareItem by remember { mutableStateOf<Commission?>(null) }
    var selecting by remember { mutableStateOf(false) }
    val picked = remember { mutableStateListOf<String>() }
    var confirmDelete by remember { mutableStateOf(false) }
    // 2026-10-03：把「所有删除都先问一句」补全 ——
    //   单条删除（卡片「⋮」里那颗）以前是**直接删**的，只有批量那条路会问。
    //   逸风：「每个删除操作都要弹这个弹窗确认一遍」。
    /** 有值 = 正在问「这条要不要删掉」 */
    var pendingDelete by remember { mutableStateOf<Commission?>(null) }
    /** true = 正在问「那两条示例要不要清掉」 */
    var confirmClearSamples by remember { mutableStateOf(false) }
    // 首次打开那两条示例还在不在（列表顶上那条「清掉」用）
    val demos = remember(state.items) {
        val ids = state.demoIds
        state.items.filter { it.id in ids }
    }
    val ctx = LocalContext.current
    // 列表滚动位置 → 喂给大标题做「滚上去就缩小」
    val listState = rememberLazyListState()

    // 先按「归档 / 进行中」分家，再套筛选和搜索
    // ⚠️ 只看**当前模式**那一摞（买家 / 画师账本各记各的）
    var pool = state.modeItems.filter { it.archived == showArchived }
    filter?.let { f -> pool = pool.filter { it.status == f } }
    if (query.isNotBlank()) {
        val q = query.trim()
        pool = pool.filter { it.artist.contains(q, true) || it.title.contains(q, true) || it.note.contains(q, true) }
    }
    val shown = when (sort) {
        SortMode.ByDate -> pool.sortedByDescending { it.dateMillis }
        SortMode.ByMoney -> pool.sortedByDescending { it.total }
        SortMode.ByDeadline -> pool.sortedBy { it.deadlineMillis ?: Long.MAX_VALUE }
    }

    ScreenSurface(modifier) {
        Column(Modifier.fillMaxSize()) {

            // ① 大标题 + 搜索（往上滚会缩下去，iOS 那种）
            CollapsibleLargeTitle(
                title = AppCtx.s(R.string.ledger_title),
                collapse = rememberCollapseFraction(listState),
                caption = AppCtx.s(R.string.ledger_summary_count, state.modeItems.size, state.active.size),
            ) {
                // 新增按钮（2026-09-30 从右下角搬上来）：
                // 原来是右下角那颗悬浮球，逸风说「有点遮挡了」→ 挪到右上角。
                // 顺带跟 iOS 版对齐（那边一直是右上角的 +）。
                CircleIconButton(Icons.Filled.Search, AppCtx.s(R.string.common_search)) {
                    searching = !searching
                    if (!searching) query = ""
                }
                Spacer(Modifier.width(8.dp))
                CircleIconButton(Icons.Filled.Add, AppCtx.s(R.string.common_add)) { creating = true }
            }

            // ⚠️ 2026-10-02 改：**整页并进同一个 LazyColumn**。
            //
            // 逸风：「在账本页面全局滑动而不是只有下半部分可以滑动，不然有条分界线太难看了」。
            // 以前是「上面一堆钉死的内容 + 底下单独一个 LazyColumn」——
            // 卡片往上滚时会在筛选那排底下被硬切出一道边。
            // 现在把搜索框 / 提示条 / 合计卡 / 排期卡 / 分段行 / 胶囊行
            // 全塞进同一个 LazyColumn 当普通 item：一滑整页一起动，没有分界。
            // 只有大标题还钉在上面（它是「导航栏」性质，本来就该固定）。
            //
            // ⚠️ contentPadding 只给上下 —— 横向留白由每个 item 自己带（各 16dp），
            //    两边都给会叠成 32dp，跟大标题那层对不齐。
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    top = 8.dp,
                    // ⚠️ 多选时底下浮着那条「已选 N 条 / 归档 / 删除」——
                    //    不多留一截的话，列表最后一条会被它整个压住
                    //    （逸风 2026-10-02 报的：「会挡住条目」）。
                    bottom = if (selecting) 100.dp else 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {

            // 搜索框：淡入 + 向下撑开，不要「刷的一下」蹦出来（逸风 2026-09-29 的审美要求）
            item(key = "search") {
                AnimatedVisibility(
                    visible = searching,
                    enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(180)),
                ) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(Terms.searchHint(state.appMode), fontSize = 14.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }
            }

            // ① 首次打开那两条示例：顶上浮一条「点这里清掉」（2026-10-01 加）。
            //    不想要就一键删干净，不用自己一条条进菜单去删。
            if (demos.isNotEmpty()) {
                item(key = "demo") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                        .clickable {
                            // 2026-10-03：清示例也是删数据，先问一句再动手
                            confirmClearSamples = true
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        AppCtx.s(R.string.ledger_samples_hint, demos.size),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                }
            }

            // ② 合计（只算「进行中」的 —— 归档的不算，跟 iOS 版一致）
            // 切「已归档」时它是收走 / 放回，不是硬切
            item(key = "hero") {
            AnimatedVisibility(
                visible = !showArchived,
                enter = fadeIn(tween(180)) + expandVertically(tween(240)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(200)),
            ) {
                HeroCard(Modifier.padding(horizontal = 16.dp)) {
                    Text(AppCtx.s(R.string.ledger_in_progress_total), fontSize = 12.sp, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f))
                    Spacer(Modifier.height(4.dp))
                    // ④ 会滚的数字（2026-09-30 第三批）：进来从 0 滚上去，数据一变从旧值滚到新值
                    RollingMoney(
                        state.activeTotal,
                        fontSize = 30.sp,
                        color = androidx.compose.ui.graphics.Color.White,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        HeroCell(Terms.paid(state.appMode), money(state.activePaid), Modifier.weight(1f))
                        HeroDivider()
                        HeroCell(Terms.unpaid(state.appMode), money((state.activeTotal - state.activePaid).coerceAtLeast(0.0)), Modifier.weight(1f))
                        HeroDivider()
                        HeroCell(AppCtx.s(R.string.ledger_order_count_2), AppCtx.s(R.string.ledger_order_count, state.active.size), Modifier.weight(1f))
                    }
                }
            }
            }

            // ②.5 排期（2026-09-30 第二刀）
            // 手上还有几单、谁先交 —— 只看填了截止日的，按日期从近到远排，最多列 3 条。
            // 「记不全、排期乱」是接稿的人最头疼的两件事之一。
            val queue = state.active
                .filter { it.deadlineMillis != null }
                .sortedBy { it.deadlineMillis }
            // ⑦ 挤堆提示（2026-09-30 第三批）：这一周（含今天）要到期的有几单
            val crowded7 = queue.count { (it.daysLeft() ?: 99) in 0..7 }
            if (!showArchived && queue.isNotEmpty()) {
                item(key = "queue") {
                SoftCard(Modifier.padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Terms.scheduleTitle(state.appMode),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            AppCtx.s(R.string.ledger_order_count_value, Terms.handWord(state.appMode), queue.size),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    val head = queue.take(3)
                    head.forEachIndexed { i, c ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${i + 1}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(18.dp),
                            )
                            Text(
                                c.artist.ifBlank { Terms.otherBlank(state.appMode) },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                c.title.ifBlank { AppCtx.s(R.string.common_no_content) },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            val d = c.daysLeft()
                            Text(
                                when {
                                    c.status == CommissionStatus.DELIVERED -> AppCtx.s(R.string.ledger_delivered)
                                    d == null -> ""
                                    d < 0 -> AppCtx.s(R.string.ledger_overdue_days, -d)
                                    d == 0 -> AppCtx.s(R.string.notify_due_today)
                                    else -> AppCtx.s(R.string.notify_days_left, d)
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (c.status != CommissionStatus.DELIVERED && d != null && d < 0)
                                            MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (i < head.size - 1) Spacer(Modifier.height(8.dp))
                    }
                    if (queue.size > head.size) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            AppCtx.s(R.string.ledger_unlisted_orders, queue.size - head.size),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // ⑦ 挤堆提示：7 天内 ≥3 单就说一句（不新加字段，从现成截止日算）
                    if (crowded7 >= 3) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFFF9F0A).copy(alpha = 0.12f))
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFF9F0A),
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                AppCtx.s(R.string.ledger_week_crowded, crowded7),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFB26A00),
                            )
                        }
                    }
                }
                }
            }

            // ③ 进行中 / 已归档（iOS 分段控件）+ 排序 + 多选
            item(key = "toolbar") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (selecting) {
                    // ④ 多选中：这一排让位给「已选 N 条 / 全选 / 完成」
                    Text(
                        AppCtx.s(R.string.common_selected_count, picked.size),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        if (picked.size == shown.size && shown.isNotEmpty()) {
                            picked.clear()
                        } else {
                            picked.clear()
                            picked.addAll(shown.map { it.id })
                        }
                    }) {
                        Text(
                            if (picked.size == shown.size && shown.isNotEmpty()) AppCtx.s(R.string.common_deselect_all) else AppCtx.s(R.string.common_select_all),
                            fontSize = 13.sp,
                        )
                    }
                    TextButton(onClick = {
                        selecting = false
                        picked.clear()
                    }) {
                        Text(AppCtx.s(R.string.common_done), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    SegmentedControl(
                        options = listOf(AppCtx.s(R.string.common_in_progress), AppCtx.s(R.string.common_archived)),
                        index = if (showArchived) 1 else 0,
                        modifier = Modifier.weight(1f),
                    ) { i -> showArchived = i == 1 }
                    SortButton(sort) { sort = it }
                    // ④ 多选入口（2026-10-01 加）
                    CircleIconButton(Icons.Filled.Checklist, AppCtx.s(R.string.common_multi_select)) { selecting = true }
                }
            }
            }

            // ④ 进度筛选
            // ⚠️ 必须是**一排**（逸风 2026-09-29：「还是一排比较好看」）。
            // 外面那层 horizontalScroll 是兜底：真遇上比 320dp 还窄的怪屏能滑，而不是被硬切。
            item(key = "chips") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SlimChip(AppCtx.s(R.string.common_all), filter == null) { filter = null }
                CommissionStatus.entries.forEach { s ->
                    SlimChip(Terms.status(s, state.appMode), filter == s) { filter = if (filter == s) null else s }
                }
            }
            }

            // ⑤ 卡片们（不再自己开 LazyColumn —— 整页就是这一个）
            if (shown.isEmpty()) {
                item(key = "empty") {
                    EmptyHint(
                        when {
                            query.isNotBlank() -> AppCtx.s(R.string.common_no_results)
                            showArchived -> AppCtx.s(R.string.ledger_archive_empty)
                            else -> Terms.empty(state.appMode)
                        }
                    )
                }
            } else {
                    items(shown, key = { it.id }) { item ->
                        if (selecting) {
                            // ④ 多选时换成「带勾的卡片」：点一下 = 打勾，不进编辑
                            PickCard(
                                item = item,
                                mode = state.appMode,
                                checked = item.id in picked,
                                modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
                                onClick = {
                                    if (item.id in picked) picked.remove(item.id) else picked.add(item.id)
                                },
                            )
                        } else {
                            CommissionCard(
                                item = item,
                                mode = state.appMode,
                                // 新增 / 删除 / 归档时，卡片是「滑进去 / 淡出来」而不是瞬间闪现
                                modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
                                onEdit = { editing = item },
                                onArchive = { state.toggleArchive(item.id) },
                                onDelete = { pendingDelete = item },
                                // ② 分享这一单（2026-10-01 加）：生成一张卡，存相册或发出去
                                onShare = { shareItem = item },
                                // ③ 左滑推进一档；推到「已交付」→ ② 来一下小庆祝
                                onAdvance = {
                                    val next = CommissionStatus.entries.getOrNull(item.status.ordinal + 1)
                                    if (next != null) {
                                        state.upsert(item.copy(status = next))
                                        if (next == CommissionStatus.DELIVERED) celebrate = true
                                    }
                                },
                                onNudge = { nudgeItem = item },
                                // 退回一档（2026-10-01 加）：点错推进了能原路退回来。
                                // 退到「待报价」就没得退了 —— 但**不弹提示、不弹确认框**，
                                // 箭头自己灰掉就够了，别为这点小事打断人。
                                onRetreat = {
                                    val prev = CommissionStatus.entries.getOrNull(item.status.ordinal - 1)
                                    if (prev != null) state.upsert(item.copy(status = prev))
                                },
                            )
                        }
                    }
            }
            }
        }

        // ② 交付那一下的小庆祝（2026-09-30 第三批）
        //
        // ⚠️ 必须写在**这个 Box 里面**（ScreenSurface 本身就是个 Box）。
        //    一开始写在 ScreenSurface 外面 → 它被排到屏幕外，压根看不见：
        //    账本页装在 HorizontalPager 里，一页里多出第二个子元素，
        //    会被当成「横向的第二项」摆到第一项右边去（跑出屏幕）。
        CelebrateOverlay(
            show = celebrate,
            label = AppCtx.s(R.string.ledger_delivered),
            onDone = { celebrate = false },
        )

        // ④ 多选时底下浮出操作栏（2026-10-01 加）
        // ⚠️ 跟 CelebrateOverlay 一个道理，必须写在 ScreenSurface **里面** ——
        //    写在外面会被 HorizontalPager 当成「横向的第二项」摆到屏幕外去。
        AnimatedVisibility(
            visible = selecting,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(180)) + slideInVertically(tween(240)) { it / 2 },
            exit = fadeOut(tween(120)) + slideOutVertically(tween(180)) { it / 2 },
        ) {
            BatchBar(
                count = picked.size,
                archivedView = showArchived,
                onArchive = {
                    val n = picked.size
                    state.archiveMany(picked.toList(), !showArchived)
                    toastNow(ctx, if (showArchived) AppCtx.s(R.string.ledger_restored_count, n) else AppCtx.s(R.string.ledger_archived_count, n))
                    selecting = false
                    picked.clear()
                },
                onDelete = { confirmDelete = true },
            )
        }
    }

    // ④ 批量删除：删了不可逆，先问一句
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(AppCtx.s(R.string.ledger_delete_confirm, picked.size)) },
            text = { Text(AppCtx.s(R.string.ledger_delete_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    state.deleteMany(picked.toList())
                    // 2026-10-03：事后那条「已删掉 N 条」的 Toast **撤了** ——
                    // 刚弹的确认框本身就是回话，再飘一条是重复；
                    // 而列表少了几条、多选栏收回去，眼睛看得见。
                    confirmDelete = false
                    selecting = false
                    picked.clear()
                }) {
                    Text(AppCtx.s(R.string.common_delete_2), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(AppCtx.s(R.string.common_never_mind)) }
            },
        )
    }

    // ④.1 单条删除：**一样先问一句**（2026-10-03 加）
    //     逸风「每个删除操作都要弹这个弹窗确认一遍」—— 以前卡片「⋮」里点删除是直接删的，
    //     只有批量那条路会问。现在三条路（单条 / 批量 / 清示例）统一。
    // ⚠️ 归档 / 放回**不弹**（逸风定：可逆的东西别多拦一步），那两处保留 Toast 当回话。
    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(AppCtx.s(R.string.ledger_delete_confirm, 1)) },
            text = { Text(AppCtx.s(R.string.ledger_delete_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    state.delete(target.id)
                    pendingDelete = null
                }) {
                    Text(AppCtx.s(R.string.common_delete_2), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(AppCtx.s(R.string.common_never_mind)) }
            },
        )
    }

    // ①.1 清掉那两条示例：**也是删数据**，一样先问一句（2026-10-03 加）
    if (confirmClearSamples) {
        AlertDialog(
            onDismissRequest = { confirmClearSamples = false },
            title = { Text(AppCtx.s(R.string.ledger_clear_samples_confirm, demos.size)) },
            text = { Text(AppCtx.s(R.string.ledger_delete_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    state.clearDemo()
                    confirmClearSamples = false
                }) {
                    Text(AppCtx.s(R.string.common_delete_2), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearSamples = false }) { Text(AppCtx.s(R.string.common_never_mind)) }
            },
        )
    }

    // ② 分享这一单（2026-10-01 加）：生成一张卡 → 系统分享面板
    shareItem?.let { item ->
        SingleShareDialog(
            item = item,
            mode = state.appMode,
            onDismiss = { shareItem = null },
            onShare = { bmp ->
                val intent = sharePng(ctx, bmp, "约稿账本-${item.artist.ifBlank { "这一单" }}.png")
                if (intent == null) {
                    toastNow(ctx, AppCtx.s(R.string.common_share_failed))
                } else {
                    ctx.startActivity(Intent.createChooser(intent, AppCtx.s(R.string.ledger_share_order)))
                }
                shareItem = null
            },
        )
    }

    if (creating) {
        EditSheet(
            original = null,
            mode = state.appMode,
            onDismiss = { creating = false },
            onSave = { state.upsert(it); creating = false },
            onAddPhoto = { state.addPhoto(it) },
            photoPath = { state.photoFile(it).absolutePath },
        )
    }

    editing?.let { item ->
        EditSheet(
            original = item,
            mode = state.appMode,
            onDismiss = { editing = null },
            onAddPhoto = { state.addPhoto(it) },
            photoPath = { state.photoFile(it).absolutePath },
            onSave = { new ->
                // ② 在表单里刚改成「已交付」→ 来一下小庆祝
                if (new.status == CommissionStatus.DELIVERED && item.status != CommissionStatus.DELIVERED) {
                    celebrate = true
                }
                state.upsert(new)
                editing = null
            },
        )
    }

    // ⑤ 催尾款 / 催交稿话术
    nudgeItem?.let { item ->
        NudgeSheet(
            c = item,
            mode = state.appMode,
            onDismiss = { nudgeItem = null },
            onCopied = { msg -> nudgeItem = null; toastNow(ctx, msg) },
        )
    }
}

@Composable
private fun HeroCell(label: String, value: String, modifier: Modifier) {
    val w = androidx.compose.ui.graphics.Color.White
    Column(modifier) {
        Text(label, fontSize = 11.sp, color = w.copy(alpha = 0.8f))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = w)
    }
}

/** 排序：一个圆形按钮 + 下拉，当前选中的打勾 */
@Composable
private fun SortButton(sort: SortMode, onPick: (SortMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        CircleIconButton(Icons.Filled.SwapVert, sort.label) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortMode.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.label, fontSize = 14.sp) },
                    leadingIcon = {
                        if (s == sort) Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp))
                        else Spacer(Modifier.width(18.dp))
                    },
                    onClick = { onPick(s); open = false },
                )
            }
        }
    }
}

/**
 * 进度条左边/右边那颗小圆箭头（2026-10-01 加）。
 *
 * `enabled = false` 时不响应点击、颜色压淡 —— **但位置照样占着**，
 * 这样同一张卡片的箭头永远在同一个地方，闭着眼也不会点错。
 */
@Composable
private fun StageArrow(
    forward: Boolean,
    enabled: Boolean,
    tint: Color,
    desc: String,
    onClick: () -> Unit,
) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val fg = when {
        !enabled -> dim.copy(alpha = 0.45f)
        else -> tint
    }
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(if (enabled) tint.copy(alpha = 0.14f) else dim.copy(alpha = 0.07f))
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (forward) Icons.Filled.ArrowForward else Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = desc,
            tint = fg,
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
private fun CommissionCard(
    item: Commission,
    mode: AppMode,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onAdvance: () -> Unit,
    onRetreat: () -> Unit,
    onNudge: () -> Unit,
    onShare: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    // 推进的目标（最后一档没有下一档）
    val nextStatus = CommissionStatus.entries.getOrNull(item.status.ordinal + 1)
    SoftCard(modifier.fillMaxWidth(), onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarBubble(item.artist.ifBlank { "？" }, size = 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.artist.ifBlank { Terms.otherBlank(mode) }, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(
                    item.title.ifBlank { AppCtx.s(R.string.common_no_content) },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            StatusChip(item.status, mode = mode)
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.MoreHoriz,
                        contentDescription = AppCtx.s(R.string.common_more),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    // ⑤ 催尾款 / 催交稿话术（2026-09-30 第三批）
                    DropdownMenuItem(
                        text = { Text(nudgeTitle(mode), fontSize = 14.sp) },
                        onClick = { menu = false; onNudge() },
                    )
                    // ② 分享这一单（2026-10-01 加）
                    // ⚠️ **不给图标** —— 这一列菜单里别的项都没有 leadingIcon，
                    //    只有它挂一个，看着不齐（逸风 2026-10-02：「太不和谐了」）。
                    DropdownMenuItem(
                        text = { Text(AppCtx.s(R.string.ledger_share_order), fontSize = 14.sp) },
                        onClick = { menu = false; onShare() },
                    )
                    DropdownMenuItem(
                        text = { Text(if (item.archived) AppCtx.s(R.string.ledger_unarchive) else AppCtx.s(R.string.common_archive), fontSize = 14.sp) },
                        onClick = { menu = false; onArchive() },
                    )
                    DropdownMenuItem(
                        text = { Text(AppCtx.s(R.string.common_delete_2), color = MaterialTheme.colorScheme.error, fontSize = 14.sp) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // ③ 推进进度 / **退回一档**（2026-10-01 加）
            //
            // ⚠️ 原计划做成「左滑推进」，实测**被这一页的左右切页抢掉了横向手势**
            //    （账本页是 HorizontalPager，横向拖动先到它那儿）。
            //    所以改成一对小箭头：左边退回、右边推进，不跟切页打架。
            //
            //    逸风 10/1：「建议出个推进进度返回的箭头，不然太容易误触了」
            //    —— 关键是**两颗一直都在**（没有上一档/下一档时灰着、点不动），
            //    位置固定才不会点错；只有一颗、还忽隐忽现的时候最容易按岔。
            StageArrow(
                forward = false,
                enabled = item.status.ordinal > 0,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                desc = AppCtx.s(R.string.ledger_revert_one),
                onClick = onRetreat,
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) { StageBar(item.status) }
            Spacer(Modifier.width(8.dp))
            StageArrow(
                forward = true,
                enabled = nextStatus != null,
                tint = statusColor(item.status),
                desc = if (nextStatus != null) AppCtx.s(R.string.ledger_advance_to, Terms.status(nextStatus, mode)) else AppCtx.s(R.string.ledger_last_stage),
                onClick = onAdvance,
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 金额当主角（加粗放大），已付降成小字 —— 卡片第一眼先看到「多少钱」
            Text(
                money(item.total),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
            )
            Spacer(Modifier.width(7.dp))
            Text(
                "${Terms.paid(mode)} ${money(item.deposit)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 有参考图就在这一行尾巴上挂个小角标（没图什么都不画，布局一点不动）
            if (item.photos.isNotEmpty()) {
                Spacer(Modifier.width(9.dp))
                PhotoBadge(item.photos.size)
            }
            Spacer(Modifier.weight(1f))
            val dl = deadlineText(item)
            if (dl != null) {
                Text(
                    dl.first,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (dl.second) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(dayText(item.dateMillis), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (item.note.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            // 用图标代替 emoji：emoji 是彩色位图，跟整体线性圆角风格不搭
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.StickyNote2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(item.note, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// MARK: - ④ 多选（2026-10-01 加）
//
// 单子攒多了，一条条进菜单去归档 / 删除太烦。
// 路径：分段控件那排点「清单」图标进多选 → 点卡片打勾 → 底下浮出操作栏。
// 多选时**不显示进度箭头、也不弹编辑表单** —— 免得打勾的手势跟它们打架。

@Composable
private fun PickCard(
    item: Commission,
    mode: AppMode,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    SoftCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 自己画那颗勾：Material 自带的 Checkbox 颜色跟这套设计不太搭
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (checked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = AppCtx.s(R.string.common_selected),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            AvatarBubble(item.artist.ifBlank { "？" }, size = 38.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.artist.ifBlank { Terms.otherBlank(mode) },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.title.ifBlank { AppCtx.s(R.string.common_no_content) },
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(money(item.total), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** 多选时底下浮出来的那条操作栏 */
@Composable
private fun BatchBar(
    count: Int,
    archivedView: Boolean,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.surface,
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(AppCtx.s(R.string.common_selected_count, count), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onArchive, enabled = count > 0) {
                Icon(
                    if (archivedView) Icons.AutoMirrored.Filled.ArrowBack else Icons.Filled.Archive,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(if (archivedView) AppCtx.s(R.string.ledger_put_back) else AppCtx.s(R.string.common_archive), fontSize = 13.sp)
            }
            TextButton(onClick = onDelete, enabled = count > 0) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = null,
                    tint = cs.error,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(AppCtx.s(R.string.common_delete_2), fontSize = 13.sp, color = cs.error)
            }
        }
    }
}

// MARK: - ② 分享这一单（2026-10-01 加）
//
// 跟「年度报告」那张图一个路子：图片在手机上现画现出，不走网络、不要存储权限。

/**
 * 「分享这一单」：先把这张卡摆出来看一眼，点一下才生成图片去分享。
 */
@Composable
fun SingleShareDialog(
    item: Commission,
    mode: AppMode,
    onDismiss: () -> Unit,
    onShare: (android.graphics.Bitmap) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val gl = rememberGraphicsLayer()
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        // 默认宽度太窄，装不下那张 300dp 的卡 —— 自己控宽
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .widthIn(max = 340.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(cs.surface)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 这块就是要拍下来的内容
            Box(Modifier.captureTo(gl)) {
                CommissionShareCard(item, mode)
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    scope.launch {
                        runCatching { gl.toImageBitmap().asAndroidBitmap() }
                            .onSuccess { onShare(it) }
                            .onFailure { toastNow(context, AppCtx.s(R.string.stats_image_failed)) }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = cs.primary),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(AppCtx.s(R.string.common_share_2), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onDismiss) { Text(AppCtx.s(R.string.common_done), fontSize = 13.sp) }
            Text(
                AppCtx.s(R.string.common_image_local_note),
                fontSize = 11.sp,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

/** 那一张要发出去的卡：跟 iOS 版长得一模一样（冰蓝渐变 + 白字） */
@Composable
fun CommissionShareCard(item: Commission, mode: AppMode) {
    val w = Color.White
    Column(
        Modifier
            .width(300.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(IceBlueLight, IceBlue, Color(0xFF14406F))))
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(AppCtx.s(R.string.ledger_title), fontSize = 13.sp, color = w.copy(alpha = 0.85f))
            Spacer(Modifier.weight(1f))
            Text(dayText(item.dateMillis), fontSize = 12.sp, color = w.copy(alpha = 0.85f))
        }

        Spacer(Modifier.height(20.dp))
        Text(
            item.artist.ifBlank { Terms.otherBlank(mode) },
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = w,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            item.title.ifBlank { AppCtx.s(R.string.common_no_content) },
            fontSize = 13.sp,
            color = w.copy(alpha = 0.88f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(money(item.total), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = w)
            Spacer(Modifier.width(6.dp))
            Text(
                AppCtx.s(R.string.ledger_total_price),
                fontSize = 11.sp,
                color = w.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Text(
            "${Terms.paid(mode)} ${money(item.deposit)}  ·  ${Terms.unpaid(mode)} ${money(item.unpaid)}",
            fontSize = 12.sp,
            color = w.copy(alpha = 0.88f),
        )

        Spacer(Modifier.height(16.dp))
        // 进度条：四小格，走到哪亮到哪（跟页面上那条一模一样）
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(CommissionStatus.STAGE_COUNT) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (i <= item.status.stageIndex) w else w.copy(alpha = 0.25f))
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            Terms.status(item.status, mode),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = w,
        )

        item.deadlineMillis?.let {
            Spacer(Modifier.height(8.dp))
            Text(AppCtx.s(R.string.ledger_deadline, dayText(it)), fontSize = 11.5.sp, color = w.copy(alpha = 0.75f))
        }
        if (item.note.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                item.note,
                fontSize = 11.5.sp,
                color = w.copy(alpha = 0.78f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(AppCtx.s(R.string.ledger_slogan), fontSize = 11.sp, color = w.copy(alpha = 0.68f))
            Spacer(Modifier.weight(1f))
            Text(AppCtx.s(R.string.common_author), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = w.copy(alpha = 0.68f))
        }
    }
}

// MARK: - 新增 / 编辑

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSheet(
    original: Commission?,
    mode: AppMode,
    onDismiss: () -> Unit,
    onSave: (Commission) -> Unit,
    /**
     * 收下一张图（从系统图片选择器给的那个 uri）→ 返回落盘后的文件名。
     * 默认实现返回 null（= 收不了），这样别处想单独用这个表单也不会崩。
     */
    onAddPhoto: (android.net.Uri) -> String? = { null },
    /** 文件名 → 磁盘绝对路径（给缩略图解码用） */
    photoPath: (String) -> String = { "" },
) {

    var artist by remember { mutableStateOf(original?.artist ?: "") }
    var title by remember { mutableStateOf(original?.title ?: "") }
    var total by remember { mutableStateOf(original?.total?.let { trimNum(it) } ?: "") }
    var deposit by remember { mutableStateOf(original?.deposit?.let { trimNum(it) } ?: "") }
    var status by remember { mutableStateOf(original?.status ?: CommissionStatus.QUOTING) }
    var dateText by remember { mutableStateOf(dayText(original?.dateMillis ?: System.currentTimeMillis())) }
    var hasDeadline by remember { mutableStateOf(original?.deadlineMillis != null) }
    var deadlineStr by remember { mutableStateOf(original?.deadlineMillis?.let { dayText(it) } ?: "") }
    var note by remember { mutableStateOf(original?.note ?: "") }

    // 参考图（2026-10-03 加）
    //
    // ⚠️ 这里存的是**文件名**，图在本体收下的那一刻就已经落盘了（见 [Store.savePhotoFrom]）。
    //    「先收图、再点保存」这个顺序是故意的：表单里能立刻看到缩略图（不然点了加图
    //    什么都没发生，像坏了）；代价是中途取消会留下一张没人引用的图 ——
    //    那些孤儿图会在下一次存盘时被 [AppState.persist] 顺手清掉。
    var photos by remember { mutableStateOf(original?.photos ?: emptyList()) }
    var viewerAt by remember { mutableStateOf<Int?>(null) }
    var picking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 系统自带的图片选择器：**不要相册权限**，用户挑哪张才把哪张给我（见 Photos 顶部注释）。
    // 一次能挑好几张（上限就是 6 张那个数），不用一张一张加。
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(Photos.MAX_COUNT)
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        picking = true
        scope.launch {
            // 压缩是 CPU 活，挪到 IO 线程去 —— 6 张图在主线程解会把表单卡住
            val added = withContext(Dispatchers.IO) {
                uris.take(Photos.MAX_COUNT - photos.size).mapNotNull { onAddPhoto(it) }
            }
            photos = photos + added.map { Photo(it) }
            picking = false
        }
    }

    val fieldShape = RoundedCornerShape(14.dp)

    // 🔧 2026-09-30 修：逸风报「设置截止日期的时候 Sheet 会自动下滑，看不见截止日期」。
    //
    // 两个原因叠在一起：
    //   ① `ModalBottomSheet` 默认**允许停在半截**（partially expanded）。这张单子有十来行，
    //      半截状态下底部那几行（截止日期、备注、按钮）正好在屏幕外面；
    //      一交互它还会自己回落到半截位置 —— 看着就像「Sheet 自己往下滑」。
    //      → `skipPartiallyExpanded = true`：一弹出来就占满，不再有半截那个位置。
    //   ② 整列**没有滚动**，被挡住的字段是真够不着。
    //      → `verticalScroll`：够不着就滑。
    //      → `imePadding`：键盘顶上来时把内容抬起来，别让键盘盖住正填的那一格。
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (original == null) {
                    if (mode == AppMode.BUYER) AppCtx.s(R.string.ledger_new_commission) else AppCtx.s(R.string.ledger_new_gig)
                } else {
                    if (mode == AppMode.BUYER) AppCtx.s(R.string.ledger_edit_commission) else AppCtx.s(R.string.artist_edit_commission)
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            OutlinedTextField(artist, { artist = it }, label = { Text(Terms.other(mode)) }, singleLine = true, shape = fieldShape, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(title, { title = it }, label = { Text(AppCtx.s(R.string.ledger_content_hint)) }, singleLine = true, shape = fieldShape, modifier = Modifier.fillMaxWidth())

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    total, { total = it }, label = { Text(AppCtx.s(R.string.ledger_total_price)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = fieldShape,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    deposit, { deposit = it }, label = { Text(Terms.paid(mode)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = fieldShape,
                    modifier = Modifier.weight(1f),
                )
            }

            Text(AppCtx.s(R.string.ledger_status), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // 同账本页：一排。这里只有 4 个按钮，更宽裕
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CommissionStatus.entries.forEach { s ->
                    SlimChip(Terms.status(s, mode), status == s) { status = s }
                }
            }

            OutlinedTextField(
                dateText, { dateText = it }, label = { Text(AppCtx.s(R.string.ledger_order_date)) },
                placeholder = { Text("yyyy-MM-dd") },
                singleLine = true, shape = fieldShape, modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(checked = hasDeadline, onCheckedChange = {
                    hasDeadline = it
                    if (it && deadlineStr.isBlank()) {
                        deadlineStr = dayText(System.currentTimeMillis() + 7L * 86_400_000L)
                    }
                })
                Text(AppCtx.s(R.string.ledger_set_deadline), fontSize = 14.sp)
            }
            if (hasDeadline) {
                OutlinedTextField(
                    deadlineStr, { deadlineStr = it }, label = { Text(AppCtx.s(R.string.ledger_deadline_2)) },
                    placeholder = { Text("yyyy-MM-dd") },
                    singleLine = true, shape = fieldShape, modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                note, { note = it }, label = { Text(AppCtx.s(R.string.ledger_note_hint)) },
                shape = fieldShape,
                modifier = Modifier.fillMaxWidth(),
            )

            // 参考图（2026-10-03 加，逸风要的「买家约稿的时候可以添加图片」）
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        AppCtx.s(R.string.photos_section),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    if (photos.isNotEmpty()) {
                        Text(
                            AppCtx.s(R.string.photos_count, photos.size, Photos.MAX_COUNT),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
                PhotoStrip(
                    items = photos,
                    pathFor = photoPath,
                    onAdd = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onRemove = { name -> photos = photos.filterNot { it.name == name } },
                    onOpen = { i -> viewerAt = i },
                )
                PhotoHint(
                    if (picking) AppCtx.s(R.string.photos_working)
                    else if (photos.size >= Photos.MAX_COUNT) AppCtx.s(R.string.photos_max_reached, Photos.MAX_COUNT)
                    else AppCtx.s(R.string.photos_hint)
                )
            }

            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(AppCtx.s(R.string.common_cancel), fontSize = 15.sp) }
                Button(
                    onClick = {
                        val c = (original ?: Commission()).copy(
                            artist = artist.trim(),
                            title = title.trim(),
                            total = total.toDoubleOrNull() ?: 0.0,
                            deposit = deposit.toDoubleOrNull() ?: 0.0,
                            status = status,
                            dateMillis = parseDay(dateText) ?: (original?.dateMillis ?: System.currentTimeMillis()),
                            deadlineMillis = if (hasDeadline) parseDay(deadlineStr) else null,
                            note = note.trim(),
                            // 新单归当前模式；编辑老单时保持它原来的归属
                            mode = original?.mode ?: mode.key,
                            photos = photos,
                        )
                        onSave(c)
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(2f).height(48.dp),
                ) { Text(AppCtx.s(R.string.common_save), fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
            }
        }
    }

    // 看大图（点缩略图进来，整屏、能左右翻、底下能给这张写说明）
    viewerAt?.let { start ->
        PhotoViewer(
            items = photos,
            startIndex = start,
            pathFor = photoPath,
            onDismiss = { viewerAt = null },

        )
    }
}

/** 300.0 → "300"，300.5 → "300.5" */
fun trimNum(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

/** "2026-09-29" → 毫秒；看不懂就返回 null */
fun parseDay(text: String): Long? {
    val t = text.trim().replace("/", "-").replace(".", "-")
    val m = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$").find(t) ?: return null
    val (y, mo, d) = m.destructured
    val cal = Calendar.getInstance()
    cal.set(y.toInt(), mo.toInt() - 1, d.toInt(), 0, 0, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}
