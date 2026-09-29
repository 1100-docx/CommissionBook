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
 * 备份 / 恢复。
 *
 * ⚠️ **格式跟 iOS 版完全一致**，同一份 JSON 两边都能读：
 * ```json
 * { "commissions": [ ... ], "artistNotes": { "画师A": "微信 xxx" } }
 * ```
 * 所以 iOS 的备份能导进安卓，安卓的也能导回 iOS。
 */
object Backup {

    fun encode(items: List<Commission>, notes: Map<String, String>): String {
        val root = JSONObject()
        root.put("commissions", Json.encodeCommissions(items))
        root.put("artistNotes", Json.encodeNotes(notes))
        return root.toString(2)
    }

    /** 读一份备份文本；读不出来返回 null（比抛异常好：坏文件不该让 App 崩） */
    fun decode(text: String): Pair<List<Commission>, Map<String, String>>? = runCatching {
        val root = JSONObject(text)
        val arr = root.optJSONArray("commissions") ?: JSONArray()
        val items = Json.decodeCommissions(arr)
        val notes = Json.decodeNotes(root.optJSONObject("artistNotes"))
        items to notes
    }.getOrNull()

    /** 备份文件名：约稿账本-2026-09-29-0250-手动.json */
    fun fileName(manual: Boolean = true, at: Date = Date()): String {
        val f = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.CHINA)
        return "约稿账本-${f.format(at)}${if (manual) "-手动" else ""}.json"
    }

    /** 写进用户选的那个位置（SAF 给的 uri） */
    fun writeTo(context: Context, uri: Uri, text: String): Boolean = runCatching {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
        true
    }.getOrDefault(false)

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
}
