package com.yifeng.commissionbook

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

/**
 * 统计。
 *
 * ⚠️ 跟 iOS 版一条心得：**这里拿的是全部数据，`archived` 不过滤**。
 * 归档 = 收进档案，不是「没发生过」；过滤掉它，历史上的总数就会凭空变小。
 */
@Composable
fun StatsScreen(modifier: Modifier, state: AppState) {

    val all = state.items
    val total = all.sumOf { it.total }
    val paid = all.sumOf { it.deposit }
    val unpaid = (total - paid).coerceAtLeast(0.0)
    val active = all.count { !it.archived }
    val archived = all.count { it.archived }

    // 滚动位置 → 大标题跟着缩
    val scroll = rememberScrollState()
    val collapse = (scroll.value / 150f).coerceIn(0f, 1f)

    ScreenSurface(modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll),
        ) {
            CollapsibleLargeTitle(
                title = "统计",
                collapse = collapse,
                caption = "全部数据，含已归档",
            )

            Column(
                Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ① 总账（渐变主卡）
                HeroCard {
                    HeroLabel("全部约稿（含已归档）")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        money(total),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        StatCell("已付", money(paid), Modifier.weight(1f))
                        HeroDivider()
                        StatCell("未付", money(unpaid), Modifier.weight(1f))
                        HeroDivider()
                        StatCell("单数", "${all.size} 单", Modifier.weight(1f))
                    }
                }

                // ② 按进度（每条一根细条，一眼看出哪一档最多）
                SoftCard {
                    CardTitle("按进度")
                    Spacer(Modifier.height(12.dp))
                    // 条长 = 这一档占**全部**的比例。
                    // （之前是「占最大那一档」—— 那样只有 2 单的档也会顶满，
                    //   看着像 100%，其实说的是假话。）
                    val denom = all.size.coerceAtLeast(1)
                    CommissionStatus.entries.forEach { s ->
                        val n = all.count { it.status == s }
                        val c = statusColor(s)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(c))
                            Spacer(Modifier.width(9.dp))
                            Text(s.label, fontSize = 13.sp, modifier = Modifier.width(62.dp))
                            Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                ThinTrack(n.toFloat() / denom.toFloat(), c)
                            }
                            Text("$n 单", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.height(11.dp))
                    }
                    Text(
                        "进行中 $active 单 · 已归档 $archived 单（已归档的也在这上面）",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // ③ 近 6 个月柱状图
                SoftCard {
                    CardTitle("近 6 个月下单金额")
                    Spacer(Modifier.height(14.dp))
                    val bars = last6Months(all)
                    val maxV = (bars.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(1.0)
                    val barColor = MaterialTheme.colorScheme.primary
                    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.045f)
                    // 柱子「长出来」：进页面时从 0 长到实际高度；数据一变（切页/换数据）也重新长一次。
                    // ⚠️ 别直接写 animateFloatAsState(1f) —— 初始值就是 1，它不会动。
                    //    要先给 0 再在 LaunchedEffect 里改成 1。
                    var growTarget by remember(bars) { mutableStateOf(0f) }
                    LaunchedEffect(bars) { growTarget = 1f }
                    val grow by animateFloatAsState(
                        targetValue = growTarget,
                        animationSpec = tween(durationMillis = 620, easing = FastOutSlowInEasing),
                        label = "barsGrow",
                    )
                    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                        val n = bars.size
                        val slot = size.width / n
                        val barW = slot * 0.5f
                        bars.forEachIndexed { i, (_, v) ->
                            val h = (v / maxV).toFloat() * (size.height - 8f) * grow
                            val x = slot * i + (slot - barW) / 2f
                            val r = CornerRadius(barW / 2f, barW / 2f)
                            drawRoundRect(trackColor, Offset(x, 0f), Size(barW, size.height), r)
                            if (h > 0f) {
                                drawRoundRect(
                                    brush = Brush.verticalGradient(
                                        listOf(barColor, barColor.copy(alpha = 0.65f)),
                                        startY = size.height - h,
                                        endY = size.height,
                                    ),
                                    topLeft = Offset(x, size.height - h),
                                    size = Size(barW, h),
                                    cornerRadius = r,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        bars.forEach { (label, v) ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    if (v > 0) money(v).removePrefix("¥") else "-",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }

                // ④ 花得最多的画师
                val top = all.groupBy { it.artist }
                    .map { (name, list) -> name.ifBlank { "（没写画师）" } to list.sumOf { it.total } }
                    .sortedByDescending { it.second }
                    .take(5)
                if (top.isNotEmpty()) {
                    SoftCard {
                        CardTitle("花得最多的画师")
                        Spacer(Modifier.height(11.dp))
                        val maxTop = top.first().second.coerceAtLeast(1.0)
                        top.forEach { (name, v) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AvatarBubble(name, size = 30.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.height(5.dp))
                                    ThinTrack((v / maxTop).toFloat(), MaterialTheme.colorScheme.primary)
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(money(v), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.height(13.dp))
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun HeroLabel(text: String) {
    Text(text, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
}

/** 近 6 个月，每个月下单的总金额：[("4月", 300.0), ...] */
private fun last6Months(items: List<Commission>): List<Pair<String, Double>> {
    val cal = Calendar.getInstance()
    val out = ArrayList<Pair<String, Double>>()
    // 从 5 个月前走到这个月
    cal.add(Calendar.MONTH, -5)
    repeat(6) {
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH)
        val sum = items.filter {
            val c = Calendar.getInstance().apply { timeInMillis = it.dateMillis }
            c.get(Calendar.YEAR) == y && c.get(Calendar.MONTH) == m
        }.sumOf { it.total }
        out.add("${m + 1}月" to sum)
        cal.add(Calendar.MONTH, 1)
    }
    return out
}
