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

    val active: List<Commission> get() = items.filter { !it.archived }

    val activeTotal: Double get() = active.sumOf { it.total }

    val activePaid: Double get() = active.sumOf { it.deposit }
}
