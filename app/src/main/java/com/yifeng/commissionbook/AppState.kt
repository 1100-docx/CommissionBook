package com.yifeng.commissionbook

import android.content.Context
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
        Reminders.reschedule(context, items, prefs.reminderEnabled)
    }

    fun reload() {
        items = store.loadCommissions()
        notes = store.loadNotes()
    }

    private fun persist() {
        store.saveCommissions(items)
        store.saveNotes(notes)
        // 数据一变就把提醒闹钟重排一遍 —— 最简单的做法，也最不容易漏
        Reminders.reschedule(context, items, prefs.reminderEnabled)
    }

    // MARK: - 增删改

    fun upsert(c: Commission) {
        val i = items.indexOfFirst { it.id == c.id }
        items = if (i >= 0) items.toMutableList().also { it[i] = c } else items + c
        persist()
    }

    fun delete(id: String) {
        items = items.filterNot { it.id == id }
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

    /** 恢复备份：整批换掉 */
    fun replaceAll(newItems: List<Commission>, newNotes: Map<String, String>) {
        items = newItems
        notes = newNotes
        persist()
    }

    /** 全部数据打成一个 JSON 字符串（备份用，跟 iOS 版同一个格式） */
    fun toJsonString(): String = Backup.encode(items, notes)

    // MARK: - 屏幕上要用的几个数

    /**
     * 当前模式下的全部条目（含已归档）。
     * 两边账本**各记各的** —— 当买家约的稿、当画师接的单，是两摞，
     * 切模式只看见自己那一摞。老数据没有 mode → 全归「买家」。
     */
    val modeItems: List<Commission> get() = items.filter { it.mode == appMode.key }

    val active: List<Commission> get() = modeItems.filter { !it.archived }

    val activeTotal: Double get() = active.sumOf { it.total }

    val activePaid: Double get() = active.sumOf { it.deposit }
}
