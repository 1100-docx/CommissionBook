package com.yifeng.commissionbook

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃记录（2026-10-07 加）。
 *
 * **为什么加**：一位用户的华为 nova 3（鸿蒙 2.0 / 麒麟 970）打开「年度报告」闪退，
 * 没有堆栈就只能猜。加了它，以后任何人报闪退，都能拿到真原因。
 *
 * ⚠️ **三条铁规矩，动它之前先读**：
 *
 *  ① **纯本地**：只往 App 私有目录写文件，**不发任何网络请求、不要任何权限**。
 *     隐私政策里那句「唯一的联网动作 = 读一个版本号文件」一个字都不用改。
 *  ② **用户手动发**：开 App 时弹一句问一下，**他点了才**走系统分享面板。
 *     绝不偷偷上报 —— 这是这个 App 的立身之本（它敢说不联网，就真得不联网）。
 *  ③ **内容里没有账本数据**：只有崩溃原因、机型、系统版本、版本号，
 *     外加一句「最后停在哪个页面」。画师名 / 金额 / 稿件名一概不碰。
 *
 * ⚠️ **两条线索，缺一不可**：
 *
 *  - **Java 堆栈**（[javaCrash]）：普通异常崩的，堆栈最直接。
 *  - **异常退出 + 面包屑**（[checkAbnormalExit]）：**原生崩溃（SIGSEGV 那种图形层崩溃）
 *    根本不走 Java 层，抓不到堆栈** —— 华为鸿蒙上这恰恰是高发类型。
 *    所以还留了一手：App 在前台时置一个标记，正常退到后台再清掉。
 *    下次启动发现标记还在 = 上次是崩着/被强杀掉退出的 → 记一条，
 *    并带上「最后停在哪个页面」这条面包屑。**没有堆栈也能定位。**
 *
 * ⚠️ `BuildConfig` 是编译期常量：通用版写「通用版」、兼容版写「兼容版」——
 *    一份代码两个包，日志里一眼分得清是哪个包崩的。
 */
object CrashLog {

    private const val SP = "crash_log"
    private const val KEEP_DAYS = 30
    private const val MAX_SHOW = 1          // 一次只问最新那一条，别攒一堆烦人

    private fun sp(c: Context) = c.getSharedPreferences(SP, Context.MODE_PRIVATE)
    private fun dir(c: Context) = File(c.filesDir, "crash").apply { mkdirs() }

    // ---------- 装上去 ----------

    /** 在 [App.onCreate] 里调一次 */
    fun install(app: Application) {
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { writeRecord(app, javaCrash(app, t, e), "crash") }
            // 记完把「前台」标记放掉 —— 不然下次启动会再补一条「异常退出」，同一件事报两遍
            runCatching { sp(app).edit().putBoolean("foreground", false).commit() }
            // ⚠️ 交回给系统：该弹「应用已停止」还弹，该杀进程还杀。**我们不吞异常**。
            old?.uncaughtException(t, e)
        }
    }

    // ---------- 会话心跳 ----------

    /** Activity.onStart / 回到前台 */
    fun onForeground(c: Context) = sp(c).edit().putBoolean("foreground", true).apply()

    /**
     * Activity.onStop / 退到后台。
     * ⚠️ 这一步是把「正常退出」记下来：之后进程被系统回收、被一键清理，都算**正常**，
     *    不会误报成崩溃。
     */
    fun onBackground(c: Context) = sp(c).edit().putBoolean("foreground", false).apply()

    /** 记一句「现在停在哪儿」——崩溃日志里最有用的那一行 */
    fun breadcrumb(c: Context, where: String) {
        sp(c).edit().putString("where", where).apply()
    }

    /**
     * 上次是不是「没正常退出」。在 MainActivity.onCreate 里调一次。
     * 是 → 补一条记录（**没有堆栈也没关系**，面包屑往往就够了）。
     */
    fun checkAbnormalExit(c: Context) {
        prune(c)
        val p = sp(c)
        if (!p.getBoolean("foreground", false)) return
        p.edit().putBoolean("foreground", false).apply()
        writeRecord(c, abnormalText(c), "abnormal")
    }

    /**
     * 拼一封「发给作者」的邮件（`mailto:`，跟反馈页同一套写法、同一个地址）。
     * ⚠️ 手工拼，不用 `Uri.Builder` 那套 —— 地址里的 `@` 被转义成 `%40` 的话，
     *    有些邮件 App 会当成坏地址、草稿打不开（iOS 那边踩过，见 Feedback.swift 的注释）。
     * 没装邮件 App 的话这里返回的 Intent 起不来，调用处**兜底走系统分享**。
     */
    fun mailIntent(text: String): Intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, "约稿账本 · 崩溃记录")
        putExtra(Intent.EXTRA_TEXT, text)
    }

    // ---------- 给界面用 ----------

    /** 有没有还没给用户看过的记录。有 → 返回「文件 + 内容」 */
    fun pending(c: Context): Pair<File, String>? {
        prune(c)
        val seen = sp(c).getStringSet("seen", emptySet())?.toSet() ?: emptySet()
        val f = dir(c).listFiles { it -> it.isFile && it.name.endsWith(".txt") }
            ?.sortedByDescending { it.name }
            ?.take(MAX_SHOW)
            ?.firstOrNull { it.name !in seen }
            ?: return null
        val text = runCatching { f.readText() }.getOrNull() ?: return null
        return f to text
    }

    /** 用户看过了（点了「发出去」或「先不用」）→ 不再问这一条 */
    fun markSeen(c: Context, f: File) {
        val p = sp(c)
        val seen = (p.getStringSet("seen", emptySet()) ?: emptySet()).toMutableSet()
        seen.add(f.name)
        p.edit().putStringSet("seen", seen).apply()
    }

    /**
     * 一条记录（2026-10-07 加）。
     *
     * 之前的 `pending()` 只吐「文件 + 一整段文字」，够弹单子用；
     * 但要摆成一屏给人看，就得把里头那几行拆开 —— 时间、类型、最后停在哪各占一行，
     * 堆栈折起来藏好（上来就铺一整屏堆栈会吓人）。
     */
    class Record internal constructor(val file: File, val text: String) {

        /** 「时间：2026-10-07 14:12:33」；信号那份没写这行，退回用文件时间 */
        val time: String = line("时间").ifBlank { tsFmt.format(Date(file.lastModified())) }

        /** 「类型：Java 异常」→「Java 异常」（空串时界面自己兜底一句本地化文案） */
        val kind: String = line("类型")

        /** 「最后停在：年度报告」→「年度报告」；没记到就给空串，界面不显示这行 */
        val lastOn: String = line("最后停在").let { if (it == "（没记到）") "" else it }

        /** 给人看的那段正文 —— 头几行（机型/系统/指纹）已经在卡片上单列了，这里跳过 */
        val detail: String = text.lineSequence()
            .filterNot { s -> HEAD_KEYS.any { s.startsWith("$it：") } }
            .joinToString("\n")
            .trim()
            .ifBlank { text }

        /** 取「键：值」里的「值」。取不到给空串 —— 老记录格式不完全一样，不能崩 */
        private fun line(key: String): String = text.lineSequence()
            .firstOrNull { it.startsWith("$key：") }
            ?.substringAfter("：")?.trim()
            ?: ""

        private companion object {
            val tsFmt = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
            val HEAD_KEYS = listOf("时间", "App", "类型", "包", "版本", "机型", "系统", "品牌 / 硬件", "系统显示版本", "指纹", "语言", "最后停在")
        }
    }

    /**
     * 设置页「崩溃记录」那一行用：本机存着的记录**全摆出来**（新的在前）。
     *
     * 跟 `pending()` 的区别：那个只挑「还没给用户看过的」，用来决定弹不弹单子；
     * 这个是给用户自己翻的 —— 想发哪条发哪条，不用等它崩完自己弹。
     */
    fun allRecords(c: Context): List<Record> {
        prune(c)
        return dir(c).listFiles { it -> it.isFile && it.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            ?.mapNotNull { f -> runCatching { f.readText() }.getOrNull()?.let { Record(f, it) } }
            ?: emptyList()
    }

    /** 用户自己点「清空全部记录」时调。**只删本机这几个文本文件**，别的什么都不碰 */
    fun clearAll(c: Context) {
        runCatching { dir(c).listFiles()?.forEach { it.delete() } }
        sp(c).edit().remove("seen").apply()
    }

    // ---------- 内容 ----------

    private fun head(c: Context, kind: String): String = buildString {
        appendLine("【约稿账本 · 崩溃记录】")
        appendLine("类型：$kind")
        appendLine("包：${if (BuildConfig.LEGACY_COMPAT) "老机兼容版" else "通用版"}")
        appendLine("版本：${BuildConfig.VERSION_NAME}（code ${BuildConfig.VERSION_CODE}）")
        appendLine("机型：${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("系统：Android ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）")
        appendLine("品牌 / 硬件：${Build.BRAND} / ${Build.HARDWARE}")
        appendLine("系统显示版本：${Build.DISPLAY}")
        appendLine("指纹：${Build.FINGERPRINT}")
        appendLine("语言：${Locale.getDefault()}")
        appendLine("最后停在：${sp(c).getString("where", "（没记到）")}")
    }

    private fun javaCrash(c: Context, t: Thread, e: Throwable): String =
        head(c, "Java 异常") +
            "\n线程：${t.name}\n\n" +
            android.util.Log.getStackTraceString(e)

    private fun abnormalText(c: Context): String =
        head(c, "异常退出（没有 Java 堆栈）") +
            """

            |这台机器上崩的时候没走到 Java 层 —— 多半是**原生崩溃**（图形 / 系统层那种），
            |或者被系统强杀了。上面「最后停在」那一行就是最有用的一条线索：
            |把那一步的操作告诉我，基本就能定位。
            """.trimMargin()

    // ---------- 落盘 ----------

    private fun writeRecord(c: Context, body: String, prefix: String) {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())
        runCatching {
            File(dir(c), "$prefix-${System.currentTimeMillis()}.txt")
                .writeText("时间：$ts\n$body\n")
        }
    }

    /** 超过 30 天的记录自己清掉 —— 别在人家手机里攒东西 */
    private fun prune(c: Context) {
        val cutoff = System.currentTimeMillis() - KEEP_DAYS * 24L * 60 * 60 * 1000
        runCatching {
            dir(c).listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
        }
    }
}
