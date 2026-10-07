package com.yifeng.commissionbook

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// =====================================================================
// 2026-09-30 第三批：七个小功能
//
//   ① 年度报告卡（能存成图片发出去）   ② 交付时的小庆祝
//   ③ 左滑推进进度                      ④ 合计金额滚动
//   ⑤ 催尾款 / 催交稿话术               ⑥ 报价计算器
//   ⑦ 排期挤堆提示（在 LedgerScreen 里）
//
// ⚠️ **一个字节的数据格式都没动** —— 备份还是老格式，跟 iOS 版照样互通。
//    这七个全是「从现成数据算出来」或者「临时生成、不落盘」的东西。
// =====================================================================

// MARK: - ④ 合计金额滚动

/**
 * 会滚的数字。一进屏从 0 滚到目标值；数据一变从**旧值**滚到新值。
 *
 * 滚动过程中只显示整数（不然中间帧会闪一堆小数点），到位了再显示原本的写法。
 */
@Composable
fun RollingMoney(
    value: Double,
    fontSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Bold,
) {
    val a = remember { Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(value) {
        a.animateTo(
            targetValue = value.toFloat(),
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        )
    }
    val shown = a.value.toDouble()
    val text = if (abs(shown - value) > 0.4) "¥${shown.roundToInt()}" else money(value)
    Text(text, fontSize = fontSize, fontWeight = fontWeight, color = color, modifier = modifier)
}

// MARK: - ② 交付时的小庆祝

/**
 * 「已交付」那一下的小庆祝：圆底打勾 + 一圈碎光往外飘，一秒后自己消失。
 *
 * 不挡事：点一下屏幕就能提前收掉。做这个是因为逸风说过「界面出现/消失别硬切」——
 * 顺手让「做完一件事」有个交代，而不是静默变个状态。
 */
@Composable
fun CelebrateOverlay(show: Boolean, label: String = AppCtx.s(R.string.ledger_delivered), onDone: () -> Unit) {
    val p = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current

    androidx.compose.runtime.LaunchedEffect(show) {
        if (!show) return@LaunchedEffect
        p.snapTo(0f)
        lightTick(haptic)
        p.animateTo(1f, tween(durationMillis = 950, easing = LinearEasing))
        kotlinx.coroutines.delay(110)
        onDone()
    }
    if (!show) return

    val t = p.value
    // 出现：前 35% 的时间里从 0.7 弹到 1（带一点点回弹感）
    val pop = (t / 0.35f).coerceIn(0f, 1f)
    val scale = 0.70f + 0.30f * (1f - (1f - pop) * (1f - pop))
    val fade = (1f - (t / 0.55f).coerceIn(0f, 1f)) * 0.55f + 0.25f

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.10f * fade))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDone() },
        contentAlignment = Alignment.Center,
    ) {
        // 碎光：12 颗小圆，沿 12 个方向往外飘、同时淡掉
        Canvas(Modifier.size(240.dp)) {
            val ease = 1f - (1f - t) * (1f - t)
            val r = size.minDimension * (0.16f + 0.30f * ease)
            repeat(12) { i ->
                val ang = i * 30.0 * Math.PI / 180.0
                val cx = center.x + (r * cos(ang)).toFloat()
                val cy = center.y + (r * sin(ang)).toFloat()
                val dotColor = if (i % 2 == 0) IceBlue else IceBlueLight
                drawCircle(
                    color = dotColor.copy(alpha = (1f - t).coerceIn(0f, 1f) * 0.85f),
                    radius = 5.5f * (1f - t * 0.5f),
                    center = androidx.compose.ui.geometry.Offset(cx, cy),
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = pop
            },
        ) {
            Box(
                Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = statusColor(CommissionStatus.DELIVERED),
                    modifier = Modifier.size(64.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                label,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// MARK: - ⑤ 催尾款 / 催交稿 话术

fun nudgeTitle(mode: AppMode): String =
    if (mode == AppMode.ARTIST) AppCtx.s(R.string.script_remaining_balance) else AppCtx.s(R.string.script_delivery_reminder)

/**
 * 三段话术：客气 / 正常 / 直接。
 * 全是本地拼的 —— 不联网、不上传，App 连 INTERNET 权限都没有。
 */
fun nudgeOptions(c: Commission, mode: AppMode): List<Pair<String, String>> {
    val name = c.artist.ifBlank { Terms.otherBlank(mode) }
    val title = c.title.ifBlank { AppCtx.s(R.string.ledger_this_order) }
    val unpaid = (c.total - c.deposit).coerceAtLeast(0.0)
    val d = c.daysLeft()
    val late = when {
        d == null -> AppCtx.s(R.string.notify_deadline_soon)
        d < 0 -> AppCtx.s(R.string.notify_overdue_days, -d)
        d == 0 -> AppCtx.s(R.string.notify_due_today)
        else -> AppCtx.s(R.string.notify_days_left, d)
    }

    return if (mode == AppMode.ARTIST) {
        listOf(
            AppCtx.s(R.string.script_polite) to AppCtx.s(R.string.script_polite_balance, name, title, money(unpaid)),
            AppCtx.s(R.string.script_normal) to AppCtx.s(R.string.script_normal_balance, name, title, money(unpaid)),
            AppCtx.s(R.string.script_direct) to AppCtx.s(R.string.script_direct_balance, name, title, money(unpaid), late),
        )
    } else {
        listOf(
            AppCtx.s(R.string.script_polite) to AppCtx.s(R.string.script_polite_status, name, title, late),
            AppCtx.s(R.string.script_normal) to AppCtx.s(R.string.script_normal_status, name, title, late),
            AppCtx.s(R.string.script_direct) to AppCtx.s(R.string.script_direct_status, name, title, late),
        )
    }
}

/** 选一段话术、复制走人 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NudgeSheet(c: Commission, mode: AppMode, onDismiss: () -> Unit, onCopied: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val clip = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val opts = remember(c.id, mode) { nudgeOptions(c, mode) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Text(nudgeTitle(mode), fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                AppCtx.s(R.string.script_copy_hint),
                fontSize = 12.sp,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))

            opts.forEach { (tone, body) ->
                SoftCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tone, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = cs.primary)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = {
                            lightTick(haptic)
                            clip.setText(AnnotatedString(body))
                            onCopied(AppCtx.s(R.string.script_copied, tone))
                        }) {
                            Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(AppCtx.s(R.string.common_copy), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(body, fontSize = 13.5.sp, color = cs.onSurface, lineHeight = 21.sp)
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

// MARK: - ⑥ 报价计算器

// ⚠️ 2026-10-05（3.5.37）改成 `get()`：这四个名字都要按**当前语言**取（AppCtx.s），
//    写成普通顶层 `val` 会被第一次访问时的语言钉死 —— 跟 PrivacyScreen 那个
//    「切了语言、隐私政策还是中文」是同一个病（见那边的长注释）。
private val QUOTE_TYPES: List<Pair<String, Double>> get() = listOf(AppCtx.s(R.string.common_avatar) to 50.0, AppCtx.s(R.string.common_half_body) to 100.0, AppCtx.s(R.string.common_full_body) to 200.0, AppCtx.s(R.string.artist_fursuit_full) to 400.0)

/**
 * 一条加价项 —— **画师自己定的**：叫什么、加多少、按比例还是固定金额。
 *
 * [kind]：0 = 按比例（value 是百分数，30 就是 +30%）；1 = 固定金额（value 是元）。
 *
 * 2026-10-01 改：原先写死「加急/商用授权/加背景」三项，逸风说「有点死板，
 * 应该让画师自己决定」—— 现在名字、数值、算法都能改，还能自己加/删。
 */
data class QuoteExtra(val name: String, val kind: Int, val value: Double) {
    val isPercent: Boolean get() = kind == 0

    /** 给界面看的一行小字：「+30%」/「+¥50」 */
    fun label(): String = if (isPercent) "+${trimNum(value)}%" else "+${money(value)}"

    /** 完整叫法：「按比例 +30%」/「固定 +¥50」 */
    fun describe(): String = (if (isPercent) AppCtx.s(R.string.artist_surcharge_ratio) else AppCtx.s(R.string.artist_surcharge_fixed)) + label()
}

/** 没改过的时候用这三项（原来的写法，当出厂值留住） */
// ⚠️ 2026-10-05（3.5.37）同样改成 `get()`：这三项的名字也要按当前语言取，
//    普通顶层 `val` 会被第一次访问时的语言钉死（同 PrivacyScreen 那个病）。
val DEFAULT_QUOTE_EXTRAS: List<QuoteExtra> get() = listOf(
    QuoteExtra(AppCtx.s(R.string.artist_surcharge_rush), 0, 30.0),
    QuoteExtra(AppCtx.s(R.string.artist_surcharge_commercial), 0, 50.0),
    QuoteExtra(AppCtx.s(R.string.artist_surcharge_background), 0, 20.0),
)

/**
 * 从本机设置里读加价项。空 / 读坏了 → 用默认那三项。
 * 存的是 `[{"n":"加急","k":0,"v":30}, …]`。
 */
fun loadQuoteExtras(raw: String): List<QuoteExtra> {
    if (raw.isBlank()) return DEFAULT_QUOTE_EXTRAS
    val parsed = runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            QuoteExtra(o.optString("n"), o.optInt("k", 0), o.optDouble("v", 0.0))
        }
    }.getOrNull()
    return parsed?.takeIf { it.isNotEmpty() && it.all { e -> e.name.isNotBlank() } } ?: DEFAULT_QUOTE_EXTRAS
}

/** 存回本机设置。⚠️ 只进本机 prefs，**不进备份文件**（备份格式一个字节没动） */
fun encodeQuoteExtras(list: List<QuoteExtra>): String {
    val arr = JSONArray()
    list.forEach { e ->
        arr.put(JSONObject().put("n", e.name).put("k", e.kind).put("v", e.value))
    }
    return arr.toString()
}

/**
 * 报价计算器（画师向）：类型 → 单价（可改）→ 张数 → 加价项 → 出一个数，
 * 再生成一段能直接复制给客户的报价文本。
 *
 * 不改数据、不落盘 —— 纯工具页。
 */
@Composable
fun QuoteCalculatorScreen(onBack: () -> Unit, onCopied: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val clip = LocalClipboardManager.current

    var typeIdx by remember { mutableStateOf(1) }
    var unit by remember { mutableStateOf(trimNum(QUOTE_TYPES[1].second)) }
    var qty by remember { mutableStateOf(1) }

    // 加价项清单：从本机设置读，画师改完立刻存回去
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var extraList by remember { mutableStateOf(loadQuoteExtras(prefs.quoteExtras)) }
    var extras by remember { mutableStateOf(setOf<String>()) }
    var editing by remember { mutableStateOf(false) }

    // 清单里被删掉的项，也要从「已选」里去掉 —— 不然它会一直算进总价
    androidx.compose.runtime.LaunchedEffect(extraList) {
        val names = extraList.map { it.name }.toSet()
        if (!names.containsAll(extras)) extras = extras.intersect(names)
    }
    fun saveExtras(nl: List<QuoteExtra>) {
        extraList = nl
        prefs.quoteExtras = encodeQuoteExtras(nl)
    }

    val unitV = unit.toDoubleOrNull() ?: 0.0
    val chosen = extraList.filter { extras.contains(it.name) }
    val percentRate = chosen.filter { it.isPercent }.sumOf { it.value } / 100.0
    val fixedSum = chosen.filterNot { it.isPercent }.sumOf { it.value }
    // 先按比例加成（比例是加在「单价 × 张数」上的），再加固定金额
    val sum = unitV * qty * (1.0 + percentRate) + fixedSum

    androidx.activity.compose.BackHandler { onBack() }

    ScreenSurface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
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
                    Text(AppCtx.s(R.string.artist_quote_calculator), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
                    Text(AppCtx.s(R.string.artist_quote_calculator_subtitle), fontSize = 12.sp, color = cs.onSurfaceVariant)
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 类型
                SoftCard(Modifier.fillMaxWidth()) {
                    Text(AppCtx.s(R.string.artist_quote_what), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        QUOTE_TYPES.forEachIndexed { i, (label, price) ->
                            SlimChip(label, i == typeIdx, Modifier.weight(1f)) {
                                typeIdx = i
                                unit = trimNum(price)
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text(AppCtx.s(R.string.artist_quote_unit_price), fontSize = 12.sp) },
                        prefix = { Text("¥") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(14.dp))
                    Text(AppCtx.s(R.string.artist_quote_quantity), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RoundStepButton("−") { if (qty > 1) qty-- }
                        Text(
                            AppCtx.s(R.string.artist_quote_quantity_count, qty),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        RoundStepButton("+") { if (qty < 99) qty++ }
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(AppCtx.s(R.string.artist_quote_surcharges), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = {
                            lightTick(haptic)
                            editing = true
                        }) {
                            Icon(Icons.Outlined.Tune, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(AppCtx.s(R.string.artist_quote_custom), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (extraList.isEmpty()) {
                        Text(
                            AppCtx.s(R.string.artist_quote_no_surcharge),
                            fontSize = 12.sp,
                            color = cs.onSurfaceVariant,
                        )
                    } else {
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            extraList.forEach { e ->
                                SlimChip("${e.name} ${e.label()}", extras.contains(e.name)) {
                                    extras = if (extras.contains(e.name)) extras - e.name else extras + e.name
                                }
                            }
                        }
                    }
                }

                // 结果
                HeroCard {
                    Text(AppCtx.s(R.string.artist_quote_quote), fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
                    Spacer(Modifier.height(4.dp))
                    RollingMoney(sum, fontSize = 32.sp, color = Color.White)
                    Spacer(Modifier.height(10.dp))
                    val detail = buildString {
                        append("${QUOTE_TYPES[typeIdx].first} × $qty")
                        if (chosen.isNotEmpty()) {
                            append(" · ")
                            append(chosen.joinToString(" / ") { "${it.name}${it.label()}" })
                        }
                    }
                    Text(detail, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
                }

                Button(
                    onClick = {
                        lightTick(haptic)
                        clip.setText(AnnotatedString(quoteText(typeIdx, unitV, qty, chosen, sum)))
                        onCopied(AppCtx.s(R.string.artist_quote_copied))
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = cs.primary),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    Text(AppCtx.s(R.string.artist_quote_copy_text), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(20.dp))
            }
        }

        if (editing) {
            QuoteExtrasSheet(
                list = extraList,
                onChange = ::saveExtras,
                onDismiss = { editing = false },
            )
        }
    }
}

/**
 * 「加价项」编辑面板。
 *
 * 两种状态装在一个 Sheet 里：**列表**（增 / 改 / 删）和**表单**（填一条）。
 * 没做成「Sheet 里再弹 Dialog」—— 套两层弹窗在安卓上容易出怪事，
 * 而且来回点的时候会闪。一个 Sheet 自己切页面，最稳也最省事。
 *
 * ⚠️ 老规矩：`skipPartiallyExpanded = true` + `verticalScroll` + `imePadding()`，
 *    不然键盘一起来就把「保存」盖住了（2.7 那个坑）。
 */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun QuoteExtrasSheet(
    list: List<QuoteExtra>,
    onChange: (List<QuoteExtra>) -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current

    var formOpen by remember { mutableStateOf(false) }
    var editIndex by remember { mutableStateOf(-1) }   // -1 = 新加一条
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(0) }
    var value by remember { mutableStateOf("") }

    fun openForm(i: Int) {
        editIndex = i
        val e = if (i >= 0) list[i] else null
        name = e?.name ?: ""
        kind = e?.kind ?: 0
        value = e?.let { trimNum(it.value) } ?: ""
        formOpen = true
    }

    val valueV = value.toDoubleOrNull() ?: 0.0
    val canSave = name.isNotBlank() && valueV > 0.0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            if (!formOpen) {
                // ---------- 列表 ----------
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(AppCtx.s(R.string.artist_surcharge_title), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            AppCtx.s(R.string.artist_surcharge_desc),
                            fontSize = 12.sp,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))

                if (list.isEmpty()) {
                    Text(
                        AppCtx.s(R.string.artist_surcharge_empty),
                        fontSize = 13.sp,
                        color = cs.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                }

                list.forEachIndexed { i, e ->
                    SoftCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(e.name, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(2.dp))
                                Text(e.describe(), fontSize = 12.sp, color = cs.onSurfaceVariant)
                            }
                            IconButton(onClick = {
                                lightTick(haptic)
                                openForm(i)
                            }) {
                                Icon(Icons.Outlined.Edit, AppCtx.s(R.string.common_edit), Modifier.size(17.dp), tint = cs.primary)
                            }
                            IconButton(onClick = {
                                lightTick(haptic)
                                onChange(list.filterIndexed { j, _ -> j != i })
                            }) {
                                Icon(Icons.Outlined.Close, AppCtx.s(R.string.common_delete), Modifier.size(17.dp), tint = cs.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                Button(
                    onClick = {
                        lightTick(haptic)
                        openForm(-1)
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = cs.primary),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text(AppCtx.s(R.string.artist_surcharge_add_item), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = {
                        lightTick(haptic)
                        onChange(DEFAULT_QUOTE_EXTRAS)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(AppCtx.s(R.string.artist_surcharge_restore_defaults), fontSize = 13.sp, color = cs.onSurfaceVariant)
                }
            } else {
                // ---------- 表单 ----------
                Text(
                    if (editIndex >= 0) AppCtx.s(R.string.artist_surcharge_edit_item) else AppCtx.s(R.string.artist_surcharge_new_item),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    AppCtx.s(R.string.artist_surcharge_name_hint),
                    fontSize = 12.sp,
                    color = cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(AppCtx.s(R.string.artist_surcharge_name), fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(14.dp))
                Text(AppCtx.s(R.string.artist_surcharge_how), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SlimChip(AppCtx.s(R.string.artist_surcharge_percent), kind == 0, Modifier.weight(1f)) { kind = 0 }
                    SlimChip(AppCtx.s(R.string.artist_surcharge_fixed_yen), kind == 1, Modifier.weight(1f)) { kind = 1 }
                }

                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = {
                        Text(if (kind == 0) AppCtx.s(R.string.artist_surcharge_percent_hint) else AppCtx.s(R.string.artist_surcharge_amount_hint), fontSize = 12.sp)
                    },
                    prefix = { Text(if (kind == 0) "%" else "¥") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))
                Text(
                    if (kind == 0) {
                        AppCtx.s(R.string.script_example_tiered, money(390.0))
                    } else {
                        AppCtx.s(R.string.script_example_flat, money(50.0))
                    },
                    fontSize = 11.5.sp,
                    color = cs.onSurfaceVariant,
                )

                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            lightTick(haptic)
                            formOpen = false
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = cs.surfaceVariant,
                            contentColor = cs.onSurfaceVariant,
                        ),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text(AppCtx.s(R.string.common_cancel), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = {
                            if (!canSave) return@Button
                            lightTick(haptic)
                            val e = QuoteExtra(name.trim(), kind, valueV)
                            val nl = list.toMutableList()
                            if (editIndex >= 0) nl[editIndex] = e else nl += e
                            onChange(nl)
                            formOpen = false
                        },
                        enabled = canSave,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = cs.primary),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text(AppCtx.s(R.string.common_save), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundStepButton(label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(cs.primary.copy(alpha = 0.12f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = cs.primary)
    }
}

/** 给客户的报价文本。加价项按画师自己写的名字和算法列出来（比例、固定混着也行） */
fun quoteText(typeIdx: Int, unit: Double, qty: Int, extras: List<QuoteExtra>, total: Double): String {
    val lines = mutableListOf<String>()
    lines += AppCtx.s(R.string.script_quote_line, QUOTE_TYPES[typeIdx].first, money(unit), qty)
    if (extras.isNotEmpty()) {
        lines += extras.joinToString("、") { "${it.name}${it.label()}" } + AppCtx.s(R.string.script_included_in_total)
    }
    lines += AppCtx.s(R.string.script_total, money(total))
    lines += ""
    lines += AppCtx.s(R.string.script_note_minor_revisions)
    lines += AppCtx.s(R.string.script_note_deposit)
    lines += AppCtx.s(R.string.script_note_draft_eta)
    return lines.joinToString("\n")
}

// MARK: - ① 年度报告

data class YearReport(
    val year: Int,
    val count: Int,
    val total: Double,
    val paid: Double,
    val delivered: Int,
    val avg: Double,
    val topPartner: Pair<String, Int>?,
    val biggest: Commission?,
    val busiest: Pair<Int, Double>?,
) {
    val unpaid: Double get() = (total - paid).coerceAtLeast(0.0)
}

fun availableYears(items: List<Commission>): List<Int> {
    val cur = Calendar.getInstance().get(Calendar.YEAR)
    val ys = items.map {
        Calendar.getInstance().apply { timeInMillis = it.dateMillis }.get(Calendar.YEAR)
    }.toSortedSet()
    ys.add(cur)
    return ys.reversed().toList()
}

/** 全部从现成字段算 —— 不新增任何数据 */
fun buildYearReport(items: List<Commission>, year: Int): YearReport {
    val list = items.filter {
        Calendar.getInstance().apply { timeInMillis = it.dateMillis }.get(Calendar.YEAR) == year
    }
    val total = list.sumOf { it.total }
    val top = list.groupBy { it.artist.ifBlank { AppCtx.s(R.string.script_unnamed) } }
        .map { (name, g) -> name to g.size }
        .maxByOrNull { it.second }
    val busiest = list.groupBy {
        Calendar.getInstance().apply { timeInMillis = it.dateMillis }.get(Calendar.MONTH)
    }.map { (m, g) -> m to g.sumOf { it.total } }.maxByOrNull { it.second }
    return YearReport(
        year = year,
        count = list.size,
        total = total,
        paid = list.sumOf { it.deposit },
        delivered = list.count { it.status == CommissionStatus.DELIVERED },
        avg = if (list.isEmpty()) 0.0 else total / list.size,
        topPartner = top,
        biggest = list.maxByOrNull { it.total },
        busiest = busiest,
    )
}

/**
 * 年度报告页：上面是那张「要发出去的图」，下面是按钮。
 * 年份能前后翻（有数据的年份 + 今年）。
 */
@Composable
fun YearReportScreen(
    items: List<Commission>,
    mode: AppMode,
    onBack: () -> Unit,
    onSharePng: (android.graphics.Bitmap) -> Unit,
    /** 老机兼容版专用：把报告当**纯文字**发出去（见下面 `BuildConfig.LEGACY_COMPAT`） */
    onShareText: (String) -> Unit,
    onCopied: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val years = remember(items) { availableYears(items) }
    var year by remember { mutableStateOf(years.firstOrNull() ?: 2026) }
    val report = remember(items, year) { buildYearReport(items, year) }

    // 把这张卡「拍」成图片：Compose 1.7 的 GraphicsLayer
    //
    // ⚠️ 老机兼容版（2026-10-07）**连创建都不创建**：
    //    那位用户的华为 nova 3（鸿蒙 2.0 / 麒麟 970）一进这一页就闪退，
    //    而这一页独有的、最可疑的东西就是它（GraphicsLayer 底下是 RenderNode，
    //    在老华为那套魔改图形栈上最容易翻车）。兼容版改成发纯文字，彻底不碰。
    //    `BuildConfig.LEGACY_COMPAT` 是编译期常量，这个分支一辈子不会变，写法是稳的。
    val gl = if (BuildConfig.LEGACY_COMPAT) null else rememberGraphicsLayer()

    androidx.activity.compose.BackHandler { onBack() }

    ScreenSurface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
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
                    Text(AppCtx.s(R.string.stats_annual_report), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
                    Text(AppCtx.s(R.string.stats_orders_from_data, report.count), fontSize = 12.sp, color = cs.onSurfaceVariant)
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 年份左右翻
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        val i = years.indexOf(year)
                        if (i < years.size - 1) year = years[i + 1]
                    }) { Text(AppCtx.s(R.string.stats_prev_year), fontSize = 13.sp) }
                    Text("$year", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = {
                        val i = years.indexOf(year)
                        if (i > 0) year = years[i - 1]
                    }) { Text(AppCtx.s(R.string.stats_next_year), fontSize = 13.sp) }
                }

                // 这张就是要发出去的图（兼容版不录，见上面 `gl` 那段注释）
                Box(
                    Modifier
                        .fillMaxWidth()
                        .then(if (gl != null) Modifier.captureTo(gl) else Modifier),
                ) {
                    ReportCard(report, mode)
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        lightTick(haptic)
                        if (BuildConfig.LEGACY_COMPAT) {
                            // 兼容版：发文字。一个字都不画，所以不可能在这里闪退。
                            onShareText(reportText(report, mode))
                        } else {
                            scope.launch {
                                runCatching {
                                    val bmp = gl!!.toImageBitmap().asAndroidBitmap()
                                    onSharePng(bmp)
                                }.onFailure { onCopied(AppCtx.s(R.string.stats_image_failed)) }
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = cs.primary),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    Text(
                        AppCtx.s(
                            if (BuildConfig.LEGACY_COMPAT) R.string.stats_share_card_text
                            else R.string.stats_share_card
                        ),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    AppCtx.s(R.string.stats_save_or_share_hint),
                    fontSize = 11.5.sp,
                    color = cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/**
 * 把这一层的内容录进 [gl]，后面 `gl.toImageBitmap()` 就能拿到这块内容的位图。
 * （Compose 1.7 的写法：先 record，再 drawLayer）
 */
fun Modifier.captureTo(gl: androidx.compose.ui.graphics.layer.GraphicsLayer): Modifier =
    this.drawWithContent {
        gl.record { this@drawWithContent.drawContent() }
        drawLayer(gl)
    }

/** 那张报告图：固定宽度，深色渐变底，白字 —— 一眼能看完一年 */
@Composable
fun ReportCard(r: YearReport, mode: AppMode) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier
            .width(320.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(listOf(IceBlueLight, IceBlue, Color(0xFF14406F)))
            )
            .padding(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(AppCtx.s(R.string.ledger_title), fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
            Spacer(Modifier.weight(1f))
            Text(AppCtx.s(R.string.stats_year_report_title, r.year), fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
        }

        Spacer(Modifier.height(24.dp))
        Text(
            if (mode == AppMode.ARTIST) AppCtx.s(R.string.stats_earnings_this_year) else AppCtx.s(R.string.stats_spent_this_year),
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.85f),
        )
        Spacer(Modifier.height(6.dp))
        Text(money(r.total), fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(6.dp))
        Text(
            AppCtx.s(R.string.stats_summary_line, r.count, r.delivered, money(r.avg)),
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.85f),
        )

        Spacer(Modifier.height(20.dp))
        ReportLine(AppCtx.s(R.string.stats_top_collaborator), r.topPartner?.let { AppCtx.s(R.string.stats_collaborator_orders, it.first, it.second) } ?: "—")
        ReportLine(AppCtx.s(R.string.stats_largest_order), r.biggest?.let { "${it.artist.ifBlank { "（没写名字）" }} ${money(it.total)}" } ?: "—")
        ReportLine(
            AppCtx.s(R.string.stats_busiest_month),
            r.busiest?.let { AppCtx.s(R.string.stats_month_amount, it.first + 1, money(it.second)) } ?: "—",
        )
        ReportLine(Terms.unpaid(mode), money(r.unpaid))

        Spacer(Modifier.height(18.dp))
        // 2026-10-02 加：逸风要「所有分享出去的图片都带上『作者：筱晞』」——
        // 这张年度卡原来只写「本地生成 · 没有联网」，没有署名。
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                AppCtx.s(R.string.stats_local_only),
                fontSize = 10.5.sp,
                color = Color.White.copy(alpha = 0.7f),
            )
            Spacer(Modifier.weight(1f))
            Text(
                AppCtx.s(R.string.common_author),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun ReportLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f))
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

/**
 * 老机兼容版专用：把年度报告拼成一段纯文字。
 * **不碰任何绘图 / 位图 API**，所以在老华为那种图形栈上也不会出岔子。
 * 词条全部复用界面上已有的那几条，不新增翻译。
 */
fun reportText(r: YearReport, mode: AppMode): String = buildString {
    appendLine(AppCtx.s(R.string.stats_year_report_title, r.year))
    appendLine()
    appendLine(
        AppCtx.s(if (mode == AppMode.ARTIST) R.string.stats_earnings_this_year else R.string.stats_spent_this_year)
            + " " + money(r.total)
    )
    appendLine(AppCtx.s(R.string.stats_summary_line, r.count, r.delivered, money(r.avg)))
    appendLine(
        AppCtx.s(R.string.stats_top_collaborator) + "：" +
            (r.topPartner?.let { AppCtx.s(R.string.stats_collaborator_orders, it.first, it.second) } ?: "—")
    )
    appendLine(
        AppCtx.s(R.string.stats_largest_order) + "：" +
            (r.biggest?.let { "${it.artist.ifBlank { AppCtx.s(R.string.script_unnamed) }} ${money(it.total)}" } ?: "—")
    )
    appendLine(
        AppCtx.s(R.string.stats_busiest_month) + "：" +
            (r.busiest?.let { AppCtx.s(R.string.stats_month_amount, it.first + 1, money(it.second)) } ?: "—")
    )
    appendLine(Terms.unpaid(mode) + "：" + money(r.unpaid))
    appendLine()
    append(AppCtx.s(R.string.common_author))
}

/** 发一段纯文字（走系统分享面板）。跟 [sharePng] 一个路子，只是不带文件、不要任何权限。 */
fun shareText(context: Context, text: String): Intent? = runCatching {
    Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
}.getOrNull()

/** 一句话提示（复制完、生成失败这类） */
fun toastNow(context: Context, msg: String) {
    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
}

// MARK: - 分享图片

/**
 * 分享一张 PNG。
 * 先落一份到 cache/share，再用 FileProvider 给出去 ——
 * **不用任何存储权限**（相册那套要权限，这个不要，隐私政策也不用改）。
 */
fun sharePng(context: Context, bmp: android.graphics.Bitmap, name: String): Intent? = runCatching {
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    val file = File(dir, name)
    file.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}.getOrNull()
