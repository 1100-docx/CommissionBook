package com.yifeng.commissionbook

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/*
 * 取色器（2026-10-07 加，给参考图水印挑颜色用）。
 *
 * ⚠️ **安卓没有给 App 用的系统取色器** —— iOS 那边是白送的（`ColorPicker` 一行就完事），
 *    安卓这边 Material 是**故意不做**这个组件的，也没有系统 Intent 能唤起。
 *    所以只能自己画一个：色相条 + 明暗/饱和度方块 + 十六进制值。
 *    **不引第三方取色库**（这个 App 的脾气是不加依赖，包体 2.2MB 是卖点之一）。
 *
 * 交互跟 iOS 的系统取色器尽量对齐：色相条左右拖，方块里点/拖定具体深浅；
 * 摆色值（十六进制）在中间，方便他照着填。
 */
@Composable
fun ColorPickerDialog(
    initialArgb: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // 起始色 → HSV。用 HSV 是因为取色器本来就是"色相 + 深浅"两件事，
    // RGB 在界面上没法直观地拖。
    val hsv = remember(initialArgb) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initialArgb, it) }
    }
    var hue by remember { mutableStateOf(hsv[0]) }        // 0–360
    var sat by remember { mutableStateOf(hsv[1]) }        // 0–1
    var value by remember { mutableStateOf(hsv[2]) }      // 0–1
    val picked = Color.hsv(hue, sat, value)

    fun setFrom(offset: Offset, w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return
        sat = (offset.x / w).coerceIn(0f, 1f)
        value = (1f - offset.y / h).coerceIn(0f, 1f)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 6.dp) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    AppCtx.s(R.string.wm_color_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(14.dp))

                // ① 深浅方块：底色是当前色相 → 横向白给它去饱和 → 纵向黑给它压暗
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .pointerInput(Unit) {
                            detectTapGestures { o -> setFrom(o, size.width.toFloat(), size.height.toFloat()) }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { c, _ ->
                                setFrom(c.position, size.width.toFloat(), size.height.toFloat())
                            }
                        },
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRect(Color.hsv(hue, 1f, 1f))
                        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
                        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                        // 选中的那一点：白圈套黑圈，浅色深色底上都看得见
                        val c = Offset(sat * size.width, (1f - value) * size.height)
                        drawCircle(Color.Black.copy(alpha = 0.45f), 12.dp.toPx(), c, style = Stroke(2.dp.toPx()))
                        drawCircle(Color.White, 10.dp.toPx(), c, style = Stroke(3.dp.toPx()))
                    }
                }

                Spacer(Modifier.height(14.dp))

                // ② 色相条
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .pointerInput(Unit) {
                            detectTapGestures { o ->
                                hue = (o.x / size.width).coerceIn(0f, 1f) * 360f
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { c, _ ->
                                hue = (c.position.x / size.width).coerceIn(0f, 1f) * 360f
                            }
                        },
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRect(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
                                    Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF),
                                    Color(0xFFFF0000),
                                )
                            )
                        )
                        val cx = hue / 360f * size.width
                        drawCircle(Color.Black.copy(alpha = 0.4f), 11.dp.toPx(), Offset(cx, size.height / 2), style = Stroke(2.dp.toPx()))
                        drawCircle(Color.White, 9.dp.toPx(), Offset(cx, size.height / 2), style = Stroke(3.dp.toPx()))
                    }
                }

                Spacer(Modifier.height(14.dp))

                // ③ 当前色 + 色值
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .padding(2.dp)
                    ) {
                        Box(Modifier.fillMaxSize().clip(CircleShape).background(picked))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        // 16 进制色值（他要想手填/对照别的图，看这一行就够）
                        String.format("#%06X", 0xFFFFFF and picked.toArgb()),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(AppCtx.s(R.string.common_cancel)) }
                    TextButton(onClick = { onPick(picked.toArgb()) }) {
                        Text(AppCtx.s(R.string.common_done), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
