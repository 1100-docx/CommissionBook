package com.yifeng.commissionbook

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

// MARK: - 进度
// 跟 iOS 版一模一样：rawValue 就是存盘值，一个字节都不许改
enum class CommissionStatus(val label: String) {
    QUOTING("待报价"),
    DEPOSIT_PAID("已付定金"),
    DRAWING("绘制中"),
    DELIVERED("已交付");

    /** 阶段走了几格：0 / 1 / 2 / 3 */
    val stageIndex: Int get() = ordinal

    companion object {
        const val STAGE_COUNT = 4
        fun from(label: String?): CommissionStatus =
            entries.firstOrNull { it.label == label } ?: QUOTING
    }
}

// MARK: - 一条约稿
data class Commission(
    val id: String = UUID.randomUUID().toString(),
    var artist: String = "",
    var title: String = "",
    var total: Double = 0.0,
    var deposit: Double = 0.0,
    var status: CommissionStatus = CommissionStatus.QUOTING,
    var dateMillis: Long = System.currentTimeMillis(),
    var archived: Boolean = false,
    var deadlineMillis: Long? = null,
    var note: String = "",
) {
    /** 还欠多少 */
    val unpaid: Double get() = (total - deposit).coerceAtLeast(0.0)

    /** 还剩几天（负数 = 超期） */
    fun daysLeft(nowMillis: Long = System.currentTimeMillis()): Int? {
        val d = deadlineMillis ?: return null
        val dayMs = 86_400_000L
        val today = nowMillis / dayMs
        val target = d / dayMs
        // 用本地时区把「当天 0 点」对齐，避免差一小时算错一天
        val zoneOffset = java.util.TimeZone.getDefault().getOffset(nowMillis)
        val todayStart = (nowMillis + zoneOffset) / dayMs * dayMs - zoneOffset
        val targetStart = (d + zoneOffset) / dayMs * dayMs - zoneOffset
        return ((targetStart - todayStart) / dayMs).toInt()
    }

    /** 时间走过几成（没截止日就是 null） */
    fun timeProgress(nowMillis: Long = System.currentTimeMillis()): Float? {
        val d = deadlineMillis ?: return null
        val total = d - dateMillis
        if (total <= 0) return 1f
        val done = nowMillis - dateMillis
        return (done.toDouble() / total.toDouble()).coerceIn(0.0, 1.0).toFloat()
    }
}

// MARK: - JSON 编解码
//
// ⚠️ 跟 iOS 版共用同一份文件格式，所以日期是 **Swift 的「参考日期」秒数**
// （2001-01-01 00:00:00 UTC 起算的 Double），不是 Unix 毫秒。
// 这层转换错了，两边的备份就互不认了。
object SwiftDate {
    /** 2001-01-01 00:00:00 UTC 的 Unix 秒数 */
    private const val REFERENCE_UNIX_SECONDS = 978_307_200.0

    fun toSwiftMillis(millis: Long): Double = millis / 1000.0 - REFERENCE_UNIX_SECONDS
    fun fromSwift(seconds: Double): Long = ((seconds + REFERENCE_UNIX_SECONDS) * 1000.0).toLong()
}

object Json {
    fun encodeCommissions(items: List<Commission>): JSONArray {
        val arr = JSONArray()
        for (c in items) {
            val o = JSONObject()
            o.put("id", c.id.uppercase())
            o.put("artist", c.artist)
            o.put("title", c.title)
            o.put("total", c.total)
            o.put("deposit", c.deposit)
            o.put("status", c.status.label)
            o.put("date", SwiftDate.toSwiftMillis(c.dateMillis))
            o.put("archived", c.archived)
            c.deadlineMillis?.let { o.put("deadline", SwiftDate.toSwiftMillis(it)) }
            o.put("note", c.note)
            arr.put(o)
        }
        return arr
    }

    /**
     * 读「约稿」这一串。
     *
     * ⚠️ 两种格式都认（2026-09-29 踩的坑）：
     * - **备份格式**（跟 iOS 共用的那个）：`{"commissions":[...],"artistNotes":{...}}`
     * - **光秃秃一个数组**：`[...]`
     *
     * 只认一种的话，把一份 iOS 备份直接放进 App 目录就会**读不出来 → 界面全空**，
     * 而用户看到的是「我的数据没了」。宁可多写两行，也不能让人以为数据丢了。
     */
    fun readCommissions(text: String): List<Commission> {
        val t = text.trim()
        if (t.startsWith("[")) return decodeCommissions(JSONArray(t))
        val root = JSONObject(t)
        return decodeCommissions(root.optJSONArray("commissions") ?: JSONArray())
    }

    fun decodeCommissions(arr: JSONArray): List<Commission> {
        val out = ArrayList<Commission>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                Commission(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    artist = o.optString("artist"),
                    title = o.optString("title"),
                    total = o.optDouble("total", 0.0),
                    deposit = o.optDouble("deposit", 0.0),
                    status = CommissionStatus.from(o.optString("status")),
                    dateMillis = optMillis(o, "date") ?: System.currentTimeMillis(),
                    archived = o.optBoolean("archived", false),
                    deadlineMillis = optMillis(o, "deadline"),
                    note = o.optString("note"),
                )
            )
        }
        return out
    }

    /** 日期可能是 Swift 的数字，也可能是别的工具导出的字符串 —— 两种都认 */
    private fun optMillis(o: JSONObject, key: String): Long? {
        if (!o.has(key) || o.isNull(key)) return null
        o.optDouble(key, Double.NaN).takeIf { !it.isNaN() }?.let { return SwiftDate.fromSwift(it) }
        val s = o.optString(key, "")
        return runCatching { java.time.Instant.parse(s).toEpochMilli() }.getOrNull()
    }

    fun encodeNotes(notes: Map<String, String>): JSONObject {
        val o = JSONObject()
        for ((k, v) in notes) o.put(k, v)
        return o
    }

    fun decodeNotes(o: JSONObject?): MutableMap<String, String> {
        val m = LinkedHashMap<String, String>()
        if (o == null) return m
        for (k in o.keys()) m[k] = o.optString(k)
        return m
    }
}

// MARK: - 存盘：一个 JSON 文件，放在 App 自己的目录里
class Store(private val context: Context) {

    private val dataFile: File get() = File(context.filesDir, "commissions.json")
    private val notesFile: File get() = File(context.filesDir, "artistNotes.json")

    fun loadCommissions(): MutableList<Commission> {
        if (!dataFile.exists()) {
            val seed = mutableListOf(sample())
            saveCommissions(seed)
            return seed
        }
        val text = runCatching { dataFile.readText() }.getOrElse { "" }
        return runCatching {
            Json.readCommissions(text).toMutableList()
        }.getOrElse { e ->
            // ⚠️ 读不出来时**绝对不要**悄悄返回空表就完事 ——
            // 那样用户一操作就会把空表存回去，真数据就没了。
            // 先把原件改名留一份，再返回空。
            android.util.Log.e("CommissionBook", "读存档失败，已留副本: ${e}")
            runCatching {
                dataFile.renameTo(File(context.filesDir, "commissions-读不出来-${System.currentTimeMillis()}.json"))
            }
            mutableListOf()
        }
    }

    fun saveCommissions(items: List<Commission>) {
        runCatching { dataFile.writeText(Json.encodeCommissions(items).toString()) }
    }

    fun loadNotes(): MutableMap<String, String> {
        // ① 正常情况：小册子有自己的文件
        if (notesFile.exists()) {
            return runCatching { Json.decodeNotes(JSONObject(notesFile.readText())) }.getOrElse { LinkedHashMap() }
        }
        // ② 没有小册子文件时，看一眼存档 —— 从 iOS 那边拿过来的备份是「一份文件装全部」的，
        //    里面就带着 artistNotes。不然恢复完约稿，画师备注会凭空消失。
        return runCatching {
            val t = dataFile.readText().trim()
            if (t.startsWith("{")) Json.decodeNotes(JSONObject(t).optJSONObject("artistNotes"))
            else LinkedHashMap()
        }.getOrElse { LinkedHashMap() }
    }

    fun saveNotes(notes: Map<String, String>) {
        runCatching { notesFile.writeText(Json.encodeNotes(notes).toString()) }
    }

    /** 第一次打开塞一条示例，跟 iOS 版一致 */
    private fun sample() = Commission(
        artist = "画师A",
        title = "全身像",
        total = 300.0,
        deposit = 150.0,
        status = CommissionStatus.DRAWING,
        dateMillis = System.currentTimeMillis(),
        deadlineMillis = System.currentTimeMillis() + 10L * 86_400_000L,
    )
}
