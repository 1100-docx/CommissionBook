package com.yifeng.commissionbook

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// =====================================================================
// 帮助 · 功能一览（2026-10-01 加）
//
// 逸风的原话：「建议出帮助选项，因为后续功能越多，用户越难以自己摸索」。
//
// 做法上就一条原则：「不要写说明书」。
// 每条只回答两个问题 ——「这功能是干嘛的」和「从哪儿点进去」。
// 路径那行统一用蓝色 + 「在哪：」开头，扫一眼就能找到。
//
// ⚠️ 内容跟着「模式」走：画师模式才有「报价计算器 / 加价项」那两块，
//    话术那一条也会自己换成「催尾款」叫法。
// =====================================================================

data class HelpItem(
    val title: String,
    /** 这功能是干嘛的 */
    val what: String,
    /** 从哪儿点进去 */
    val where: String,
)

data class HelpGroup(val name: String, val items: List<HelpItem>)

/** 按当前模式生成目录。加新功能的时候只动这里，界面不用碰。 */
fun helpGroups(mode: AppMode): List<HelpGroup> {
    val other = Terms.other(mode)          // 买家模式=画师，画师模式=客户
    val paid = Terms.paid(mode)            // 已付 / 已收定金
    val unpaid = Terms.unpaid(mode)        // 未付 / 待收尾款

    val groups = mutableListOf(
        HelpGroup(
            AppCtx.s(R.string.ledger_add_entry),
            listOf(
                HelpItem(
                    AppCtx.s(R.string.ledger_add_one),
                    AppCtx.s(R.string.ledger_entry_fields, other, paid),
                    AppCtx.s(R.string.ledger_plus_top_right),
                ),
                HelpItem(
                    AppCtx.s(R.string.ledger_edit_entry),
                    AppCtx.s(R.string.ledger_edit_tap_card),
                    AppCtx.s(R.string.ledger_tap_anywhere_card),
                ),
                HelpItem(
                    AppCtx.s(R.string.ledger_archive_delete),
                    AppCtx.s(R.string.ledger_archive_delete_desc),
                    AppCtx.s(R.string.ledger_card_menu_archive),
                ),
                HelpItem(
                    AppCtx.s(R.string.ledger_find_entry),
                    AppCtx.s(R.string.ledger_search_filter_sort),
                    AppCtx.s(R.string.ledger_search_filter_sort_ui),
                ),
                HelpItem(
                    AppCtx.s(R.string.ledger_bulk_actions),
                    AppCtx.s(R.string.ledger_bulk_actions_desc),
                    AppCtx.s(R.string.ledger_bulk_select_ui),
                ),
                HelpItem(
                    AppCtx.s(R.string.ledger_sample_entries),
                    AppCtx.s(R.string.ledger_sample_entries_desc),
                    AppCtx.s(R.string.ledger_sample_banner),
                ),
            ),
        ),
        HelpGroup(
            AppCtx.s(R.string.ledger_status),
            listOf(
                HelpItem(
                    AppCtx.s(R.string.ledger_advance_one),
                    AppCtx.s(R.string.ledger_status_flow_hint),
                    AppCtx.s(R.string.ledger_arrow_right_hint),
                ),
                HelpItem(
                    AppCtx.s(R.string.ledger_revert_one),
                    AppCtx.s(R.string.ledger_revert_hint),
                    AppCtx.s(R.string.ledger_arrow_left_hint),
                ),
            ),
        ),
        HelpGroup(
            AppCtx.s(R.string.script_title),
            listOf(
                HelpItem(
                    nudgeTitle(mode),
                    AppCtx.s(R.string.script_tone_hint),
                    AppCtx.s(R.string.script_entry_hint, nudgeTitle(mode)),
                ),
                HelpItem(
                    AppCtx.s(R.string.script_balance_how),
                    AppCtx.s(R.string.script_amount_auto_hint, paid),
                    AppCtx.s(R.string.script_amount_deadline_hint),
                ),
            ),
        ),
    )

    // 画师模式才有：报价计算器 + 加价项
    if (mode == AppMode.ARTIST) {
        groups += HelpGroup(
            AppCtx.s(R.string.artist_only),
            listOf(
                HelpItem(
                    AppCtx.s(R.string.artist_quote_calculator),
                    AppCtx.s(R.string.script_quote_flow_hint),
                    AppCtx.s(R.string.script_quote_entry_hint),
                ),
                HelpItem(
                    AppCtx.s(R.string.script_surcharge_custom),
                    AppCtx.s(R.string.script_surcharge_custom_hint),
                    AppCtx.s(R.string.script_surcharge_entry_hint),
                ),
                HelpItem(
                    AppCtx.s(R.string.script_surcharge_how),
                    AppCtx.s(R.string.script_surcharge_how_hint),
                    AppCtx.s(R.string.script_surcharge_detail_hint),
                ),
            ),
        )
    }

    groups += HelpGroup(
        AppCtx.s(R.string.common_share),
        listOf(
            HelpItem(
                AppCtx.s(R.string.ledger_share_order),
                AppCtx.s(R.string.ledger_share_order_hint, other),
                AppCtx.s(R.string.ledger_share_order_entry_hint),
            ),
            HelpItem(
                AppCtx.s(R.string.stats_full_card),
                AppCtx.s(R.string.stats_full_card_hint),
                AppCtx.s(R.string.stats_full_card_entry_hint),
            ),
        ),
    )

    groups += HelpGroup(
        AppCtx.s(R.string.stats_title),
        listOf(
            HelpItem(
                AppCtx.s(R.string.stats_annual_report),
                AppCtx.s(R.string.stats_yearly_report),
                AppCtx.s(R.string.stats_yearly_report_entry_hint),
            ),
            HelpItem(
                AppCtx.s(R.string.stats_more),
                AppCtx.s(R.string.stats_more_hint, other),
                AppCtx.s(R.string.stats_entry_hint),
            ),
        ),
    )

    groups += HelpGroup(
        AppCtx.s(R.string.settings_title),
        listOf(
            HelpItem(
                AppCtx.s(R.string.settings_usage_mode),
                AppCtx.s(R.string.settings_usage_mode_desc),
                AppCtx.s(R.string.settings_usage_mode_path),
            ),
            HelpItem(
                AppCtx.s(R.string.settings_protection),
                AppCtx.s(R.string.settings_protection_desc),
                AppCtx.s(R.string.settings_protection_path),
            ),
            // 2026-10-08 加：模糊拆出来单列一行（原来跟保护绑死，帮助页里也该说清楚）
            HelpItem(
                AppCtx.s(R.string.settings_blur),
                AppCtx.s(R.string.settings_blur_desc),
                AppCtx.s(R.string.settings_blur_path),
            ),
            HelpItem(
                AppCtx.s(R.string.settings_deadline_reminder),
                AppCtx.s(R.string.settings_deadline_reminder_desc),
                AppCtx.s(R.string.settings_deadline_reminder_path),
            ),
            HelpItem(
                AppCtx.s(R.string.settings_backup_restore),
                AppCtx.s(R.string.settings_backup_restore_desc),
                AppCtx.s(R.string.settings_backup_restore_path),
            ),
        ),
    )

    groups += HelpGroup(
        AppCtx.s(R.string.settings_about_data),
        listOf(
            HelpItem(
                AppCtx.s(R.string.settings_where_stored),
                AppCtx.s(R.string.settings_where_stored_desc),
                AppCtx.s(R.string.settings_where_stored_path),
            ),
            HelpItem(
                AppCtx.s(R.string.settings_really_offline),
                AppCtx.s(R.string.settings_really_offline_desc),
                AppCtx.s(R.string.settings_really_offline_path),
            ),
        ),
    )

    return groups
}

@Composable
fun HelpScreen(mode: AppMode, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current

    BackHandler { onBack() }

    ScreenSurface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {

            // 顶栏：返回 + 标题
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
                    Text(AppCtx.s(R.string.help_how_to_use), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
                    Text(
                        AppCtx.s(R.string.help_how_to_use_desc),
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                helpGroups(mode).forEach { g ->
                    Column {
                        SectionLabel(g.name)
                        SoftCard(Modifier.fillMaxWidth()) {
                            g.items.forEachIndexed { i, it ->
                                if (i > 0) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 11.dp)
                                            .height(1.dp)
                                            .background(cs.onSurfaceVariant.copy(alpha = 0.12f)),
                                    )
                                }
                                Column {
                                    Text(
                                        it.title,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = cs.onSurface,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        it.what,
                                        fontSize = 12.5.sp,
                                        lineHeight = 19.sp,
                                        color = cs.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.Top) {
                                        Icon(
                                            Icons.Outlined.Place,
                                            null,
                                            tint = cs.primary,
                                            modifier = Modifier
                                                .padding(top = 2.dp)
                                                .height(13.dp)
                                                .width(13.dp),
                                        )
                                        Spacer(Modifier.width(5.dp))
                                        Text(
                                            it.where,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            lineHeight = 18.sp,
                                            color = cs.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Text(
                    AppCtx.s(R.string.help_feedback_hint_2),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
