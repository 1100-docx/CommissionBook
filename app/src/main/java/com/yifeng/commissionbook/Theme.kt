package com.yifeng.commissionbook

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// MARK: - 颜色
//
// 主题还是「冰蓝」那一套（跟 iOS 版同源），但 2026-09-29 逸风说要「iOS 那种感觉、更现代」，
// 所以底色从死白改成带一点点蓝的浅灰（像 iOS 的分组背景），
// 卡片纯白浮在上面 + 很淡的投影 —— 靠「白卡浮在淡底上」拉开层次，而不是靠边框。

val IceBlue = Color(0xFF2E74CA)
val IceBlueLight = Color(0xFF8AB6F0)

private val Light = lightColorScheme(
    primary = IceBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEAFB),
    onPrimaryContainer = Color(0xFF0E3255),
    secondary = Color(0xFF5B6B7C),
    secondaryContainer = Color(0xFFDCEAFB),
    onSecondaryContainer = Color(0xFF10375F),
    tertiary = Color(0xFF3FBFA8),
    background = Color(0xFFF1F5FA),
    onBackground = Color(0xFF0F151C),
    surface = Color.White,
    onSurface = Color(0xFF0F151C),
    surfaceVariant = Color(0xFFDDE5EF),
    onSurfaceVariant = Color(0xFF5C6875),
    outline = Color(0xFFC3CFDC),
    outlineVariant = Color(0xFFE3EAF2),
    error = Color(0xFFE0464B),
    onError = Color.White,
)

private val Dark = darkColorScheme(
    primary = IceBlueLight,
    onPrimary = Color(0xFF06213F),
    primaryContainer = Color(0xFF1D3A5C),
    onPrimaryContainer = Color(0xFFCFE2FA),
    secondary = Color(0xFF9FB0C0),
    secondaryContainer = Color(0xFF1D3A5C),
    onSecondaryContainer = Color(0xFFCFE2FA),
    tertiary = Color(0xFF5BD3BC),
    background = Color(0xFF0B0F15),
    onBackground = Color(0xFFE8EDF3),
    surface = Color(0xFF151B24),
    onSurface = Color(0xFFE8EDF3),
    surfaceVariant = Color(0xFF242D38),
    onSurfaceVariant = Color(0xFF9BA8B6),
    outline = Color(0xFF3A4552),
    outlineVariant = Color(0xFF232C36),
    error = Color(0xFFFF6B6E),
    onError = Color(0xFF3A0A0C),
)

// MARK: - 形状 / 字体
//
// iOS 的观感很大一半来自「圆角大 + 数字粗 + 小字灰」。
// 圆角统一放大（卡片 22dp），正文小一号并压低对比度，标题加粗。

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val BaseType = Typography()

private val AppType = BaseType.copy(
    // 页面大标题（iOS 的 Large Title 那种感觉）
    headlineSmall = BaseType.headlineSmall.copy(
        fontSize = 27.sp,
        lineHeight = 33.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = BaseType.headlineMedium.copy(
        fontSize = 30.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.6).sp,
    ),
    titleLarge = BaseType.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaseType.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyMedium = BaseType.bodyMedium.copy(lineHeight = 20.sp),
)

@Composable
fun AppTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        shapes = AppShapes,
        typography = AppType,
        content = content,
    )
}
