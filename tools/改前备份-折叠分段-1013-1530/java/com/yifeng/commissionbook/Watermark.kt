package com.yifeng.commissionbook

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.hypot

/*
 * 参考图水印（2026-10-07 加）。
 *
 * 逸风：「给参考图加水印功能怎么样，就像图片里的水印，用户可以自定义是否加水印以及水印内容及其颜色」
 *      —— 他发来的是一张兽设设定图，图上正是**斜排平铺**的「筱晞」两个字。
 *
 * 这个文件只管一件事：**把水印铺上去**（怎么排、字多大、颜色和透明度怎么用）。
 * 有两处要用它，所以预览和分享出去的那张**必须长得一模一样**：
 *   ① 看图页的实时预览 —— 走 [overlay]，做一张透明「水印层」盖在照片上
 *   ② 分享出去那一下 —— 走 [stamp]，同一套画法直接烧进照片
 *
 * ⚠️ 出图一定要走同一个函数：预览一套、导出另一套的话，差一点点都会被人一眼看出来。
 * ⚠️ 原图**永不改动**：不管开关怎么拨、颜色怎么调，磁盘上那张一直是干净的 ——
 *    改完参数所有图立刻跟着变，也不用「再存一遍」。
 * ⚠️ 这个文件**不许碰 Android 的界面/存储**（Bitmap/Canvas 除外）——
 *    它得能被单独读、单独想清楚；设置从外面传进来（见 [WatermarkStyle]）。
 */

/** 水印那一套参数（谁传进来谁负责存：设置页一份默认值，单条约稿要单独改就自己带一份） */
data class WatermarkStyle(
    val text: String,
    val colorArgb: Int,
    val percent: Int,
)

object Watermark {

    /** 倾斜角（度）。斜着铺像水印，正着铺像贴标签 */
    private const val ANGLE = -28f

    /** 不透明度范围（%）：太低看不见、太高把图糊死，两头都不给 */
    const val MIN_PERCENT = 5
    const val MAX_PERCENT = 80
    const val DEFAULT_PERCENT = 30

    /** 默认白 —— 深色图上是白的、浅色图上他能自己改成黑/其它色 */
    const val DEFAULT_COLOR = 0xFFFFFFFF.toInt()

    /** 开关开着、而且内容不是空白 —— 内容空着等于没开（界面里会提示一句） */
    fun active(enabled: Boolean, text: String): Boolean = enabled && text.isNotBlank()

    /** 内容空着的默认那一套（给预览用，省得到处判 null） */
    fun styleOf(text: String, colorArgb: Int, percent: Int) = WatermarkStyle(
        text = text,
        colorArgb = colorArgb,
        percent = percent.coerceIn(MIN_PERCENT, MAX_PERCENT),
    )

    /** 字号：按**短边**的 1/10 算 —— 大图上不至于小得像蚂蚁，小图上不至于糊满整张 */
    private fun textSize(w: Float, h: Float): Float = (minOf(w, h) / 10f).coerceAtLeast(12f)

    private fun paintFor(w: Float, h: Float, colorArgb: Int, percent: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorArgb
        // ⚠️ 透明度要单独设 alpha（0-255），别把 alpha 乘进颜色值里 —— 那样跟其它地方的颜色对不上
        alpha = (255 * percent.coerceIn(0, 100) / 100f).toInt()
        textSize = textSize(w, h)
        // 中文别用默认字体：有的机型默认字体没有中文字形，会出豆腐块
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }

    /** 把水印铺到画布上（画布尺寸 = w × h） */
    fun draw(canvas: Canvas, w: Float, h: Float, style: WatermarkStyle) {
        if (style.text.isBlank() || w <= 0f || h <= 0f) return
        val paint = paintFor(w, h, style.colorArgb, style.percent)

        val tw = paint.measureText(style.text)
        val fm = paint.fontMetrics
        val th = fm.descent - fm.ascent
        // 间距：横着一格多留 1/8 图宽、竖着多留 1/6 图高 —— 太密看不清图，太稀挡不住
        val stepX = tw + w / 8f
        val stepY = th + h / 6f

        canvas.save()
        // 整块一起转（不是一个字一个字转）—— 旋转中心放在画布正中，四个角才铺得匀
        canvas.rotate(ANGLE, w / 2f, h / 2f)
        // ⚠️ 旋转之后要盖满整张图，铺的范围得按**对角线**算，否则转出来四个角会空一块
        val span = hypot(w, h)
        var y = h / 2f - span / 2f
        while (y <= h / 2f + span / 2f) {
            var x = w / 2f - span / 2f - stepX    // 往左多铺一格
            while (x <= w / 2f + span / 2f) {
                canvas.drawText(style.text, x, y, paint)
                x += stepX
            }
            y += stepY
        }
        canvas.restore()
    }

    /** 一张透明的「水印层」（看图页盖在照片上用）。尺寸随便给，字会自动跟着缩。 */
    fun overlay(w: Int, h: Int, style: WatermarkStyle): Bitmap? {
        if (w <= 0 || h <= 0 || style.text.isBlank()) return null
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), w.toFloat(), h.toFloat(), style)
        return bmp
    }

    /** 把水印烧进一张**新图**（分享用）。传进来那张一个像素都不动。 */
    fun stamp(src: Bitmap, style: WatermarkStyle): Bitmap {
        if (style.text.isBlank()) return src
        val out = src.copy(Bitmap.Config.ARGB_8888, true) ?: return src
        draw(Canvas(out), out.width.toFloat(), out.height.toFloat(), style)
        return out
    }

    /** 颜色 → `#RRGGBB`（界面上显示用） */
    fun toHex(argb: Int): String = String.format("#%06X", 0xFFFFFF and argb)
}
