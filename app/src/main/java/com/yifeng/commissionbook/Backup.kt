package com.yifeng.commissionbook

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 一份备份解析出来的三样东西。
 *
 * ⚠️ [avatars] 是**可空**的，这不是多余 —— 跟别的字段不一样：
 *    「备份里没有 artistAvatars」和「备份里 artistAvatars 是空的」
 *    是两件不同的事。老备份属于前者 —— 那种时候恢复**不该动**本机现有的头像。
 */
data class BackupData(
    val items: List<Commission>,
    val notes: Map<String, String>,
    val avatars: Map<String, String>?,
)

/**
 * 备份 / 恢复。
 *
 * ⚠️ **格式跟 iOS 版完全一致**，同一份 JSON 两边都能读：
 * ```json
 * { "commissions": [ ... ], "artistNotes": { "画师A": "微信 xxx" },
 *   "artistAvatars": { "画师A": "<base64 的 JPEG>" } }
 * ```
 * 所以 iOS 的备份能导进安卓，安卓的也能导回 iOS。
 *
 * `artistAvatars` 是 2026-10-08 加的（头像）—— 一本都没设就不写这个键，
 * 老备份长什么样、新备份还长什么样。
 */
object Backup {

    /**
     * 打一份备份文本。
     *
     * ⚠️ 2026-10-03 起**备份带图**（逸风拍板）：参考图会以 base64 写进 JSON 的
     *    `"photos": [{"name":…,"b64":…}]` 里。好处是换手机、iOS↔安卓互导都不丢图；
     *    代价是备份文件会大不少（一张缩略后的图约 150–400KB，base64 还要再涨三分之一）。
     *
     * @param photoBytes 能给出一张图的原始字节；给不出（或者图片文件已经没了）
     *                   就只写文件名，不会让整份备份导不出来。
     * @param avatars 名字 → base64 的头像（2026-10-08 加）。空着就**连键都不写**。
     */
    fun encode(
        items: List<Commission>,
        notes: Map<String, String>,
        photoBytes: ((String) -> ByteArray?)? = null,
        avatars: Map<String, String> = emptyMap(),
    ): String {
        val root = JSONObject()
        root.put("commissions", Json.encodeCommissions(items, photoBytes))
        root.put("artistNotes", Json.encodeNotes(notes))
        if (avatars.isNotEmpty()) {
            // 跟 [Json.encodeNotes] 一个写法：一个个 put，不用 `JSONObject(Map)` 那个构造器
            // （它的泛型签名在 Kotlin 这边不太听话，容易报一堆无谓的告警）
            val o = JSONObject()
            for ((k, v) in avatars) o.put(k, v)
            root.put("artistAvatars", o)
        }
        return root.toString(2)
    }

    /**
     * 读一份备份文本；读不出来返回 null（比抛异常好：坏文件不该让 App 崩）。
     *
     * @param onPhoto 备份里带着图时，每张图回调一次 —— 传进来的这个函数负责落盘。
     *                不传就只恢复记录本身（图还是本机原来那些）。
     */
    fun decode(
        text: String,
        onPhoto: ((String, ByteArray) -> Unit)? = null,
    ): BackupData? = runCatching {
        val root = JSONObject(text)
        val arr = root.optJSONArray("commissions") ?: JSONArray()
        val items = Json.decodeCommissions(arr, onPhoto)
        val notes = Json.decodeNotes(root.optJSONObject("artistNotes"))
        // ⚠️ `optJSONObject` 对「没有这个键」和「键是 null」都给 null ——
        //    正好就是我们要的「老备份」语义；真有一本空的那也等于没设，一样处理。
        val avatars = root.optJSONObject("artistAvatars")?.let { o ->
            val m = mutableMapOf<String, String>()
            for (k in o.keys()) {
                val v = o.optString(k)
                if (v.isNotBlank()) m[k] = v
            }
            m
        }
        BackupData(items, notes, avatars)
    }.getOrNull()

    /** 备份文件名：约稿账本-2026-09-29-0250-手动.json */
    fun fileName(manual: Boolean = true, at: Date = Date()): String {
        val f = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.CHINA)
        return "约稿账本-${f.format(at)}${if (manual) "-手动" else ""}.json"
    }

    /** 写进用户选的那个位置（SAF 给的 uri） */
    fun writeTo(context: Context, uri: Uri, text: String): Boolean =
        writeBytes(context, uri, text.toByteArray()) != null

    /**
     * 往用户选的那个位置写字节。**返回盘上真实的字节数**（写不成给 null）。
     *
     * ⚠️ 2026-10-07 大改，起因：逸风连着两次反馈「安卓导出的 Excel 是空的」。
     *    老写法是「`openOutputStream` 没抛异常就算成功」，这里头藏着两个坑：
     *    ① 有的系统文件管理器（ColorOS 那个就中招）`openOutputStream` 会**返回 null** ——
     *       老写法的 `?.use {}` 于是**一个字节都没写**，可它照样 `return true`、
     *       提示「已保存」，盘上留下的却是个 **0 字节的空文件**。用户看到的就是「表是空的」。
     *    ② `"wt"`（截断写）不是每个 provider 都认，认不了会直接抛异常 → 整个失败。
     *    现在：写不进去就**明说失败**；"wt" 不成退到 "w"；
     *    写完还把同一份文件**读回来数一遍字节**，长度/内容对不上也算失败。
     *    这样「提示已保存」这句话才真的可信 —— 也才有字节数可报。
     */
    fun writeBytes(context: Context, uri: Uri, bytes: ByteArray): Int? {
        var ok = false
        runCatching {
            val out = context.contentResolver.openOutputStream(uri, "wt")
                ?: context.contentResolver.openOutputStream(uri, "w")
            if (out != null) {
                out.use {
                    it.write(bytes)
                    it.flush()
                }
                ok = true
            }
        }
        if (!ok) return null

        // 写完之后**照着用户点开的那个文件**读回来核对（同一个 URI）
        val back = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return null

        return if (back.size == bytes.size && back.contentEquals(bytes)) back.size else null
    }

    /** 读用户选的那个文件 */
    fun readFrom(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
    }.getOrNull()

    /**
     * 「发出去」—— 走系统分享面板（发微信、发给自己都行）。
     * 文件先落一份到 cache，再用 FileProvider 给出去（不能直接扔 app 私有路径，别的 App 读不到）。
     */
    fun share(context: Context, name: String, text: String): Intent? = runCatching {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, name)
        file.writeText(text)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }.getOrNull()

    /**
     * 「发出去」的字节版 —— 导出报表写不进用户选的位置时兜底用。
     * 跟 [share] 一样：先落一份到 cache，再用 FileProvider 给出去。
     */
    fun shareBytes(context: Context, name: String, bytes: ByteArray, mime: String): Intent? = runCatching {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, name)
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }.getOrNull()
}
