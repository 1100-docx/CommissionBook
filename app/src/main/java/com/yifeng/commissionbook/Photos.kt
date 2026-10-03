package com.yifeng.commissionbook

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 参考图（2026-10-03 加，逸风提的：「买家约稿的时候可以添加图片」）。
 *
 * 三条硬规矩，改代码前先读：
 *
 * ① **不吃相册权限**。选图走的是系统自带的「图片选择器」
 *    （安卓 Photo Picker，AndroidX 的 `PickVisualMedia`）。
 *    它由系统弹、你挑哪张才把哪张交给 App，所以：
 *      - 不用申请 READ_MEDIA_IMAGES / 存储权限（清单里一个都没加）
 *      - App 也**看不到**你没选的那些图
 *    隐私政策里「不申请相册权限」那句靠这个立着，别改成自己去翻相册。
 *
 * ② **图片只在本机**。压完存进 App 私有目录 `files/photos/`，
 *    别的 App 读不到，卸载 App 就一起没了（跟数据同命）。
 *    唯一的例外是**导出备份**：那时图会以 base64 写进备份 JSON（见 Backup.kt）。
 *
 * ③ **存之前先压**。手机直出的图动辄 3–5MB，json 里 base64 还要再大三分之一；
 *    压到长边 [MAX_SIDE]、JPEG [QUALITY] 之后一张约 150–400KB，
 *    备份文件才不至于几百 MB。
 */
object Photos {

    /** 最多几张（表单里那个「+」到 6 张就变灰） */
    const val MAX_COUNT = 6

    /** 长边最多多少像素。1400 看线稿细节够用，再大只是白占地方 */
    const val MAX_SIDE = 1400

    /** JPEG 质量。82 是肉眼几乎看不出降质的那一档 */
    const val QUALITY = 82

    /**
     * 把系统选择器给的 uri 读成一份压缩过的 JPEG 字节。
     *
     * ⚠️ 两个机型坑：
     * ① **API 28 起走 ImageDecoder** —— 它会自动把相机拍的图按 EXIF 方向摆正。
     *    用 BitmapFactory 的老路子读，横竖屏拍的图会躺倒（安卓老毛病）。
     * ② `allocator` 必须是 `ALLOCATOR_SOFTWARE`：默认给的硬件位图
     *    **读不了像素**，一 `compress()` 就抛异常，图存不下来。
     *    API 26/27 没有 ImageDecoder（也顺便没有 EXIF 矫正），只能两步采样凑合。
     */
    fun compress(context: Context, uri: Uri): ByteArray? = runCatching {
        val bmp = decode(context, uri) ?: return null
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        bmp.recycle()
        out.toByteArray()
    }.getOrNull()

    private fun decode(context: Context, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val src = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val side = max(w, h)
                if (side > MAX_SIDE) {
                    val scale = MAX_SIDE.toFloat() / side
                    decoder.setTargetSize(
                        (w * scale).roundToInt().coerceAtLeast(1),
                        (h * scale).roundToInt().coerceAtLeast(1),
                    )
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        // 老机型：先只读尺寸，再按 2 的幂采样解码
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val side = max(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (side / sample > MAX_SIDE * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    /**
     * 缩略图：给表单/查看器里那排小方块用。
     * 每次都小尺寸解一遍（不缓存位图 —— 一条单最多 6 张，重解一次几毫秒，
     * 缓存反而要自己管内存）。
     */
    fun thumbnail(path: String, maxSide: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val side = max(bounds.outWidth, bounds.outHeight)
        if (side <= 0) return null
        var sample = 1
        while (side / sample > maxSide * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(path, opts)
    }.getOrNull()

    /**
     * 文件名消毒。
     *
     * 备份 JSON 是可以从外面塞进来的（他还可能拿一份别人给的备份来恢复），
     * 里面的文件名要是写成 `../../commissions.json`，直接往磁盘上写就等于**让人改我的数据**。
     * 所以只留字母数字和 `. _ -`，别的全扔掉。
     */
    fun safeName(name: String): String =
        name.filter { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }.take(64)
}
