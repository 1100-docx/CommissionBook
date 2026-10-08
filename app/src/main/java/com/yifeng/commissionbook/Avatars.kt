package com.yifeng.commissionbook

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * 画师 / 客户头像（2026-10-08 加，跟 iOS 版 `AvatarStore.swift` 同一套语义）。
 *
 * 逸风提的：「加一个自定义画师/客户头像怎么样，就像QQ头像那样」。
 *
 * ⚠️ 跟 iOS 端**必须一致**的两条约定：
 *  ① 存哪儿：设置偏好里一个键 `artistAvatars`，内容是 `{"名字": "<base64 的 JPEG>"}`。
 *    备份里也是这个键名、这个形状 —— 两端互导才认得出。
 *  ② 存之前**中心裁方 + 缩到 256×256 / JPEG 80**。参考图那套是长边 1400，
 *    头像用不着那么大，还要进备份（base64 再涨三分之一）。
 *
 * ⚠️ 有一处**有意跟 iOS 不一样**：没设头像时那个「默认圆」——
 *    iOS 是 8 色**扁平**底 + 首字，安卓这边保留原来的 **6 色渐变**底 + 首字
 *    （见 [AvatarBubble]，从 2026-09 就在用，用户看惯了）。
 *    两边不统一是有意为之：安卓那 6 位用户在用，不该为了「两端颜色一样」
 *    去动一个他们早就认熟的东西。**设过头像的人，两端显示的是同一张图，这才是要紧的。**
 *
 * 为什么不落盘成文件（像参考图那样）：参考图有「删单时清孤儿图」那套 prune，
 * 头像挂的是**名字**、生命周期完全不同 —— 混在一起迟早误删。
 * 一张十来 KB，也经得起走偏好存储。
 */
object Avatars {

    private const val PREF = "settings"
    private const val KEY = "artistAvatars"

    /** 头像存多大（长边像素） */
    const val SIDE = 256

    /** JPEG 质量 */
    private const val QUALITY = 80

    /** 相册里挑来的原图先缩到这个量级再裁 —— 不然一张相机原图能把老机器撑爆 */
    private const val DECODE_HINT = 1024

    private lateinit var sp: SharedPreferences

    /**
     * 名字 → base64。**这是唯一的那份**（界面、备份、存盘都读它）。
     *
     * 用 Compose 的 state map：谁读它，谁就在它变了之后自动重画 ——
     * 所以「画师页设了头像 → 账本列表 / 统计页排行一起变」不用一处处通知。
     */
    val map = mutableStateMapOf<String, String>()

    fun init(context: Context) {
        sp = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        map.clear()
        map.putAll(parse(sp.getString(KEY, null)))
    }

    /** 界面上那个圆读它：这个名字有没有设过头像 */
    fun base64For(name: String): String? = map[clean(name)]

    // MARK: - 增 / 删 / 改

    /** 从相册收下一张：裁方 → 缩到 [SIDE] → 存 */
    fun setFromUri(context: Context, name: String, uri: Uri): Boolean {
        val raw = readDownsampled(context, uri) ?: return false
        val square = square(raw) ?: return false
        return setBitmap(name, square)
    }

    fun setBitmap(name: String, bitmap: Bitmap): Boolean {
        val c = clean(name)
        if (c.isEmpty()) return false
        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            out.toByteArray()
        }
        map[c] = Base64.encodeToString(bytes, Base64.NO_WRAP)
        persist()
        return true
    }

    /** 移除 → 变回「首字圆」 */
    fun remove(name: String) {
        if (map.remove(clean(name)) != null) persist()
    }

    /**
     * 改名 → 头像跟着搬（跟 iOS 版同语义）。
     * 新名字上**已经有**头像就不覆盖 —— 改名多半是「同一人写成两种名字」，两边都可能设过。
     */
    fun rename(from: String, to: String) {
        val a = clean(from)
        val b = clean(to)
        if (a.isEmpty() || b.isEmpty() || a == b) return
        val v = map[a] ?: return
        if (map[b] == null) map[b] = v
        map.remove(a)
        persist()
    }

    // MARK: - 备份

    /** 导出成 `名字 → base64`（进备份 JSON 那份）；一本都没设就返回空 */
    fun exportBase64(): Map<String, String> = map.toMap()

    /** 从备份里灌回来（**整本换掉**）。只有备份里真有这个键时才该调它 */
    fun importBase64(src: Map<String, String>) {
        map.clear()
        for ((k, v) in src) {
            val c = clean(k)
            if (c.isNotEmpty() && v.isNotBlank()) map[c] = v
        }
        persist()
    }

    // MARK: - 解码

    /** base64 → 图。失败给 null（宁可这块空着，也不能让整个界面崩） */
    fun decode(b64: String): ImageBitmap? = runCatching {
        val bytes = Base64.decode(b64, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()

    // MARK: - 内部

    private fun clean(n: String) = n.trim()

    private fun persist() {
        // 还没 init 就（理论上不会发生）先别炸 —— 宁可不存，也不能开 App 就崩
        if (!::sp.isInitialized) return
        sp.edit().putString(KEY, toJson(map)).apply()
    }

    private fun toJson(m: Map<String, String>): String {
        val o = JSONObject()
        for ((k, v) in m) o.put(k, v)
        return o.toString()
    }

    private fun parse(text: String?): Map<String, String> {
        if (text.isNullOrBlank()) return emptyMap()
        return runCatching {
            val o = JSONObject(text)
            val out = mutableMapOf<String, String>()
            for (k in o.keys()) {
                val v = o.optString(k)
                if (v.isNotBlank()) out[k] = v
            }
            out
        }.getOrDefault(emptyMap())
    }

    /**
     * 读相册那张图，**先按尺寸缩一遍再解码**。
     *
     * ⚠️ 不能直接 `decodeStream` 原图：一张 4000×3000 的相机图解成位图就是 48MB，
     *    8GB 的机器还行、手机当场 OOM。所以先用 `inJustDecodeBounds` 问一下多大，
     *    再按 2 的幂次缩到 [DECODE_HINT] 以内。
     */
    private fun readDownsampled(context: Context, uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        var sample = 1
        var longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / sample > DECODE_HINT) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }.getOrNull()

    /**
     * 中心裁成正方形 → 缩到 [SIDE]。
     *
     * 为什么必须裁方：头像框是个圆，长方形图被 `ContentScale.Crop` 随便切，
     * 人像可能只剩半张脸。裁在存之前做一次，以后每次显示都不用再算。
     */
    fun square(src: Bitmap): Bitmap? {
        val w = src.width
        val h = src.height
        if (w <= 0 || h <= 0) return null
        val edge = minOf(w, h)
        // ⚠️ 裁出来的正好就是整张图时，createBitmap 会把**同一个对象**还给你 ——
        //    所以下面判 `!==` 再决定要不要回收，别把传进来那张给回收了。
        val cropped = Bitmap.createBitmap(src, (w - edge) / 2, (h - edge) / 2, edge, edge)
        if (edge == SIDE) return cropped
        val scaled = Bitmap.createScaledBitmap(cropped, SIDE, SIDE, true)
        if (cropped !== src && cropped !== scaled) cropped.recycle()
        return scaled
    }
}
