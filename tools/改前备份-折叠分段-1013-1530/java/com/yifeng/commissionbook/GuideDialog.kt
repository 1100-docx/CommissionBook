package com.yifeng.commissionbook

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 首启**最后一层**：选完使用模式之后，告诉人「用法都收在设置里」（2026-10-01 加）。
 *
 * 起因是逸风那句：「建议出帮助选项，因为后续功能越多，用户越难以自己摸索」——
 * 光有帮助页不够，新装的人**不知道它存在**。所以模式问完再补一句指路。
 *
 * 时机：政策同意完 + 人没被锁屏拦着 + 模式已经问过（[AppState.modeChosen]）
 *      + 从没弹过（[AppState.guideShown]）。只弹一次。
 * ⚠️ 跟模式弹窗同一个道理：`guideShown` 是**新键**，所以老用户升上来也会补看一次。
 *
 * 长得跟 [ModePickerDialog] 一套手法：底色淡入 + 卡片轻微放大 + 顶边一道淡高光，
 * 按钮按下去缩一下（同一个工程里的老规矩，不硬切）。
 */
@Composable
fun GuideDialog(
    onOpenHelp: () -> Unit,
    onDone: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val dark = isDarkUi()
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(28.dp)

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val fade by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(200),
        label = "gFade",
    )
    val pop by animateFloatAsState(
        targetValue = if (shown) 1f else 0.93f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 520f),
        label = "gPop",
    )

    Dialog(
        // 点旁边空白 / 按返回 = 「知道了」（也算提示过了，别反复烦人）
        onDismissRequest = onDone,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.34f * fade)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .padding(horizontal = 28.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = pop
                        scaleY = pop
                        alpha = fade
                    }
                    .shadow(
                        elevation = 26.dp,
                        shape = shape,
                        clip = false,
                        ambientColor = Color(0x1F1B3A5E),
                        spotColor = Color(0x33213C5E),
                    )
                    .clip(shape)
                    .background(cs.surface)
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (dark) 0.10f else 0.90f),
                                Color.Transparent,
                            )
                        ),
                        shape = shape,
                    )
                    .padding(horizontal = 22.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(IceBlueLight, IceBlue))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.MenuBook,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Spacer(Modifier.height(15.dp))
                Text(
                    AppCtx.s(R.string.help_not_sure_how),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface,
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    AppCtx.s(R.string.help_more_features_coming) +
                        AppCtx.s(R.string.help_settings_help_docs),
                    fontSize = 12.5.sp,
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp,
                )

                Spacer(Modifier.height(20.dp))
                // 主按钮：实心冰蓝（这个弹窗里只有它是「行动」，别的都靠边站）
                PrimaryPill(
                    text = AppCtx.s(R.string.help_go_check_now),
                    onClick = { lightTick(haptic); onOpenHelp() },
                )

                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { lightTick(haptic); onDone() }) {
                    Text(AppCtx.s(R.string.common_got_it), fontSize = 13.sp, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}

/** 主按钮：一枚实心冰蓝胶囊，按下去缩一下再回弹（跟底栏药丸一个脾气） */
@Composable
private fun PrimaryPill(text: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 900f),
        label = "pillScale",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(15.dp))
            .background(Brush.horizontalGradient(listOf(IceBlueLight, IceBlue)))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    }
}
