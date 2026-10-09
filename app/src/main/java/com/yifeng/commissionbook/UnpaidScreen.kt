package com.yifeng.commissionbook

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 未结清单（2026-10-09 加，#1 逸风 10-06 提过、一直没做的那条）。
 *
 * 一句话：**把所有还欠着钱的单拉成一张清单，顶上给合计。**
 *
 * 为什么单独开一页、而不是塞在统计页里：
 *   统计页回答的是「我这一年花了多少」；这一页回答的是「**现在谁还欠我钱 / 我还欠谁**」——
 *   一个往回看、一个管眼下，混在一张卡里两边都不好用。
 *
 * ⚠️ 含**已归档**的单（见 [AppState.unpaidItems] 的注释）：归档只是收进档案，
 *    钱还没到账就不该从这张清单上消失。这类条目会挂一枚「已归档」小标，
 *    让人一眼看出「这单我收进档案了，可钱还没结」。
 */
@Composable
fun UnpaidScreen(state: AppState, onBack: () -> Unit) {

    val cs = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    // 系统返回键也走同一条路（不然会直接退出 App，体验很怪）
    BackHandler { onBack() }

    val list = state.unpaidItems
    val total = state.unpaidTotal
    val mode = state.appMode

    /** 清单文本：发微信给对面 / 发给自己备忘都用得上（不做「导出文件」，太重） */
    fun plainText(): String {
        val sb = StringBuilder()
        sb.append(
            AppCtx.s(
                R.string.unpaid_copy_header,
                AppCtx.s(R.string.unpaid_title),
                list.size,
                money(total),
            )
        )
        list.forEachIndexed { i, c ->
            val name = c.artist.ifBlank { Terms.otherBlank(mode) }
            sb.append("\n").append(i + 1).append(". ").append(name)
            if (c.title.isNotBlank()) sb.append(" ｜ ").append(c.title)
            sb.append(" ｜ ").append(Terms.unpaid(mode)).append(" ").append(money(c.unpaid))
            sb.append(
                AppCtx.s(
                    R.string.unpaid_copy_paid_part,
                    Terms.paid(mode),
                    money(c.deposit),
                    AppCtx.s(R.string.ledger_total_price),
                    money(c.total),
                )
            )
            deadlineText(c)?.let { (t, late) ->
                sb.append(" ｜ ").append(t).append(if (late) AppCtx.s(R.string.unpaid_copy_late_mark) else "")
            }
            if (c.archived) sb.append(" ｜ ").append(AppCtx.s(R.string.unpaid_archived_tag))
        }
        return sb.toString()
    }

    ScreenSurface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {

            // 顶栏：返回 + 标题（跟隐私政策 / 崩溃记录一套出场方式）
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
                    Text(
                        AppCtx.s(R.string.unpaid_title),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = cs.onSurface,
                    )
                    Text(
                        AppCtx.s(R.string.unpaid_subtitle),
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            if (list.isEmpty()) {
                // 空态也说得明白点：「没有未结清的单」听着像报错，加后半句才像个好消息
                // ⚠️ 后半句跟着 mode 走（买家=付清 / 画师=收齐），别写死成画师口气
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    EmptyHint(Terms.unpaidEmpty(mode))
                }
            } else {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // ① 合计（渐变主卡）
                    HeroCard {
                        Text(
                            AppCtx.s(R.string.unpaid_hero_label),
                            fontSize = 12.sp,
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                        )
                        Spacer(Modifier.height(4.dp))
                        RollingMoney(total, fontSize = 32.sp, color = androidx.compose.ui.graphics.Color.White)
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                AppCtx.s(R.string.unpaid_hero_count, list.size),
                                fontSize = 12.sp,
                                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                            )
                            Spacer(Modifier.weight(1f))
                            // 复制清单：给他「粘到微信里就能发」的那一步（不做导出文件）
                            TextButton(onClick = {
                                lightTick(haptic)
                                clipboard.setText(AnnotatedString(plainText()))
                                Toast.makeText(
                                    context,
                                    AppCtx.s(R.string.unpaid_copied),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }) {
                                androidx.compose.material3.Icon(
                                    Icons.Outlined.ContentCopy,
                                    contentDescription = null,
                                    tint = androidx.compose.ui.graphics.Color.White,
                                    modifier = Modifier.width(16.dp).height(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    AppCtx.s(R.string.unpaid_copy),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = androidx.compose.ui.graphics.Color.White,
                                )
                            }
                        }
                    }

                    // ② 一条一张卡：一眼看清「谁、哪单、还欠多少、什么时候该结」
                    list.forEach { c ->
                        UnpaidRow(c, mode)
                    }

                    Spacer(Modifier.height(28.dp))
                }
            }

            // 底部给系统导航栏留位置（安卓 15 起边到边，不留会贴着金刚键）
            Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars))
        }
    }
}

/** 未结清单里的一条 */
@Composable
private fun UnpaidRow(c: Commission, mode: AppMode) {
    val cs = MaterialTheme.colorScheme
    val name = c.artist.ifBlank { Terms.otherBlank(mode) }
    val dl = deadlineText(c)

    SoftCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarBubble(name, size = 36.dp)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    if (c.archived) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier
                                .background(
                                    cs.onSurfaceVariant.copy(alpha = 0.12f),
                                    androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                                )
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                AppCtx.s(R.string.unpaid_archived_tag),
                                fontSize = 10.sp,
                                color = cs.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (c.title.isNotBlank()) {
                    Text(
                        c.title,
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        AppCtx.s(
                            R.string.unpaid_paid_of_total,
                            Terms.paid(mode),
                            money(c.deposit),
                            money(c.total),
                        ),
                        fontSize = 11.sp,
                        color = cs.onSurfaceVariant,
                        style = tnum,
                    )
                    dl?.let { (t, late) ->
                        Spacer(Modifier.width(8.dp))
                        Text(
                            t,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (late) cs.error else cs.onSurfaceVariant,
                            style = tnum,
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    money(c.unpaid),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.primary,
                    style = tnum,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    Terms.unpaid(mode),
                    fontSize = 10.sp,
                    color = cs.onSurfaceVariant,
                )
            }
        }
    }
}
