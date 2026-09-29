package com.yifeng.commissionbook

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.SwapVert
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

private enum class SortMode(val label: String) { ByDate("按下单日期"), ByMoney("按金额"), ByDeadline("按截止日") }

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
    // 列表滚动位置 → 喂给大标题做「滚上去就缩小」
    val listState = rememberLazyListState()

    // 先按「归档 / 进行中」分家，再套筛选和搜索
    var pool = state.items.filter { it.archived == showArchived }
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
                title = "约稿账本",
                collapse = rememberCollapseFraction(listState),
                caption = "共 ${state.items.size} 单 · 进行中 ${state.items.count { !it.archived }}",
            ) {
                CircleIconButton(Icons.Filled.Search, "搜索") {
                    searching = !searching
                    if (!searching) query = ""
                }
            }

            // 搜索框：淡入 + 向下撑开，不要「刷的一下」蹦出来（逸风 2026-09-29 的审美要求）
            AnimatedVisibility(
                visible = searching,
                enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(180)),
            ) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜画师 / 内容 / 备注", fontSize = 14.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }

            // ② 合计（只算「进行中」的 —— 归档的不算，跟 iOS 版一致）
            // 切「已归档」时它是收走 / 放回，不是硬切
            AnimatedVisibility(
                visible = !showArchived,
                enter = fadeIn(tween(180)) + expandVertically(tween(240)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(200)),
            ) {
                HeroCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("进行中合计", fontSize = 12.sp, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f))
                    Spacer(Modifier.height(4.dp))
                    Text(money(state.activeTotal), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        HeroCell("已付", money(state.activePaid), Modifier.weight(1f))
                        HeroDivider()
                        HeroCell("未付", money((state.activeTotal - state.activePaid).coerceAtLeast(0.0)), Modifier.weight(1f))
                        HeroDivider()
                        HeroCell("单数", "${state.items.count { !it.archived }} 单", Modifier.weight(1f))
                    }
                }
            }

            // ③ 进行中 / 已归档（iOS 分段控件）+ 排序
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SegmentedControl(
                    options = listOf("进行中", "已归档"),
                    index = if (showArchived) 1 else 0,
                    modifier = Modifier.weight(1f),
                ) { i -> showArchived = i == 1 }
                SortButton(sort) { sort = it }
            }

            // ④ 进度筛选
            // ⚠️ 必须是**一排**（逸风 2026-09-29：「还是一排比较好看」）。
            // 外面那层 horizontalScroll 是兜底：真遇上比 320dp 还窄的怪屏能滑，而不是被硬切。
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SlimChip("全部", filter == null) { filter = null }
                CommissionStatus.entries.forEach { s ->
                    SlimChip(s.label, filter == s) { filter = if (filter == s) null else s }
                }
            }

            // ⑤ 列表
            if (shown.isEmpty()) {
                EmptyHint(
                    when {
                        query.isNotBlank() -> "没搜到诶"
                        showArchived -> "归档箱是空的"
                        else -> "还没有约稿"
                    }
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    items(shown, key = { it.id }) { item ->
                        CommissionCard(
                            item = item,
                            // 新增 / 删除 / 归档时，卡片是「滑进去 / 淡出来」而不是瞬间闪现
                            modifier = Modifier.animateItem(),
                            onEdit = { editing = item },
                            onArchive = { state.toggleArchive(item.id) },
                            onDelete = { state.delete(item.id) },
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { creating = true },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp)
                // 带一点蓝色的投影 —— 按钮像是「浮」在页面上，而不是贴上去的
                .shadow(
                    elevation = 14.dp,
                    shape = RoundedCornerShape(20.dp),
                    clip = false,
                    ambientColor = androidx.compose.ui.graphics.Color(0x332E74CA),
                    spotColor = androidx.compose.ui.graphics.Color(0x4D2E74CA),
                ),
        ) {
            Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Add, contentDescription = "新增")
            }
        }
    }

    if (creating) {
        EditSheet(
            original = null,
            onDismiss = { creating = false },
            onSave = { state.upsert(it); creating = false },
        )
    }
    editing?.let { item ->
        EditSheet(
            original = item,
            onDismiss = { editing = null },
            onSave = { state.upsert(it); editing = null },
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

@Composable
private fun CommissionCard(
    item: Commission,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    SoftCard(modifier.fillMaxWidth(), onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarBubble(item.artist.ifBlank { "？" }, size = 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.artist.ifBlank { "（没写画师）" }, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(
                    item.title.ifBlank { "（没写内容）" },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            StatusChip(item.status)
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.MoreHoriz,
                        contentDescription = "更多",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (item.archived) "取消归档" else "归档", fontSize = 14.sp) },
                        onClick = { menu = false; onArchive() },
                    )
                    DropdownMenuItem(
                        text = { Text("删除", color = MaterialTheme.colorScheme.error, fontSize = 14.sp) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        StageBar(item.status)

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
                "已付 ${money(item.deposit)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

// MARK: - 新增 / 编辑

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSheet(original: Commission?, onDismiss: () -> Unit, onSave: (Commission) -> Unit) {

    var artist by remember { mutableStateOf(original?.artist ?: "") }
    var title by remember { mutableStateOf(original?.title ?: "") }
    var total by remember { mutableStateOf(original?.total?.let { trimNum(it) } ?: "") }
    var deposit by remember { mutableStateOf(original?.deposit?.let { trimNum(it) } ?: "") }
    var status by remember { mutableStateOf(original?.status ?: CommissionStatus.QUOTING) }
    var dateText by remember { mutableStateOf(dayText(original?.dateMillis ?: System.currentTimeMillis())) }
    var hasDeadline by remember { mutableStateOf(original?.deadlineMillis != null) }
    var deadlineStr by remember { mutableStateOf(original?.deadlineMillis?.let { dayText(it) } ?: "") }
    var note by remember { mutableStateOf(original?.note ?: "") }

    val fieldShape = RoundedCornerShape(14.dp)

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (original == null) "新增约稿" else "编辑约稿",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            OutlinedTextField(artist, { artist = it }, label = { Text("画师") }, singleLine = true, shape = fieldShape, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(title, { title = it }, label = { Text("内容（比如全身像）") }, singleLine = true, shape = fieldShape, modifier = Modifier.fillMaxWidth())

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    total, { total = it }, label = { Text("总价") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = fieldShape,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    deposit, { deposit = it }, label = { Text("已付") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = fieldShape,
                    modifier = Modifier.weight(1f),
                )
            }

            Text("进度", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // 同账本页：一排。这里只有 4 个按钮，更宽裕
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CommissionStatus.entries.forEach { s ->
                    SlimChip(s.label, status == s) { status = s }
                }
            }

            OutlinedTextField(
                dateText, { dateText = it }, label = { Text("下单日期") },
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
                Text("设置截止日期", fontSize = 14.sp)
            }
            if (hasDeadline) {
                OutlinedTextField(
                    deadlineStr, { deadlineStr = it }, label = { Text("截止日期") },
                    placeholder = { Text("yyyy-MM-dd") },
                    singleLine = true, shape = fieldShape, modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                note, { note = it }, label = { Text("备注（随手记一句）") },
                shape = fieldShape,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消", fontSize = 15.sp) }
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
                        )
                        onSave(c)
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(2f).height(48.dp),
                ) { Text("保存", fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
            }
        }
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
