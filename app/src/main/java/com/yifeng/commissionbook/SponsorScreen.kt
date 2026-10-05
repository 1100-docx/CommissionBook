package com.yifeng.commissionbook

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// =====================================================================
// 支持作者（2026-10-06 加）
//
// 逸风 2026-10-06 的原话：「要不要给 Android 版本加上赞助功能」。
//
// 做成什么样，是照他自己认的那条线来的 —— **安静**：
//   · 入口只在「设置」页最下面一行，主界面 / 账本页 / 启动页一个字都不出现；
//   · **不做任何计数**（没有「已收到 ¥N」这种字），也不做「谁支持过」的名单 ——
//     收不到的时候，计数就是在提醒他「没人给」；
//   · 文案里先把最重那句放在前头：「不给也完全没关系」。
//
// ⚠️ **只有安卓版有这一页**。iOS 那边这类功能必须走 App Store 的内购
//    （要抽成、要开发者账号、审核会拒），所以 iOS 版一行都没加 —— 别以为是漏做了。
//
// ⚠️ 收款码是**一张静态图片**（res/drawable/sponsor_qr.png，微信赞赏码）：
//    打开这一页不联网、不上传任何东西，App 也不知道谁扫过 ——
//    这句话同时写在隐私政策里（privacy_support_body），两边别改岔了。
// =====================================================================

@Composable
fun SponsorScreen(onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current

    BackHandler { onBack() }

    ScreenSurface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {

            // 顶栏：返回 + 标题（跟帮助页同一套排法，别两个页面两种动静）
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIconButton(Icons.AutoMirrored.Outlined.ArrowBack, AppCtx.s(R.string.common_back)) {
                    lightTick(haptic)
                    onBack()
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        AppCtx.s(R.string.sponsor_title),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = cs.onSurface,
                    )
                    Text(
                        AppCtx.s(R.string.sponsor_subtitle),
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
                // 码。微信赞赏码是张方图（白底 + 金色波浪 + 小狐狸），
                // 直接铺出来就很好看，别在外面再套一层白卡片 —— 会显得脏。
                SoftCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.sponsor_qr),
                            contentDescription = AppCtx.s(R.string.sponsor_title),
                            modifier = Modifier
                                .width(232.dp)
                                .clip(RoundedCornerShape(14.dp)),
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            AppCtx.s(R.string.sponsor_qr_hint),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = cs.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                SoftCard(Modifier.fillMaxWidth()) {
                    Text(
                        AppCtx.s(R.string.sponsor_thanks),
                        fontSize = 13.sp,
                        lineHeight = 21.sp,
                        color = cs.onSurface,
                    )
                }

                SoftCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            AppCtx.s(R.string.sponsor_no_pay),
                            fontSize = 12.5.sp,
                            lineHeight = 19.sp,
                            color = cs.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            AppCtx.s(R.string.sponsor_privacy),
                            fontSize = 12.5.sp,
                            lineHeight = 19.sp,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
