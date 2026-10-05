package com.yifeng.commissionbook

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * 设计系统（2026-09-29 视觉重做版）。
 *
 * 为什么要单独一个文件：四个页面用的圆角、投影、灰阶、分组卡片都是同一套，
 * 散在各页里改一处要翻四个文件。集中在这里，改一个数字全 App 一起变。
 *
 * 观感目标（逸风原话「iOS 那种感觉、好看点、现代点」）：
 *   ① 底色带一点点蓝，卡片纯白浮在上面 → 层次靠明度差，不靠粗边框
 *   ② 圆角大（卡片 22dp、控件 14~18dp）
 *   ③ 点按有「压一下」的回弹（iOS 的按压感），并且**没有安卓那圈水波纹**
 *   ④ 分组列表（设置页）用「一整块圆角里塞几行」的 iOS inset-grouped 样式
 */

/** 是不是深色。看当前配色的底色亮度，不看系统设置 —— 这样截图对比时不用改系统。 */
@Composable
fun isDarkUi(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** 轻轻一下触感（不是哒哒震手，就是「按到了」的那一下）—— iOS 的 Taptic 那味儿 */
fun lightTick(haptic: HapticFeedback) {
    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
}

/** 页面底色：顶上抹一层很淡的冰蓝，往下化开 */
@Composable
fun bgBrush(): Brush {
    val cs = MaterialTheme.colorScheme
    return if (isDarkUi()) {
        Brush.verticalGradient(listOf(Color(0xFF121A26), cs.background))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFE6EFFA), cs.background))
    }
}

/**
 * 每一页的最外层容器：铺底色 + 顶上一团很淡的光晕。
 *
 * 光晕的作用：底色从「一块平色」变成「有空气感」—— 肉眼说不出哪变了，
 * 但会觉得整页更高级。用 `drawBehind` 画在内容底下，不参与布局。
 */
@Composable
fun ScreenSurface(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val glow = if (isDarkUi()) {
        Color(0xFF2A5B96).copy(alpha = 0.30f)
    } else {
        Color(0xFFA9CEF6).copy(alpha = 0.55f)
    }
    val cx = 0.16f
    Box(
        modifier
            .fillMaxSize()
            .background(bgBrush())
            .drawBehind {
                val c = Offset(size.width * cx, -size.height * 0.03f)
                val r = size.width * 0.95f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glow, Color.Transparent),
                        center = c,
                        radius = r,
                    ),
                    radius = r,
                    center = c,
                )
            },
        content = content,
    )
}

// MARK: - 卡片

/**
 * 主力卡片。
 *
 * 与 material3 的 [androidx.compose.material3.Card] 的区别：
 *   · 圆角更大（22dp）、投影更散更淡（iOS 那种「薄薄一层」）
 *   · 按下时整张卡缩到 0.97 再弹回来 —— 手指上有反馈，但比水波纹克制
 *   · [onClick] 传进来才可点；**关掉水波纹**（indication = null），这也是 iOS 观感的关键
 */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val dark = isDarkUi()
    val shape = RoundedCornerShape(22.dp)

    val interaction = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.972f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 900f),
        label = "cardScale",
    )

    var m = modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(
            if (dark) Modifier
            else Modifier.shadow(
                elevation = 9.dp,
                shape = shape,
                clip = false,
                ambientColor = Color(0x14213C5E),
                spotColor = Color(0x1F213C5E),
            )
        )
        .then(if (dark) Modifier.border(1.dp, cs.outlineVariant, shape) else Modifier.border(1.dp, Color(0x12000000), shape))
        .clip(shape)
        .background(cs.surface)

    if (onClick != null) {
        m = m.clickable(interactionSource = interaction, indication = null) {
            lightTick(haptic)
            onClick()
        }
    }
    Column(m.padding(contentPadding), content = content)
}

/**
 * 渐变主卡（统计页的「全部约稿」、账本页的合计）。
 * 深色下不铺纯蓝（刺眼），改成深蓝灰的暗渐变。
 */
@Composable
fun HeroCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val dark = isDarkUi()
    val shape = RoundedCornerShape(24.dp)
    val brush = if (dark) {
        Brush.linearGradient(listOf(Color(0xFF1C2E47), Color(0xFF16202E)))
    } else {
        // 三档渐变（深→中→亮），比两档更有层次
        Brush.linearGradient(listOf(Color(0xFF2968BB), Color(0xFF4E93DE), Color(0xFF6FB4F2)))
    }
    Column(
        modifier
            .fillMaxWidth()
            .then(
                if (dark) Modifier
                else Modifier.shadow(
                    elevation = 14.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = Color(0x332E74CA),
                    spotColor = Color(0x4D2E74CA),
                )
            )
            .clip(shape)
            .background(brush)
            .drawBehind {
                // 两团光斑（右上偏白、左下偏亮蓝）—— 渐变卡不再是「一块死蓝」，有光在里面。
                // ⚠️ 第一版只给了 0.20 透明度、半径又很大，实测**根本看不出来**（等于白写）。
                //    要看得见就得「小一点、亮一点」：半径收到 0.45 倍宽、白光提到 0.38。
                val c1 = Offset(size.width * 0.88f, size.height * 0.02f)
                val r1 = size.width * 0.46f
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.38f), Color.Transparent),
                        center = c1, radius = r1,
                    ),
                    radius = r1, center = c1,
                )
                val c2 = Offset(size.width * 0.02f, size.height * 1.0f)
                val r2 = size.width * 0.55f
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0xFFBFE6FF).copy(alpha = 0.34f), Color.Transparent),
                        center = c2, radius = r2,
                    ),
                    radius = r2, center = c2,
                )
            }
            .padding(18.dp),
        content = content,
    )
}

// MARK: - 标题

/** 渐变卡里三列之间的极淡竖分隔线 —— 加了它，三列数字才有「秩序感」 */
@Composable
fun HeroDivider() {
    Box(
        Modifier
            .padding(horizontal = 2.dp)
            .width(1.dp)
            .height(30.dp)
            .background(Color.White.copy(alpha = 0.20f))
    )
}

/** 页面大标题（左对齐、大号加粗），右边留一小块给圆形按钮 */
@Composable
fun LargeTitle(
    title: String,
    caption: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (caption != null) {
                Text(
                    caption,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        trailing()
    }
}

/**
 * 会「缩」的大标题（iOS Large Title 那种）。
 *
 * 列表往上滚 → 标题从 27sp 收到 20sp、副标题淡掉、上下留白收紧。
 * 这是最容易被一眼看出来的「iOS 感」，而且纯观感、不改任何数据。
 *
 * 用法：把 `rememberLazyListState()` 的 state 同时喂给 `rememberCollapseFraction` 和 LazyColumn。
 */
@Composable
fun rememberCollapseFraction(state: LazyListState, span: Int = 150): Float =
    if (state.firstVisibleItemIndex > 0) 1f
    else (state.firstVisibleItemScrollOffset.toFloat() / span).coerceIn(0f, 1f)

@Composable
fun CollapsibleLargeTitle(
    title: String,
    collapse: Float,
    caption: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    val size = (27f - 7f * collapse).sp
    val capAlpha = (1f - collapse * 2.4f).coerceIn(0f, 1f)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                // ⚠️ 2026-10-05：原来是 20.dp，跟下面卡片的 16.dp 差 4 —— 整列看起来「差一点点」，
                //    这就是「说不上哪儿不齐但就是不舒服」的来源。对齐到 16。
                start = 16.dp,
                end = 12.dp,
                top = (12f - 6f * collapse).dp,
                bottom = (12f - 6f * collapse).dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = size,
                lineHeight = size * 1.22f,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5f - 0.3f * collapse).sp,
                color = cs.onSurface,
            )
            if (caption != null && capAlpha > 0.02f) {
                Text(
                    caption,
                    fontSize = 13.sp,
                    color = cs.onSurfaceVariant.copy(alpha = capAlpha),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        trailing()
    }
}

/** 小号分组标题（设置页那种灰色小字） */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 8.dp, bottom = 7.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 标题右边那个圆按钮 */
@Composable
fun CircleIconButton(icon: ImageVector, desc: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val dark = isDarkUi()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 1200f),
        label = "iconScale",
    )
    Box(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(38.dp)
            .then(if (dark) Modifier.border(1.dp, cs.outlineVariant, CircleShape) else Modifier)
            .clip(CircleShape)
            .background(if (dark) cs.surfaceVariant.copy(alpha = 0.5f) else cs.surface)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = desc, tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

// MARK: - 分段控件（iOS 的 Segmented Control）

/**
 * 「一个凹槽里一枚滑块滑过去」—— iOS 分段控件的做法。
 *
 * 之前用的是两个 material3 [androidx.compose.material3.FilterChip]，
 * 选中态各自变色，一眼就是安卓。换成一枚白色滑块在灰凹槽里滑，观感立刻不一样。
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    index: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val dark = isDarkUi()
    val track = RoundedCornerShape(50)
    val thumbColor = if (dark) Color(0xFF2C3644) else Color.White

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(track)
            .background(if (dark) cs.surfaceVariant.copy(alpha = 0.45f) else cs.surfaceVariant.copy(alpha = 0.75f))
            .padding(3.dp)
    ) {
        val slot = maxWidth / options.size
        val x by animateDpAsState(
            targetValue = slot * index,
            animationSpec = spring(dampingRatio = 0.78f, stiffness = 700f),
            label = "segThumb",
        )
        // 滑块
        Box(
            Modifier
                .offset(x = x)
                .size(slot, maxHeight)
                .then(
                    if (dark) Modifier
                    else Modifier.shadow(
                        elevation = 5.dp,
                        shape = track,
                        clip = false,
                        ambientColor = Color(0x1A000000),
                        spotColor = Color(0x26000000),
                    )
                )
                .clip(track)
                .background(thumbColor)
        )
        // 文字层（滑块从它们背后滑过）
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, t ->
                val sel = i == index
                val c by animateColorAsState(
                    targetValue = if (sel) cs.onSurface else cs.onSurfaceVariant,
                    animationSpec = tween(180),
                    label = "segText$i",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(track)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        t,
                        fontSize = 13.sp,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        color = c,
                    )
                }
            }
        }
    }
}

// MARK: - 分组列表（iOS 的 inset-grouped）

/** 一整块圆角，里面塞几行 —— 「设置」页的灵魂 */
@Composable
fun InsetGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    val dark = isDarkUi()
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .fillMaxWidth()
            .then(
                if (dark) Modifier.border(1.dp, cs.outlineVariant, shape)
                else Modifier.shadow(
                    elevation = 8.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = Color(0x12213C5E),
                    spotColor = Color(0x1A213C5E),
                )
            )
            .clip(shape)
            .background(cs.surface)
            .padding(vertical = 3.dp),
        content = content,
    )
}

/**
 * 分组里的一行。左边可以给个小图标方块，右边随便塞（开关、按钮、箭头）。
 * 按下时整行淡淡地亮一下（水波纹也关掉了）。
 */
@Composable
fun GroupRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    divider: Boolean = true,
    /**
     * 右侧那坨（选项胶囊之类）**是不是挪到下一行**。
     *
     * ⚠️ 为什么需要它（2026-10-03 逸风截图报的 bug）：
     *    右边塞的是三颗胶囊时，英文的「Follow System / Light / Dark」宽度能吃掉整行，
     *    而中间那列文字是 `weight(1f)` —— 它拿的是**剩下来的宽度**，
     *    剩到接近 0 时，标题和副标题就变成「一个字母一行」，
     *    整个设置页被撑成一列竖排字（截图里就是那样）。
     *    中文的「跟随系统 / 浅色 / 暗色」三颗加起来窄，侥幸没露；一换英文立刻现原形。
     *    开着这个开关 = 选项自己占一行，标题那列拿到整行宽度，任何语言都不会再挤。
     */
    stackTrailing: Boolean = false,
    /**
     * 副标题（那句长说明）**是不是挪到下面独占一行**。
     *
     * 2026-10-03 逸风审美反馈：「三颗胶囊另起一行看着不和谐」（那版是 stackTrailing）。
     * 真正该挪下去的是**副标题**，不是胶囊 ——
     * 因为挤爆这一行的元凶是副标题那句长文案，它跟胶囊抢同一行的宽度：
     *   标题（短：深浅色 / Light / Dark）+ 胶囊（右侧）**同一行** ≈ 250dp，放得下；
     *   再把「「跟随系统」= 手机切深色，App 跟着深色…」塞进中间那列 → 立刻出界。
     * 所以规矩定成：**短标题跟控件同一行（跟上面「模式」那行一个规矩），长说明另起一行通铺**。
     */
    stackSubtitle: Boolean = false,
    trailing: @Composable () -> Unit = {},
    onClick: (() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg by animateColorAsState(
        targetValue = if (pressed) cs.primary.copy(alpha = 0.07f) else Color.Transparent,
        animationSpec = tween(120),
        label = "rowBg",
    )
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(bg)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(interactionSource = interaction, indication = null) { onClick() }
                    } else Modifier
                )
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 13.dp,
                    // 选项另起一行时，这段底下留窄一点，不然两段之间会空出一大块
                    bottom = if (stackTrailing || stackSubtitle) 4.dp else 13.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(cs.primary.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = cs.primary, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = cs.onSurface)
                if (subtitle != null && !stackSubtitle) {
                    Text(
                        subtitle,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
            // stackTrailing 时**不在这儿**塞 —— 它会把上面那列文字挤没（见参数注释）
            if (!stackTrailing) trailing()
        }
        // 副标题独占一行：通铺到整行宽（跟标题文字左对齐），
        // 这样标题跟控件的宽度就宽裕了，长说明也能正常折行、不再一字一行
        if (stackSubtitle && subtitle != null) {
            Text(
                subtitle,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = if (icon != null) 58.dp else 16.dp,
                    end = 16.dp,
                    top = 2.dp,
                    bottom = 13.dp,
                ),
            )
        }
        // 选项自己那一行：左边缘跟正文对齐（有图标就对齐到文字起点），靠右摆
        if (stackTrailing) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (icon != null) 58.dp else 16.dp,
                        end = 16.dp,
                        bottom = 13.dp,
                    ),
                horizontalArrangement = Arrangement.End,
            ) { trailing() }
        }
        if (divider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = if (icon != null) 58.dp else 16.dp, end = 16.dp)
                    .height(1.dp)
                    .background(cs.outlineVariant)
            )
        }
    }
}

// MARK: - 画师头像

private val AVATAR_COLORS = listOf(
    Color(0xFF3B82F6) to Color(0xFF8CC0FF),
    Color(0xFF14B8A6) to Color(0xFF6EE7D8),
    Color(0xFF8B5CF6) to Color(0xFFC4B5FD),
    Color(0xFFF97316) to Color(0xFFFDBA74),
    Color(0xFFEC4899) to Color(0xFFF9A8D4),
    Color(0xFF0EA5E9) to Color(0xFF7DD3FC),
)

/**
 * 名字首字 + 渐变小圆 —— 同一个名字永远同一个颜色（按名字哈希取），
 * 所以「星野」每次都是那个色，看久了能认人。
 */
@Composable
fun AvatarBubble(name: String, size: Dp = 44.dp) {
    val h = (name.hashCode().toLong() and 0x7FFFFFFFL).toInt()
    val (a, b) = AVATAR_COLORS[h % AVATAR_COLORS.size]
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(a, b))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).ifBlank { "?" },
            color = Color.White,
            fontSize = (size.value / 2.5f).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

// MARK: - 细进度条 / 空状态

/** 统计页用的细横条：按比例填一段渐变 */
@Composable
fun ThinTrack(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val f by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "track",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color)))
        )
    }
}

/** 列表空了的时候：一行灰字，居中 */
@Composable
fun EmptyHint(text: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
