package com.yifeng.commissionbook

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 全局状态。
 *
 * 跟 iOS 版一个思路：**改状态 = 改界面**。界面只读这里的数据，
 * 想让它变，不碰界面，改这里就行（改完存盘）。
 */
class AppState(private val context: Context) {

    private val store = Store(context)
    val prefs = Prefs(context)

    var items by mutableStateOf<List<Commission>>(emptyList())
        private set

    var notes by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    /**
     * 当前使用模式（买家 / 画师）。
     * ⚠️ 是 Compose 状态，不是直接读 prefs —— 这样在设置页一改，所有页面立刻重画。
     */
    var appMode by mutableStateOf(AppMode.from(prefs.appMode))
        private set

    fun switchMode(m: AppMode) {
        appMode = m
        prefs.appMode = m.key
    }

    /**
     * 深浅色（2026-10-03 加）。
     * Compose 状态 —— 设置页一改，整个 App 立刻重画，不用重启。
     * （语言那一项就做不到这一点，所以那边改完必须重启，见设置页「语言」一节。）
     */
    var appearance by mutableStateOf(AppAppearance.from(prefs.appearance))
        private set

    /**
     * ⚠️ 名字不能叫 `setAppearance`：`var appearance` 自动生成的 setter
     * 在 JVM 上就叫 `setAppearance`，手写一个同名的会撞签名、直接编译不过。
     * 跟隔壁 `switchMode` 保持一致，叫 `switchAppearance`。
     */
    fun switchAppearance(a: AppAppearance) {
        appearance = a
        prefs.appearance = a.key
    }

    /**
     * 触感档位（2026-10-07 加，见 [HapticLevel] / [Haptics]）。
     *
     * ⚠️ 这里改的是**全局内存值** `Haptics.level` —— 所有点击反馈都读它，
     *    不逐个页面传参数。设置页拨一下当场生效，不用重启。
     */
    var hapticLevel by mutableStateOf(HapticLevel.from(prefs.hapticLevel))
        private set

    init {
        // 启动时把存下来的档位灌给全局触感，否则永远是默认的中档，设置白改
        Haptics.level = hapticLevel
    }

    /** 换档 + 当场震一下：不然选完不知道刚才那下是不是变重了 */
    fun switchHaptic(l: HapticLevel) {
        hapticLevel = l
        prefs.hapticLevel = l.key
        Haptics.level = l
        // ⚠️ 选「关」的那一下**不能震**：刚点完「关」还震一下，等于当面骗人。
        //    （选回轻/中/重时照旧震，好让你当场听见自己选的那一档有多重。）
        if (l != HapticLevel.OFF) Haptics.tick()
    }

    /**
     * 首启那一问问过没有（2026-09-30 加）。
     * Compose 状态 —— 选完立刻让弹窗自己消失，不用手动关。
     */
    var modeChosen by mutableStateOf(prefs.modeChosen)
        private set

    /** 在首启弹窗里选定了：切模式 + 记下「问过了」 */
    fun chooseMode(m: AppMode) {
        switchMode(m)
        markModeChosen()
    }

    /** 点了「稍后再说」：模式不动（还是默认买家），但也算问过，别每次开都烦人 */
    fun skipModePicker() = markModeChosen()

    private fun markModeChosen() {
        modeChosen = true
        prefs.modeChosen = true
    }

    /**
     * 「用法都在设置里」那层引导弹窗弹过没有（2026-10-01 加）。
     * Compose 状态 —— 点「知道了 / 现在去看」立刻让弹窗自己消失。
     */
    var guideShown by mutableStateOf(prefs.guideShown)
        private set

    fun markGuideShown() {
        if (guideShown) return
        guideShown = true
        prefs.guideShown = true
    }

    /**
     * 帮助页看过没有（2026-10-01 加）。
     * 决定底栏「设置」那格要不要挂角标 —— 看过就摘掉。
     */
    var helpSeen by mutableStateOf(prefs.helpSeen)
        private set

    fun markHelpSeen() {
        if (helpSeen) return
        helpSeen = true
        prefs.helpSeen = true
    }

    init {
        reload()
        // 每次打开 App 重排一次闹钟：手机重启过、或者被省电模式清过，这一步能补回来
        Reminders.reschedule(context, items, prefs.reminderEnabled, prefs.reminderHour, prefs.reminderMinute)
    }

    fun reload() {
        items = store.loadCommissions()
        notes = store.loadNotes()
    }

    private fun persist() {
        store.saveCommissions(items)
        store.saveNotes(notes)
        // 数据一变就把提醒闹钟重排一遍 —— 最简单的做法，也最不容易漏
        Reminders.reschedule(context, items, prefs.reminderEnabled, prefs.reminderHour, prefs.reminderMinute)
        // 顺手清掉没人引用的参考图（删单、改单、恢复备份之后都走这儿）。
        // ⚠️ 只在这儿清、**不在启动时清**：万一 commissions.json 读坏了，
        //    [Store.loadCommissions] 会把文件改名留档、返回空表 ——
        //    那种时候要是也清一遍孤儿图，等于把用户的图全删了，数据事故雪上加霜。
        store.prunePhotos(referencedPhotos())
    }

    /** 现在还挂在某条约稿上的图（**含另一模式**，别只算当前模式那份） */
    private fun referencedPhotos(): Set<String> = items.flatMap { it.photos.map { p -> p.name } }.toSet()

    // MARK: - 参考图（2026-10-03 加）

    fun photoFile(name: String): java.io.File = store.photoFile(name)

    fun photoExists(name: String): Boolean = store.photoExists(name)

    /** 从系统图片选择器收下的那张图：压好落盘，返回文件名（失败 null） */
    fun addPhoto(uri: Uri): String? = store.savePhotoFrom(uri)

    fun writePhotoBytes(name: String, bytes: ByteArray): Boolean = store.writePhoto(name, bytes)

    // MARK: - 增删改

    fun upsert(c: Commission) {
        val i = items.indexOfFirst { it.id == c.id }
        items = if (i >= 0) items.toMutableList().also { it[i] = c } else items + c
        persist()
    }

    fun delete(id: String) {
        val gone = items.filter { it.id == id }
        items = items.filterNot { it.id == id }
        // 单子删了，它配的参考图也一起走（不留垃圾文件。persist 里还会再兜一次底）
        store.deletePhotos(gone.flatMap { it.photos.map { p -> p.name } })
        persist()
    }

    fun toggleArchive(id: String) {
        items = items.map { if (it.id == id) it.copy(archived = !it.archived) else it }
        persist()
    }

    // MARK: - 批量操作（2026-10-01 加）
    //
    // 单子攒多了，一条条进菜单去归档 / 删除太烦 —— 多选一次搞定。

    /** 选中的一起归档（on = false 就是一起放回去） */
    fun archiveMany(ids: Collection<String>, on: Boolean) {
        if (ids.isEmpty()) return
        val set = ids.toSet()
        items = items.map { if (it.id in set) it.copy(archived = on) else it }
        persist()
    }

    /** 选中的一起删掉 */
    fun deleteMany(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val set = ids.toSet()
        items = items.filterNot { it.id in set }
        persist()
    }

    // MARK: - 首次打开那两条示例（2026-10-01 加）

    /** 示例的 id —— 列表顶上那条「点这里清掉」认它 */
    val demoIds: Set<String> get() = store.demoIds()

    /** 一键清掉示例，并把记号抹掉（之后不会再出现） */
    fun clearDemo() {
        val ids = store.demoIds()
        if (ids.isEmpty()) return
        items = items.filterNot { it.id in ids }
        store.clearDemoIds()
        persist()
    }

    fun setNote(artist: String, note: String) {
        notes = notes.toMutableMap().also { it[artist] = note }
        persist()
    }

    /**
     * 改名字（2026-10-08 加，跟 iOS 版同语义）。
     *
     * 把这个人名下**所有**条目一起改名（**两个模式都算** —— 名字是身份证，
     * 备注和头像本来就跨模式共用一份；只改当前模式那几条的话，
     * 另一个模式里同名条目会「人还在、头像没了」，看着像丢了东西）。
     * 备注和头像跟着搬；新名字上**已经有**的不覆盖。
     *
     * @return 改了几条（0 = 名字没变 / 空名字，调用方据此提示）
     */
    fun renameArtist(from: String, to: String): Int {
        val a = if (from.isBlank()) Terms.otherBlank(appMode) else from
        val b = to.trim()
        if (b.isEmpty() || b == a) return 0

        var n = 0
        items = items.map { c ->
            if (c.artist == a) {
                n++
                c.copy(artist = b)
            } else c
        }

        // 备注：新名字上已经有就不覆盖（那是他自己写的）
        val moved = notes[a]
        val next = notes.toMutableMap()
        if (!moved.isNullOrBlank() && next[b].isNullOrBlank()) next[b] = moved
        next.remove(a)
        notes = next

        Avatars.rename(a, b)
        persist()
        return n
    }

    /**
     * 常用语模板（2026-10-09 加，#7）。
     * Compose 状态 —— 管理页里改一条，写单那边的「插入常用语」立刻是新的一份。
     */
    var phrases by mutableStateOf(prefs.phrases)
        private set

    /**
     * 整批换掉常用语。顺手**去空白、去重复**：
     * 同一句话存两遍没有任何意义，还会让选择列表看着像坏了。
     */
    fun savePhrases(list: List<String>) {
        val clean = list.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        phrases = clean
        prefs.phrases = clean
    }

    /**
     * 把一句常用语插进一段文字末尾（备注框那个「插入常用语」用的）。
     * 原来有内容就**另起一行**再插，不会把上一句话黏在一起。
     */
    fun appendPhrase(note: String, phrase: String): String {
        val p = phrase.trim()
        if (p.isEmpty()) return note
        val base = note.trimEnd()
        return if (base.isEmpty()) p else "$base\n$p"
    }

    /** 恢复备份：整批换掉 */
    fun replaceAll(newItems: List<Commission>, newNotes: Map<String, String>, newAvatars: Map<String, String>? = null, newPhrases: List<String>? = null) {
        items = newItems
        notes = newNotes
        // ⚠️ 头像 **备份里有才灌**：老备份根本没有这个键，
        //    写成 `?: emptyMap()` 一股脑灌进去，恢复一份旧备份就会把现在的头像全清掉。
        if (newAvatars != null) Avatars.importBase64(newAvatars)
        // 常用语同样：**备份里有才认**（2026-10-09 加）。而且做成**合并去重**而不是覆盖 ——
        // 常用语是「我的习惯用语」，跟账本数据不是一回事；恢复一份备份顺手把
        // 本机上攒的短语抹掉，那不是用户想要的结果。
        if (newPhrases != null) savePhrases(phrases + newPhrases)
        persist()
    }

    /** 全部数据打成一个 JSON 字符串（备份用，跟 iOS 版同一个格式）。⚠️ 带图、带头像、带常用语，见 [Backup.encode] */
    fun toJsonString(): String = Backup.encode(
        items, notes,
        photoBytes = { store.readPhoto(it) },
        avatars = Avatars.exportBase64(),
        phrases = phrases,
    )

    // MARK: - 屏幕上要用的几个数

    /**
     * 当前模式下的全部条目（含已归档）。
     * 两边账本**各记各的** —— 当买家约的稿、当画师接的单，是两摞，
     * 切模式只看见自己那一摞。老数据没有 mode → 全归「买家」。
     */
    val modeItems: List<Commission> get() = items.filter { it.mode == appMode.key }

    val active: List<Commission> get() = modeItems.filter { !it.archived }

    val activeTotal: Double get() = modeItems.filter { !it.archived }.sumOf { it.total }

    val activePaid: Double get() = modeItems.filter { !it.archived }.sumOf { it.deposit }

    // MARK: - 未结清单（2026-10-09 加，#1）

    /**
     * 还有钱没结清的单（欠款 > 0）。
     *
     * ⚠️ **含已归档**：归档是「收进档案」，不是「这笔钱不用收了」。
     *    过滤掉它，等于让一笔没到账的钱从账上消失 —— 那才是真会出事的地方。
     * （总价空着的是 0，本来也算不出欠款，自然不会进来。）
     *
     * 排序：有截止日的排前面（早的在前，逾期的自然冒头），没截止日的按日期新的在前。
     */
    val unpaidItems: List<Commission>
        get() = modeItems
            .filter { it.unpaid > 0.0 }
            .sortedWith(
                compareBy(
                    { it.deadlineMillis == null },
                    { it.deadlineMillis ?: Long.MAX_VALUE },
                    { -it.dateMillis },
                )
            )

    /** 所有未结单加起来还欠多少 */
    val unpaidTotal: Double get() = unpaidItems.sumOf { it.unpaid }
}
