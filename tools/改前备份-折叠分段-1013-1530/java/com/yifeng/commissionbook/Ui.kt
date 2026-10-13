package com.yifeng.commissionbook

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// MARK: - 小工具

/** 300 → ¥300；300.5 → ¥300.50 */
fun money(v: Double): String =
    if (v % 1.0 == 0.0) "¥${v.toInt()}" else "¥" + String.format(Locale.CHINA, "%.2f", v)

val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

fun dayText(millis: Long): String = dayFmt.format(Date(millis))

/**
 * 日期选择器（Material3 的 DatePicker）吃的是 **UTC 零点**，
 * 而本 App 存的 `dateMillis` 是**本地零点** —— 两边不换算直接对招会差一天
 * （东八区：把本地零点当 UTC 用的那天，会往前跳一格）。
 *
 * 所以：进选择器前「本地 → yyyy-MM-dd → 按 UTC 解析」，出来后「UTC → yyyy-MM-dd → 按本地解析」。
 * 中间那根字符串（yyyy-MM-dd）就是两边都认的口径。
 */
private val utcDayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

fun toPickerMillis(day: String): Long? = runCatching { utcDayFmt.parse(day)?.time }.getOrNull()

fun fromPickerMillis(utcMillis: Long): String = utcDayFmt.format(Date(utcMillis))

/** 19:00 / 7:00 PM —— 跟系统「24 小时制」开关走 */
fun clockText(hour: Int, minute: Int, is24: Boolean): String {
    if (is24) return String.format(Locale.CHINA, "%02d:%02d", hour, minute)
    val h12 = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    val suffix = if (hour < 12) "AM" else "PM"
    return String.format(Locale.CHINA, "%d:%02d %s", h12, minute, suffix)
}

/**
 * 「点一下弹选择器」的字段 —— 日期、时间都用它。
 *
 * 长得跟 `OutlinedTextField` 是一家（圆角描边 + 左上小标签 + 右侧一个图标），
 * 但**不能打字**：手机上敲日期要切输入法、还得校验格式，点选省事得多
 * （2026-10-05 逸风要求：日期用日期选择器）。
 */
@Composable
fun PickerField(
    label: String,
    value: String,
    icon: ImageVector? = null,
    placeholder: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, cs.outline.copy(alpha = 0.5f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(3.dp))
            Text(
                value.ifBlank { placeholder.orEmpty() },
                fontSize = 15.sp,
                color = if (value.isBlank()) cs.onSurfaceVariant.copy(alpha = 0.55f) else cs.onSurface,
            )
        }
        if (icon != null) {
            Spacer(Modifier.width(10.dp))
            Icon(icon, contentDescription = null, tint = cs.primary, modifier = Modifier.size(19.dp))
        }
    }
}

/** 每个进度一个颜色，跟 iOS 版对齐 */
fun statusColor(s: CommissionStatus, dark: Boolean = false): Color = when (s) {
    CommissionStatus.QUOTING -> Color(0xFF8E8E93)
    CommissionStatus.DEPOSIT_PAID -> Color(0xFFFF9F0A)
    CommissionStatus.DRAWING -> Color(0xFF0A84FF)
    CommissionStatus.DELIVERED -> Color(0xFF30D158)
}

// MARK: - 进度小标签

/** 小圆点 + 文字，底色是同色的淡版。文字跟着 [AppMode] 走（买家「已付定金」/ 画师「已收定金」） */
@Composable
fun StatusChip(status: CommissionStatus, small: Boolean = false, mode: AppMode = AppMode.BUYER) {
    val c = statusColor(status)
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(c.copy(alpha = 0.15f))
            .padding(horizontal = if (small) 8.dp else 10.dp, vertical = if (small) 3.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (small) 4.dp else 5.dp),
    ) {
        Box(Modifier.size(if (small) 5.dp else 6.dp).clip(CircleShape).background(c))
        Text(
            Terms.status(status, mode),
            color = c,
            fontSize = if (small) 11.sp else 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * 阶段进度条：4 格，走到的亮、没到的暗。
 * 颜色是「一格一格亮起来」的（错开 55ms），不是整排突然跳色 —— 逸风嫌硬切。
 */
@Composable
fun StageBar(status: CommissionStatus, modifier: Modifier = Modifier) {
    val idx = status.stageIndex
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(CommissionStatus.STAGE_COUNT) { i ->
            val target = if (i <= idx) statusColor(status)
                         else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
            val c by animateColorAsState(
                targetValue = target,
                animationSpec = tween(durationMillis = 260, delayMillis = i * 55),
                label = "stage$i",
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(7.dp)
                    .background(color = c, shape = RoundedCornerShape(4.dp))
            )
        }
    }
}

/** 「还剩 3 天 / 超期 2 天」这句 */
/**
 * 金额 / 日期这类数字统一用**等宽字形**（tabular figures）。
 *
 * 为什么：默认比例字形里「1」比「8」窄，列表滚动、数字跳动时整行会左右抖一下，
 * 一屏钱看着就不稳。等宽之后每个数字占一样宽，金额列天然对齐 ——
 * 这 App 的主角本来就是钱，这一下最划算（2026-10-05 加，逸风要「界面再干净一点」）。
 */
val tnum = TextStyle(fontFeatureSettings = "tnum")

fun deadlineText(c: Commission): Pair<String, Boolean>? {
    // ⚠️ 2026-10-05 修：**已交付的单子不再报超期**。
    //    逸风报「已归档的条目中，已交付的依旧显示已超期」—— 交付完了就没「超期」这回事，
    //    老挂着红字只会让人以为还有一笔没收。
    //    iOS 那边一直就是这么处理的（`daysLeftText` 先判 `.delivered` 直接给「已交付」），
    //    安卓这里漏了，跟它对齐。
    if (c.status == CommissionStatus.DELIVERED) return AppCtx.s(R.string.ledger_delivered) to false
    val d = c.daysLeft() ?: return null
    val late = d < 0
    val s = when {
        d < 0 -> AppCtx.s(R.string.ledger_overdue_days, -d)
        d == 0 -> AppCtx.s(R.string.notify_due_today)
        else -> AppCtx.s(R.string.notify_days_left, d)
    }
    return s to late
}

// MARK: - 瘦身版筛选按钮

/**
 * 胶囊形筛选按钮（2026-09-29 视觉重做：从方角 FilterChip 换成 iOS 那种胶囊）。
 *
 * 宽度纪律不能破：逸风要求五个按钮**必须一排**。
 * 五个按钮的文字合计 180dp，左右各留 9dp → 270dp，加 4 个 6dp 间隙 = 294dp，
 * 360dp 手机可用 328dp，放得下（原生 FilterChip 左右各吃 16dp，是 364dp，放不下才会被裁）。
 * 所以横向内边距压到 9dp —— 观感是胶囊，宽度还在一排的安全线内。
 */
@Composable
fun SlimChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 12.sp,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val dark = isDarkUi()
    val shape = RoundedCornerShape(50)
    val interaction = remember { MutableInteractionSource() }
    // 未选中：铺一层比底色略深的灰（iOS 那种「实心胶囊」），不描边 ——
    // 描边看着像安卓的 FilterChip；实心灰更干净，而且宽度一点没变，还是一排。
    // ⚠️ 2026-10-05 调淡：原来浅色下是 0.8，五个胶囊并排时整排都在抢眼，
    //    反而把「哪个是选中的」这件事淹了。调成 0.45，选中的那枚（实心主色）立刻跳出来。
    val fillUnselected = if (dark) cs.surfaceVariant.copy(alpha = 0.35f) else cs.surfaceVariant.copy(alpha = 0.45f)
    val bg by animateColorAsState(
        targetValue = if (selected) cs.primary else fillUnselected,
        animationSpec = tween(200),
        label = "chipBg",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) cs.onPrimary else cs.onSurfaceVariant,
        animationSpec = tween(200),
        label = "chipFg",
    )
    Box(
        modifier
            .height(32.dp)
            .clip(shape)
            .background(bg)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = fontSize,
            maxLines = 1,
            softWrap = false,
            color = fg,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

// MARK: - 锁屏页

@Composable
fun LockScreen(hasBio: Boolean, onUnlock: () -> Unit) {
    ScreenSurface {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(AppCtx.s(R.string.ledger_locked), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                if (hasBio) AppCtx.s(R.string.ledger_unlock_biometric) else AppCtx.s(R.string.settings_no_screen_lock),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onUnlock,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text(AppCtx.s(R.string.common_unlock), fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}
