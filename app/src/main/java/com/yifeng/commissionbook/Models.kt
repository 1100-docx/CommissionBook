package com.yifeng.commissionbook

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

// MARK: - 进度
// 跟 iOS 版一模一样：rawValue 就是存盘值，一个字节都不许改
enum class CommissionStatus(val label: String, @StringRes val labelRes: Int) {
    QUOTING("待报价", R.string.ledger_status_pending_quote),
    DEPOSIT_PAID("已付定金", R.string.ledger_status_deposit_paid),
    DRAWING("绘制中", R.string.ledger_status_in_progress),
    DELIVERED("已交付", R.string.ledger_delivered);

    /**
     * 显示用（跟着语言走）。
     *
     * ⚠️ **`label` 才是存盘值**（`JSONObject.put("status", …)` 用的就是它），
     *    一个字节都不许改 —— 改了老备份认不出来。所以显示另走这一条。
     *    2026-10-03 加：跟 iOS 版同一个毛病，那批「存下来的中文」直接拿去显示，
     *    切语言时纹丝不动。
     */
    val shown: String get() = AppCtx.s(labelRes)

    /** 阶段走了几格：0 / 1 / 2 / 3 */
    val stageIndex: Int get() = ordinal

    companion object {
        const val STAGE_COUNT = 4
        fun from(label: String?): CommissionStatus =
            entries.firstOrNull { it.label == label } ?: QUOTING
    }
}

// MARK: - 使用模式（2026-09-30 加）
//
// 同一个 App，两套身份：
//   · BUYER  买家 —— 我出钱约别人的稿，记「我给了谁多少钱」
//   · ARTIST 画师 —— 别人找我画，记「谁找我、定金收没收、尾款还没收」
//
// ⚠️ 关键：**两边用的是同一套字段**（总价 / deposit / 状态 / 截止日），
// 只有**叫法**不一样。所以加模式切换不用重做数据模型，只需要两件事：
//   ① 每条记录归属一个模式（[Commission.mode]）② 界面上的字从 [Terms] 取。
enum class AppMode(val key: String, @StringRes val labelRes: Int) {
    BUYER("buyer", R.string.common_buyer),
    ARTIST("artist", R.string.common_artist);

    /** 显示用（跟着语言走）。`key` 是存盘值，别动 */
    val label: String get() = AppCtx.s(labelRes)

    companion object {
        fun from(key: String?): AppMode = entries.firstOrNull { it.key == key } ?: BUYER
    }
}

/**
 * 两套叫法。界面上跟「对方」「钱」有关的字**全从这里取** ——
 * 以后想改词只改这一个地方，不用满工程找字符串。
 */
object Terms {
    /** 对方：买家模式下是「画师」，画师模式下是「客户」 */
    fun other(m: AppMode): String = if (m == AppMode.BUYER) AppCtx.s(R.string.common_artist) else AppCtx.s(R.string.common_client)
    fun otherBlank(m: AppMode): String =
        if (m == AppMode.BUYER) AppCtx.s(R.string.ledger_no_artist) else AppCtx.s(R.string.ledger_no_client)
    /** 通知里指代某个没写名字的人 */
    fun otherSome(m: AppMode): String = if (m == AppMode.BUYER) AppCtx.s(R.string.artist_someone) else AppCtx.s(R.string.ledger_some_client)

    /** 已收/已付那格 */
    fun paid(m: AppMode): String = if (m == AppMode.BUYER) AppCtx.s(R.string.ledger_paid) else AppCtx.s(R.string.ledger_deposit_received)
    /** 还差的那部分 */
    fun unpaid(m: AppMode): String = if (m == AppMode.BUYER) AppCtx.s(R.string.ledger_unpaid) else AppCtx.s(R.string.ledger_awaiting_balance)

    fun empty(m: AppMode): String = if (m == AppMode.BUYER) AppCtx.s(R.string.ledger_no_commissions_yet) else AppCtx.s(R.string.ledger_no_jobs_yet)
    fun searchHint(m: AppMode): String = AppCtx.s(R.string.common_search_placeholder, other(m))
    fun topSpender(m: AppMode): String =
        if (m == AppMode.BUYER) AppCtx.s(R.string.stats_top_spend_artist) else AppCtx.s(R.string.stats_top_earning_client)

    // ——— 第二刀（2026-09-30）：排期 + 稿酬统计 ———

    /** 排期卡片的标题 */
    fun scheduleTitle(m: AppMode): String = if (m == AppMode.BUYER) AppCtx.s(R.string.artist_delivery_schedule) else AppCtx.s(R.string.artist_order_schedule)
    /** 手上还有几单 */
    fun handWord(m: AppMode): String = if (m == AppMode.BUYER) AppCtx.s(R.string.ledger_pending_delivery) else AppCtx.s(R.string.ledger_pending_submission)
    /** 本月 / 本年 那张卡的标题 */
    fun periodTitle(m: AppMode): String =
        if (m == AppMode.BUYER) AppCtx.s(R.string.stats_month_year_spend) else AppCtx.s(R.string.stats_month_year_earnings)

    /**
     * 状态的**显示名**。
     * ⚠️ 存盘用的是 `CommissionStatus.label`（一个字节都不许改），这个只管显示。
     */
    fun status(s: CommissionStatus, m: AppMode): String =
        if (m == AppMode.ARTIST && s == CommissionStatus.DEPOSIT_PAID) AppCtx.s(R.string.ledger_deposit_received) else s.label
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
    /**
     * 这条属于哪个模式（"buyer" / "artist"）。
     * ⚠️ 老数据没有这个键 → 默认 buyer，**存量数据一条都不动**。
     */
    var mode: String = AppMode.BUYER.key,

    /**
     * 参考图（2026-10-03 加，逸风要的）。
     *
     * 这里只存**文件名**（形如 `p3f9c2a1b7d4e5.jpg`），图本体躺在 App 私有目录
     * `files/photos/` 里（见 [Photos]）。为什么不直接把图塞进 json：
     * 每存一次盘都要重写整个文件，塞图片的话改一个数字就得重写几 MB。
     *
     * 老数据没有这个键 → 读出来是空表，**存量数据一条都不动**。
     */
    var photos: List<String> = emptyList(),
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
    /**
     * 把约稿写成 JSON 数组。
     *
     * 参考图有**两种写法**，由 [photoBytes] 决定：
     *  - `null`（App 自己存盘那份）→ `"photos": ["p1.jpg", "p2.jpg"]`，只有文件名，文件小、存盘快；
     *  - 给了取值函数（**导出备份**）→ `"photos": [{"name":"p1.jpg","b64":"/9j/4AA…"}]`，
     *    把图本体 base64 一起带上 —— 逸风 2026-10-03 拍板「备份要带图」，换手机才不丢。
     *
     * 读的时候两种都认（见 [decodeCommissions]），所以两边互导没问题。
     */
    fun encodeCommissions(
        items: List<Commission>,
        photoBytes: ((String) -> ByteArray?)? = null,
    ): JSONArray {
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
            o.put("mode", c.mode)
            if (c.photos.isNotEmpty()) {
                val ph = JSONArray()
                for (name in c.photos) {
                    val bytes = photoBytes?.invoke(name)
                    if (bytes == null) {
                        // 取不到图（文件被清掉了）→ 只写名字。
                        // 宁可这一张丢，也不能让整份备份导不出来。
                        ph.put(name)
                    } else {
                        ph.put(
                            JSONObject()
                                .put("name", name)
                                .put("b64", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
                        )
                    }
                }
                o.put("photos", ph)
            }
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
    fun readCommissions(text: String, onPhoto: ((String, ByteArray) -> Unit)? = null): List<Commission> {
        val t = text.trim()
        if (t.startsWith("[")) return decodeCommissions(JSONArray(t), onPhoto)
        val root = JSONObject(t)
        return decodeCommissions(root.optJSONArray("commissions") ?: JSONArray(), onPhoto)
    }

    /**
     * 读约稿数组。
     *
     * @param onPhoto 备份里带着图（base64）时，每张图回调一次 —— 调用方负责把字节落盘。
     *                传 null = 只管名字（App 自己存盘那份就是这么读的）。
     */
    fun decodeCommissions(arr: JSONArray, onPhoto: ((String, ByteArray) -> Unit)? = null): List<Commission> {
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
                    // 老数据没这个键 → buyer。iOS 版读这份文件时会忽略它，不影响互通
                    mode = o.optString("mode").ifBlank { AppMode.BUYER.key },
                    photos = decodePhotos(o.optJSONArray("photos"), onPhoto),
                )
            )
        }
        return out
    }

    /**
     * 读参考图这一串，**两种写法都认**：
     *  - `["p1.jpg", …]`              → App 自己存盘那份
     *  - `[{"name":…,"b64":…}, …]`    → 备份里那份，带上图本体
     *
     * ⚠️ 文件名走 [Photos.safeName] 消毒：备份是可以从外面塞进来的，
     *    里面要是写着 `../../commissions.json`，直接落盘就等于让人改我的数据。
     */
    private fun decodePhotos(arr: JSONArray?, onPhoto: ((String, ByteArray) -> Unit)?): List<String> {
        if (arr == null) return emptyList()
        val names = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            when (val entry = arr.opt(i)) {
                is String -> {
                    val n = Photos.safeName(entry)
                    if (n.isNotBlank()) names.add(n)
                }
                is JSONObject -> {
                    val n = Photos.safeName(entry.optString("name"))
                    if (n.isBlank()) continue
                    names.add(n)
                    val b64 = entry.optString("b64")
                    if (b64.isNotBlank() && onPhoto != null) {
                        runCatching { onPhoto(n, android.util.Base64.decode(b64, android.util.Base64.DEFAULT)) }
                    }
                }
            }
        }
        return names
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
            val seed = sampleCommissions().toMutableList()
            saveCommissions(seed)
            // 记着这两条示例的 id —— 列表顶上那条「点这里清掉」认它（2026-10-01 加）
            saveDemoIds(seed.map { it.id })
            return seed
        }
        val text = runCatching { dataFile.readText() }.getOrElse { "" }
        return runCatching {
            Json.readCommissions(text).toMutableList()
        }.getOrElse { e ->
            // ⚠️ 读不出来时**绝对不要**悄悄返回空表就完事 ——
            // 那样用户一操作就会把空表存回去，真数据就没了。
            // 先把原件改名留一份，再返回空。
            android.util.Log.e("CommissionBook", AppCtx.s(R.string.common_archive_read_failed, e))
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

    // MARK: - 参考图（2026-10-03 加）

    /**
     * 图放哪：`files/photos/`。
     * 用 filesDir（不是 cacheDir）—— cacheDir 会被系统在空间紧张时**自己删**，
     * 用户配的参考图不能是这种命。
     */
    private val photosDir: File get() = File(context.filesDir, "photos").apply { if (!exists()) mkdirs() }

    fun photoFile(name: String): File = File(photosDir, Photos.safeName(name))

    fun photoExists(name: String): Boolean = photoFile(name).isFile

    /** 读一张图的原字节（导出备份时要把它们 base64 进 JSON） */
    fun readPhoto(name: String): ByteArray? = runCatching { photoFile(name).readBytes() }.getOrNull()

    /**
     * 从系统选择器给的 uri 收下一张图：压好、落盘，返回文件名。
     * 失败（图坏了/读不到）返回 null —— 调用方提示一句就行，别崩。
     */
    fun savePhotoFrom(uri: Uri): String? {
        val bytes = Photos.compress(context, uri) ?: return null
        val name = "p" + UUID.randomUUID().toString().replace("-", "").take(14) + ".jpg"
        return if (writePhoto(name, bytes)) name else null
    }

    /** 落盘一张图（恢复备份时用：备份里带着 base64，直接写下来） */
    fun writePhoto(name: String, bytes: ByteArray): Boolean =
        runCatching { photoFile(name).writeBytes(bytes); true }.getOrDefault(false)

    fun deletePhotos(names: Collection<String>) {
        names.forEach { runCatching { photoFile(it).delete() } }
    }

    /**
     * 把**没有任何一条约稿引用**的图删掉，返回删了几个。
     *
     * 什么时候用：① 删单之后 ② 恢复备份（整批换掉）之后 ③ App 启动。
     * 不做这件事的话，删单只是把记录删了，图会永远堆在目录里 ——
     * 用户看不见，但手机空间实打实地少。
     */
    fun prunePhotos(referenced: Set<String>): Int {
        var n = 0
        photosDir.listFiles()?.forEach { f ->
            if (f.name !in referenced && f.delete()) n++
        }
        return n
    }

    // MARK: - 示例数据的 id（2026-10-01 加）

    /**
     * 首次打开那两条**示例**的 id 存在哪。
     *
     * 为什么单开一个键、而不是往 [Commission] 里加个 isDemo 字段：
     * 加字段就动了 JSON 结构（备份文件、跟 iOS 互换都跟着变）。
     * 单存一串 id 就干净 —— **备份格式一个字节没动**，
     * 用户手动把示例删了也不会出错（id 找不到就当没有）。
     */
    private val sp get() = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun demoIds(): Set<String> =
        (sp.getString("demoIds", "") ?: "")
            .split(",")
            .filter { it.isNotBlank() }
            .toSet()

    private fun saveDemoIds(ids: List<String>) {
        sp.edit().putString("demoIds", ids.joinToString(",")).apply()
    }

    fun clearDemoIds() {
        sp.edit().remove("demoIds").apply()
    }

    /**
     * 第一次打开塞两条示例，跟 iOS 版一致（2026-10-01 从 1 条改成 2 条）。
     *
     * 两条各有各的用意：
     *   ① 「绘制中 + 有截止日」→ 一眼看见进度条、还剩几天
     *   ② 「已交付 + 备注」    → 一眼看见走到底的卡片长什么样
     *
     * 画师名统一以「示例 ·」开头，列表顶上还会浮一条「点这里清掉」——
     * 新用户不用自己瞎点一遍才明白这 App 干嘛的，不想要也能一键删干净。
     */
    private fun sampleCommissions(): List<Commission> {
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        return listOf(
            Commission(
                artist = AppCtx.s(R.string.help_sample_blue),
                title = AppCtx.s(R.string.help_sample_fullbody),
                total = 300.0,
                deposit = 150.0,
                status = CommissionStatus.DRAWING,
                dateMillis = now,
                deadlineMillis = now + 10L * day,
                note = AppCtx.s(R.string.help_card_tips),
            ),
            Commission(
                artist = AppCtx.s(R.string.help_sample_white),
                title = AppCtx.s(R.string.help_sample_chibi),
                total = 80.0,
                deposit = 80.0,
                status = CommissionStatus.DELIVERED,
                dateMillis = now - 12L * day,
                note = AppCtx.s(R.string.help_delivered_progress),
            ),
        )
    }
}
