package com.yifeng.commissionbook

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ContactPhone
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 画师档案。
 *
 * 跟 iOS 版一样：**不新增数据表**，就是「按名字把约稿归堆」，另外配一本小册子记备注。
 * 这样数据格式零改动 —— 两边的备份继续通用。
 */
private data class ArtistGroup(
    val name: String,
    val list: List<Commission>,
    val note: String,
    /**
     * 图片备注（2026-10-11 加，**仅安卓**）：这个画师挂的图文件名（按添加顺序）。
     * 卡片上最多画 3 张缩略图，多的折成「+N」。
     */
    val photos: List<String>,
    /**
     * 分类（2026-10-13 加）。卡片上**只给「不是常稿」的挂标签** ——
     * 常稿是绝大多数、也是默认那一档，每张卡都贴一个「常稿」只是噪音。
     * ⚠️ 只在买家模式有意义：画师模式下这一页列的是「客户」。
     */
    val categoryName: String,
    val showCategoryBadge: Boolean,
) {
    val totalMoney: Double get() = list.sumOf { it.total }
    val paidMoney: Double get() = list.sumOf { it.deposit }
    val activeCount: Int get() = list.count { !it.archived && it.status != CommissionStatus.DELIVERED }
}

/**
 * 画师页排序（2026-10-12 加）—— 照账本页的 [SortMode] 那一套抄的。
 *
 * ⚠️ `labelRes` 存的是**资源 ID**，标签走计算属性现取：
 *    绝不能在构造参数里写 `ByCount(AppCtx.s(...))` —— enum 常量在**类初始化时**
 *    只求值一次，那会儿 AppCtx 还没 init（拿到空串），而且切了语言也不会更新。
 */
private enum class ArtistSort(val labelRes: Int) {
    /** 按约稿次数（多的在前）—— **默认**，跟这一页原来写死的顺序一致 */
    ByCount(R.string.artist_sort_by_count),
    /** 按最近约稿（日期新的在前） */
    ByRecent(R.string.artist_sort_by_recent),
    /** 按名字 */
    ByName(R.string.artist_sort_by_name);

    val label: String get() = AppCtx.s(labelRes)
}

/**
 * 画师页的**一段**（2026-10-13 加）。
 *
 * `id` / `title` 都是 null = 「不分段」那种画（画师模式下整条平列表）。
 * ⚠️ `id` 是给「收起来」记状态用的 —— 存 **id** 不存名字，因为名字他自己能改。
 */
private data class ArtistSection(
    val id: String?,
    val title: String?,
    val items: List<ArtistGroup>,
)

// 画师分类的模型（`CategoryDef` / 预置两档 / 增删改）在 `Models.kt` + `AppState.kt` ——
// 2026-10-13 那次「能新增 / 改名 / 删除」之后，它已经不是写死的枚举了。

@Composable
fun ArtistsScreen(modifier: Modifier, state: AppState) {

    // 排序（2026-10-12 加，照账本页那一套）：只改**列表显示顺序**，不碰任何数据。
    // ⚠️ 默认必须是「按约稿次数」= 这一页原来写死的顺序（多的在前），
    //    否则老用户一打开会发现画师的先后莫名其妙变了。
    var sort by remember { mutableStateOf(ArtistSort.ByCount) }

    // 分段（2026-10-13 加）—— 逸风：「在画师页面也加上分类，不只是详情页」→
    // 第一版做成了顶上那排筛选胶囊，他看了说「不是这样」，改成
    // **「把筛选胶囊取消，按分类分段显示」**。
    //
    // 所以现在是：列表**一档一段**（段标题 = 分类名），顺序 = 分类清单的顺序。
    // ⚠️ 分段**不动数据**，只是把同一份 `groups` 按分类切开摆。

    val groups = state.modeItems
        .groupBy { it.artist }
        .map { (name, list) ->
            ArtistGroup(
                // 显示名跟着模式走（买家「画师」/ 画师「客户」）；
                // ⚠️ 但 notes 的 key 用的还是 it0()，保持不变 —— 别把存过的联系方式读丢
                name = name.ifBlank { Terms.otherBlank(state.appMode) },
                // 卡内那摞（卡片上取前 3 条画出来）也交给排序管：
                // 按名字 → 单子按标题升序；按次数 / 按最近 → 仍是最近那单在前。
                list = when (sort) {
                    ArtistSort.ByName -> list.sortedBy { it.title }
                    ArtistSort.ByCount, ArtistSort.ByRecent -> list.sortedByDescending { it.dateMillis }
                },
                note = state.notes[it0(name)] ?: "",
                photos = state.artistPhotosOf(it0(name)),
                // 分类（2026-10-13 加）：跟 notes 同一个 key（都是 it0(name)）。
                // ⚠️ 卡片上的分类**小标签同日就撤了** —— 改成**分段显示**之后
                //    段标题已经把分类说清了，卡片再挂一个就是同一句话说两遍。
                //    （要恢复：`state.showsCategoryBadge` 还在，把它接回来就行。）
                categoryName = state.categoryNameOf(it0(name)),
                showCategoryBadge = false,
            )
        }
        // ⚠️ 这一页的先后由**两处**排序叠着决定：上面卡内那一摞（ArtistGroup.list）+
        //    这里卡片本身的先后。**两处都得过 `sort`**，别只改一边 ——
        //    留一边写死的话，就会出现「排序按钮点了但列表没动」的怪现象。
        .let { gs ->
            when (sort) {
                ArtistSort.ByCount -> gs.sortedByDescending { it.list.size }
                ArtistSort.ByRecent -> gs.sortedByDescending { g -> g.list.maxOfOrNull { it.dateMillis } ?: 0L }
                ArtistSort.ByName -> gs.sortedBy { it.name }
            }
        }

    // 分段（2026-10-13 加）：买家模式**一档一段**（`id`/`title` = 分类；都是 null = 不分段那个模式），
    // 顺序 = 分类清单的顺序（常稿在最前，他自己加的在后面）。
    // ⚠️ **空档不画** —— 一个光标题下面啥也没有，比不显示更乱。
    // ⚠️ 只在买家模式分段：画师模式下列的是「客户」，常稿 / 模版对他没意义。
    // ⚠️ 每个人**必然**落进某一段：`categoryIdOf` 兜底成 REGULAR，而 REGULAR 一定在清单里
    //    （见 AppState.categoryDefs 里那道保险）。最后一行的 `ifEmpty` 是防「万一」——
    //    宁可不分段，也绝不能摆出一张空列表给他。
    val bySection: List<ArtistSection> =
        if (state.appMode != AppMode.BUYER) {
            listOf(ArtistSection(null, null, groups))
        } else {
            state.categoryDefs.mapNotNull { def ->
                val list = groups.filter { state.categoryIdOf(it0(it.name)) == def.id }
                if (list.isEmpty()) null else ArtistSection(def.id, def.name, list)
            }.ifEmpty { listOf(ArtistSection(null, null, groups)) }
        }

    // **收起来的那几档**（2026-10-13 加）：点一下分类标题就把这一档收掉，
    // 抄的是 TestFlight「以前测试过」那种感觉。
    // ⚠️ 存的是**分类 id**（名字他自己能改，id 才稳）。
    // ⚠️ 跟排序一个规矩：`remember` **不落盘** —— 重新打开 App 一律**全展开**，
    //    老用户升级上来看到的东西跟以前一模一样，不会「我的人呢」。
    var collapsed by remember { mutableStateOf(setOf<String>()) }

    var editing by remember { mutableStateOf<ArtistGroup?>(null) }
    // 改名字（2026-10-08 加）：跟备注分开一个弹窗 —— 两个输入框塞一个框里会看不清
    var renaming by remember { mutableStateOf<ArtistGroup?>(null) }
    var renameText by remember { mutableStateOf("") }
    val context = LocalContext.current

    // 换头像（2026-10-08 加）：走系统「挑一张图」——
    // ⚠️ **不要相册权限**：ACTION_GET_CONTENT（安卓 13+ 会直接给系统的照片选择器）
    //    只把你亲手挑中的那一张交给我们，跟参考图那条路一个道理。
    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val g = editing
        if (uri != null && g != null) {
            Avatars.setFromUri(context, it0(g.name), uri)
        }
    }

    // 加图片备注（2026-10-11 加，**仅安卓**）：走**系统图片选择器**（Photo Picker）。
    // ⚠️ 跟参考图一条路：不要相册权限、App 看不到没选的那些图（隐私政策靠这个立着）。
    // 图是**即点即存**的：选完立刻落盘 + 记名字，不跟着弹窗那颗「保存」走 ——
    // 头像本来就是这个规矩（改完立即生效），两处一致。
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val g = editing
        if (uri != null && g != null) {
            if (!state.addArtistPhoto(it0(g.name), uri)) {
                toastNow(context, AppCtx.s(R.string.common_save_failed))
            }
        }
    }

    val listState = rememberLazyListState()

    ScreenSurface(modifier) {
        Column(Modifier.fillMaxSize()) {
            CollapsibleLargeTitle(
                title = Terms.other(state.appMode),
                collapse = rememberCollapseFraction(listState),
                caption = AppCtx.s(R.string.common_contact_hint, groups.size),
            ) {
                // 排序（2026-10-12 加）：跟账本页那颗一样的圆按钮 + 下拉，
                // 放在大标题右边（这一页顶栏就它一颗钮，位置跟账本页的搜索/新增一档）。
                ArtistSortButton(sort) { sort = it }
            }

            if (groups.isEmpty()) {
                EmptyHint(AppCtx.s(R.string.common_none_yet, Terms.other(state.appMode)))
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    // 段标题插在每段前面（买家模式才有；画师模式那段 `title` 是 null）。
                    // 2026-10-13 加：**点段标题能收起来**（抄 TestFlight「以前测试过」那种）。
                    // ⚠️ key 用**序号**不带分类名：分类名是他自己打的、**可能重名** ——
                    //    重名 → 两个 item 抢同一个 key → LazyColumn 会直接崩。
                    bySection.forEachIndexed { i, sec ->
                        if (sec.id != null && sec.title != null) {
                            item(key = "sec-$i") {
                                SectionHeaderRow(
                                    title = sec.title,
                                    count = sec.items.size,
                                    open = !collapsed.contains(sec.id),
                                    spaced = i != 0,     // 两段之间留点上间距（第一段不用）
                                ) {
                                    // 收 / 展
                                    collapsed = if (collapsed.contains(sec.id)) {
                                        collapsed - sec.id
                                    } else {
                                        collapsed + sec.id
                                    }
                                }
                            }
                        }
                        // 收着的那段：**一个人都不画**，就剩标题那一行
                        if (sec.id == null || !collapsed.contains(sec.id)) {
                            items(sec.items, key = { it.name }) { g ->
                                // 画师数变动时卡片滑动 / 淡入，不硬切 ——
                                // 收起 / 展开也是靠它，不然会「刷的一下」
                                ArtistCard(
                                    g = g,
                                    mode = state.appMode,
                                    pathFor = { state.artistPhotoFile(it).absolutePath },
                                    modifier = Modifier.animateItem(),
                                ) { editing = g }
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { g ->
        var text by remember(g.name) { mutableStateOf(state.notes[it0(g.name)] ?: "") }
        val hasAvatar = Avatars.base64For(it0(g.name)) != null
        // 图片备注（2026-10-11 加）：这一份读的是 Compose 状态，加/删一张当场重画
        val photos = state.artistPhotosOf(it0(g.name))
        // 点缩略图看大图：记下第几张（null = 没在看）
        var viewerAt by remember { mutableStateOf<Int?>(null) }
        // 删图先问一句（2026-10-11 深夜加）：记下要删哪张（null = 没在问）。
        // 逸风拍板「两边统一成弹确认」—— iOS 删图就是二次确认；删图不可逆，
        // 跟单子那边「删除先问一句」一个规矩（归档 / 放回那种可逆的才不弹）。
        var confirmRemove by remember { mutableStateOf<String?>(null) }
        // 分类（2026-10-13 加）：**这个画师归属哪一档**（存的是分类 id）。
        // 跟上面那个备注框一个规矩 —— **跟着「保存」走**（没点保存就取消 = 不生效）。
        // iOS 那边是「一选就存」；两端这点不一样，但各自都跟**同一页里旁边那个控件**保持一致。
        var categoryId by remember(g.name) { mutableStateOf(state.categoryIdOf(it0(g.name))) }
        // 分类的「新增 / 改名 / 删除 / 管理」（2026-10-13 加）：
        // ⚠️ 这几个是**全局配置**（改的是那份清单），跟当前这个画师无关 ——
        //    所以**当场生效**，不跟着底下那颗「保存」走。
        var catAdding by remember { mutableStateOf(false) }
        var catRenaming by remember { mutableStateOf<CategoryDef?>(null) }
        var catDeleting by remember { mutableStateOf<CategoryDef?>(null) }
        var catManaging by remember { mutableStateOf(false) }
        var catText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { editing = null },
            shape = RoundedCornerShape(24.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 头像（2026-10-08）：这里就是设头像的地方 —— 画大一点，改完当场看得清
                    AvatarBubble(g.name, size = 58.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(g.name, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { pickAvatar.launch("image/*") },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(32.dp),
                            ) { Text(AppCtx.s(R.string.avatar_change), fontSize = 13.sp) }

                            // 没设过就不显示「移除」—— 一个点了没反应的按钮比没有更烦
                            if (hasAvatar) {
                                TextButton(
                                    onClick = { Avatars.remove(it0(g.name)) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.height(32.dp),
                                ) {
                                    Text(
                                        AppCtx.s(R.string.avatar_remove),
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            text = {
                Column {
                    // 分类（2026-10-13 加）：常稿画师 / 模版画师。
                    // ⚠️ **只在买家模式显示** —— 他原话说的是「客户模式下的画师详情页」；
                    //    画师模式下这一页列的是**客户**，「常稿 / 模版」对他没意义。
                    //    （跟 iOS 端同一条规矩，两端必须一样。）
                    // 用房子里的 [SlimChip]（胶囊），别用原生 FilterChip ——
                    // 2026-09-29 视觉重做时就是特意从 FilterChip 换过来的。
                    if (state.appMode == AppMode.BUYER) {
                        // 标题行：左边标签、右边「管理」——改名 / 删除都从那儿进。
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                AppCtx.s(R.string.artist_category_label),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.weight(1f))
                            TextButton(
                                onClick = { catManaging = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(30.dp),
                            ) { Text(AppCtx.s(R.string.artist_category_manage), fontSize = 12.sp) }
                        }
                        Spacer(Modifier.height(6.dp))
                        // 一档一个胶囊，末尾再跟一个「＋ 新增」。
                        // ⚠️ 分类是**他自己加的、个数不定** → 横着排不下就横滑，
                        //    别挤成一团（保住「一排胶囊」那个观感）。
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            state.categoryDefs.forEach { def ->
                                SlimChip(
                                    text = def.name,
                                    selected = def.id == categoryId,
                                    fontSize = 13.sp,
                                ) { categoryId = def.id }
                            }
                            // 「＋ 新增」也做成同样的胶囊（房子里的观感统一）
                            SlimChip(
                                text = "＋ " + AppCtx.s(R.string.artist_category_add),
                                selected = false,
                                fontSize = 13.sp,
                            ) {
                                catText = ""
                                catAdding = true
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            AppCtx.s(R.string.artist_category_hint),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        )
                        Spacer(Modifier.height(16.dp))
                    }

                    Text(
                        AppCtx.s(R.string.common_note_contact),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text(AppCtx.s(R.string.common_write_something)) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // 图片备注（2026-10-11 加，**仅安卓**）：跟参考图表单里那条一模一样的
                    // 交互（缩略图右上角 ✕ 删、点开看大图、到 6 张就不显示「加图」）。
                    // 跟文字备注的分工：联系方式、忌讳那种**一句话**写这儿；
                    // 画风参考、聊天截图那种**一张图**挂这儿。
                    Spacer(Modifier.height(14.dp))
                    Text(
                        AppCtx.s(R.string.artist_photos_label),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    PhotoStrip(
                        items = photos.map { Photo(it) },
                        pathFor = { state.artistPhotoFile(it).absolutePath },
                        onAdd = {
                            pickPhoto.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        // ✕ 只是「想问一句」，真删在下面那个确认框里（2026-10-11 深夜改）
                        onRemove = { confirmRemove = it },
                        onOpen = { i -> viewerAt = i },
                    )

                    Spacer(Modifier.height(4.dp))
                    // 改名字：名字是这一页的「身份证」（约稿、备注、头像全按它挂），
                    // 所以放进这个人的编辑框里，跟备注挨着
                    TextButton(
                        onClick = {
                            renameText = g.name
                            renaming = g
                            editing = null
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(36.dp),
                    ) { Text(AppCtx.s(R.string.avatar_rename), fontSize = 13.sp) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    state.setNote(it0(g.name), text)
                    // 分类归属（2026-10-13 加）：跟备注一起在「保存」这一下写回
                    state.setCategory(it0(g.name), categoryId)
                    editing = null
                }) { Text(AppCtx.s(R.string.common_save), fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(AppCtx.s(R.string.common_cancel)) } },
        )

        // 管理分类（2026-10-13 加）：改名 / 删除都从这儿进。
        // ⚠️ 预置的两档**不给删** —— 它们是「回落默认」的锚点，所以那一行只显示「改名」。
        if (catManaging) {
            AlertDialog(
                onDismissRequest = { catManaging = false },
                shape = RoundedCornerShape(24.dp),
                title = { Text(AppCtx.s(R.string.artist_category_manage), fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        state.categoryDefs.forEach { def ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(def.name, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                TextButton(
                                    onClick = {
                                        catText = def.name
                                        catRenaming = def
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.height(32.dp),
                                ) { Text(AppCtx.s(R.string.avatar_rename), fontSize = 12.sp) }

                                if (!def.isBuiltIn) {
                                    TextButton(
                                        onClick = { catDeleting = def },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        modifier = Modifier.height(32.dp),
                                    ) {
                                        Text(
                                            AppCtx.s(R.string.common_delete_2),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { catManaging = false }) {
                        Text(AppCtx.s(R.string.common_done), fontWeight = FontWeight.SemiBold)
                    }
                },
            )
        }

        // 新增一个分类（2026-10-13 加）。⚠️ 当场生效（改的是全局那份清单），
        // 不是等底下那颗「保存」——跟备注 / 归属那种「这一个画师的事」不一样。
        if (catAdding) {
            AlertDialog(
                onDismissRequest = { catAdding = false },
                shape = RoundedCornerShape(24.dp),
                title = { Text(AppCtx.s(R.string.artist_category_add), fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = catText,
                        onValueChange = { catText = it },
                        placeholder = { Text(AppCtx.s(R.string.artist_category_name_hint)) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = catText.isNotBlank(),
                        onClick = {
                            val newId = state.addCategory(catText)
                            if (newId != null) {
                                // 顺手选中它 —— 他多半就是为了当前这位才加的。
                                // ⚠️ 归属走的是「保存」那一下（跟备注一个规矩），所以只动本地值。
                                categoryId = newId
                                catAdding = false
                            }
                        },
                    ) { Text(AppCtx.s(R.string.common_save), fontWeight = FontWeight.SemiBold) }
                },
                dismissButton = { TextButton(onClick = { catAdding = false }) { Text(AppCtx.s(R.string.common_cancel)) } },
            )
        }

        // 改分类的名字（2026-10-13 加）：只动清单里那一条，谁属于这一档完全不受影响。
        catRenaming?.let { def ->
            AlertDialog(
                onDismissRequest = { catRenaming = null },
                shape = RoundedCornerShape(24.dp),
                title = { Text(AppCtx.s(R.string.avatar_rename), fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = catText,
                        onValueChange = { catText = it },
                        placeholder = { Text(AppCtx.s(R.string.artist_category_name_hint)) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = catText.isNotBlank(),
                        onClick = {
                            state.renameCategory(def.id, catText)
                            catRenaming = null
                        },
                    ) { Text(AppCtx.s(R.string.common_save), fontWeight = FontWeight.SemiBold) }
                },
                dismissButton = { TextButton(onClick = { catRenaming = null }) { Text(AppCtx.s(R.string.common_cancel)) } },
            )
        }

        // 删分类：先弹确认（跟删图 / 删单一个规矩：删除不可逆，先问一句）。
        // 「删除」用红色、【取消】是退路。
        catDeleting?.let { def ->
            AlertDialog(
                onDismissRequest = { catDeleting = null },
                shape = RoundedCornerShape(24.dp),
                title = { Text(AppCtx.s(R.string.artist_category_delete_title)) },
                text = { Text(AppCtx.s(R.string.artist_category_delete_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        state.deleteCategory(def.id)
                        // 删掉的正好是这个人当前选的 → 本地值也回到常稿
                        if (categoryId == def.id) categoryId = CategoryDef.REGULAR
                        catDeleting = null
                    }) {
                        Text(AppCtx.s(R.string.common_delete_2), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { catDeleting = null }) { Text(AppCtx.s(R.string.common_cancel)) } },
            )
        }

        // 看大图（点缩略图进来）：跟单子那边同一个 [PhotoViewer]（Sheet、能左右翻）。
        // ⚠️ 画师图没有水印那一套，watermark 不传（默认 null = 什么都不盖）。
        // 2026-10-12 加两处（逸风拍板，**只动画师这一侧**；约稿「参考图」那个调用点一行没动）：
        //   ① 左上角垃圾桶「删除」→ 只回调，真删走上面那个二次确认（跟缩略图 ✕ 同一条路）；
        //   ② 右上角分享改成**直接发磁盘上的原图**（FileProvider，不烧水印、不重编码）。
        viewerAt?.let { start ->
            PhotoViewer(
                items = photos.map { Photo(it) },
                startIndex = start,
                pathFor = { state.artistPhotoFile(it).absolutePath },
                onDismiss = { viewerAt = null },
                onDelete = { photo -> confirmRemove = photo.name },
                shareOriginal = true,
            )
        }

        // 删图的二次确认 —— 文案跟 iOS 一字不差（「删掉这张图？」「删掉之后，这张图就不在了。」），
        // 「删除」用红色、【取消】是退路：这是逸风 2026-10-11 深夜拍板「两边统一成弹确认」，
        // ⚠️ 后面哪边再动删图交互，记得两边一起看。
        confirmRemove?.let { target ->
            AlertDialog(
                onDismissRequest = { confirmRemove = null },
                shape = RoundedCornerShape(24.dp),
                title = { Text(AppCtx.s(R.string.artist_photo_delete_title)) },
                text = { Text(AppCtx.s(R.string.artist_photo_delete_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        state.removeArtistPhoto(it0(g.name), target)
                        confirmRemove = null
                        // 2026-10-12：删掉之后把大图页也收掉 —— 这张已经不在了，
                        // 让它停在旧的位置上没意义（列表变短，页码也可能对不上）。
                        viewerAt = null
                    }) {
                        Text(AppCtx.s(R.string.common_delete_2), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmRemove = null }) { Text(AppCtx.s(R.string.common_cancel)) }
                },
            )
        }
    }

    // 改名字（2026-10-08 加）：跟 iOS 版同语义 ——
    // 这个人名下**所有**条目一起改（两个模式都算），备注和头像跟着搬。
    renaming?.let { g ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            shape = RoundedCornerShape(24.dp),
            title = { Text(AppCtx.s(R.string.avatar_rename), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        placeholder = { Text(AppCtx.s(R.string.avatar_new_name)) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        AppCtx.s(R.string.avatar_rename_note),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = renameText.isNotBlank(),
                    onClick = {
                        state.renameArtist(it0(g.name), renameText)
                        renaming = null
                    },
                ) { Text(AppCtx.s(R.string.avatar_rename), fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(AppCtx.s(R.string.common_cancel)) } },
        )
    }
}

/**
 * 分类那一行标题：**点一下收起 / 展开**（2026-10-13 加）。
 *
 * 抄的是 TestFlight「以前测试过」那种：标题右边挂**人数**和一个小箭头，
 * 收起来的时候就只剩这一行。
 *
 * 字号/颜色是 2026-10-13 逸风拍过的：第一版直接用设置页那个 `SectionLabel`（12sp 灰）——
 * 他回「Android版的分段小标题是不是太小了」。对：设置页那种小灰字是**分组说明**的尺寸；
 * 这里是**一整段的主标题**，上面压着一张张卡片，得立得住 → **14sp / 半粗 / 主文字色**。
 * ⚠️ 卡片里的名字是 16sp 半粗 —— 段标题压不过它，层次才对。
 *
 * ⚠️ 箭头**转着走**（90°=展开 / 0°=收起），不硬切 —— 他的老规矩：
 *    界面出现/消失别「刷的一下」。
 */
@Composable
private fun SectionHeaderRow(
    title: String,
    count: Int,
    open: Boolean,
    spaced: Boolean,
    onToggle: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val angle by animateFloatAsState(if (open) 90f else 0f, tween(200), label = "secArrow")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)          // 整行都能点，不是只有那几个字
            .padding(
                start = 4.dp,
                end = 4.dp,
                top = if (spaced) 8.dp else 0.dp,
                bottom = 8.dp,
            ),
    ) {
        Text(
            title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurface,
        )
        Spacer(Modifier.weight(1f))
        // 这一档有几个人（收着的时候也看得见，跟 TestFlight 那个「4」一样）
        Text(
            count.toString(),
            fontSize = 12.sp,
            color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = cs.onSurfaceVariant,
            modifier = Modifier
                .size(18.dp)
                .rotate(angle),
        )
    }
}

/** 排序：一个圆形按钮 + 下拉，当前选中的打勾（照账本页的 SortButton 抄的） */
@Composable
private fun ArtistSortButton(sort: ArtistSort, onPick: (ArtistSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        CircleIconButton(Icons.Outlined.SwapVert, sort.label) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ArtistSort.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.label, fontSize = 14.sp) },
                    leadingIcon = {
                        if (s == sort) Icon(Icons.Outlined.Check, null, modifier = Modifier.size(18.dp))
                        else Spacer(Modifier.width(18.dp))
                    },
                    onClick = { onPick(s); open = false },
                )
            }
        }
    }
}

@Composable
private fun ArtistCard(
    g: ArtistGroup,
    mode: AppMode,
    /** 图片备注的文件名 → 绝对路径（图片备注那一排缩略图用） */
    pathFor: (String) -> String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    SoftCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarBubble(g.name, size = 48.dp)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(g.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)

                    // 分类标签（2026-10-13 加）：**只挂「不是常稿」的**。
                    //
                    // 为什么不每张都挂：常稿是绝大多数、而且是默认那一档 ——
                    // 每张卡都贴一个「常稿」只是噪音；把少数那几个分类标出来，
                    // 才真叫「一眼分得清」（没标签 = 常稿）。
                    // 名字走 g.categoryName —— 他自己加的分类、改过的名字都在里面。
                    // ⚠️ 只在买家模式显示：画师模式下这一页列的是「客户」，
                    //    分类对他没意义。（跟 iOS 端同一条规矩。）
                    if (mode == AppMode.BUYER && g.showCategoryBadge) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                g.categoryName,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(AppCtx.s(R.string.ledger_order_count, g.list.size), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (g.activeCount > 0) {
                        Box(Modifier.size(3.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant))
                        Text(AppCtx.s(R.string.ledger_in_progress, g.activeCount), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(money(g.totalMoney), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(3.dp))
                Text("${Terms.paid(mode)} ${money(g.paidMoney)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp),
            )
        }

        if (g.note.isNotBlank()) {
            Spacer(Modifier.height(11.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(horizontal = 11.dp, vertical = 9.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.ContactPhone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(g.note, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // 图片备注（2026-10-11 加，**仅安卓**）：挂了这个人的图，卡片上直接画出来
        // （最多 3 张、多的折成 +N）—— 「给画师存参考图」的意义就是**一眼能看见**，
        // 藏在详情页里还得专门点进去一趟。点卡片进详情，那儿才是加图 / 删图的地方。
        if (g.photos.isNotEmpty()) {
            Spacer(Modifier.height(11.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                g.photos.take(3).forEachIndexed { i, name ->
                    Box {
                        PhotoThumb(
                            path = pathFor(name),
                            size = 56.dp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
                        )
                        // 还有更多：最后一张压一层「+N」（N = 没画出来的张数）
                        if (i == 2 && g.photos.size > 3) {
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "+${g.photos.size - 3}",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(11.dp))
        g.list.take(3).forEach { c ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(statusColor(c.status)))
                Spacer(Modifier.width(9.dp))
                Text(
                    c.title.ifBlank { AppCtx.s(R.string.common_no_content) },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(money(c.total), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
        if (g.list.size > 3) {
            Spacer(Modifier.height(4.dp))
            Text(AppCtx.s(R.string.ledger_more_orders, g.list.size - 3), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 归堆用的 key —— 空名字统一成「（没写画师）」 */
private fun it0(name: String): String = if (name.isBlank()) AppCtx.s(R.string.ledger_no_artist) else name
