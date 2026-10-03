package com.yifeng.commissionbook

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

// MARK: - 检查更新（2026-10-03 加，**只有安卓版有**）
//
// 逸风要的「自动更新功能仅限于 Android」—— iOS 那边不做（沙盒里本来也不让自装，
// 只能走 App Store；而安卓可以自己下 APK 让系统装）。
//
// ⚠️ 这个功能给整个 App 带来的最大变化不是代码，是**隐私政策**：
//    这是这个 App 第一次申请 INTERNET 权限。以前整份政策立的旗子是
//    「系统层面就没有联网能力」，现在那句不能再写了 —— 见 PrivacyScreen.kt。
//
// 设计上刻意做得很小，就是为了政策还能写得硬：
//   ① 唯一的请求 = 去读一个几行字的版本号文件（version.json），**不带任何数据**；
//   ② 下载新版本走系统安装界面，**装不装由用户点确认**（安卓不允许静默安装，
//      「真·自动装」物理上做不到，所以这里从来不说「自动安装」）；
//   ③ 设置里能一键关掉「启动时自动检查」—— 关掉之后 App 一次请求都不发。
//
// 数据源：逸风自己的 GitHub 仓库 + 三个公共镜像回退。
// ⚠️ 国内 GitHub 直链基本不通，所以镜像放在前面，直连放最后兜底。

object Updater {

    /** 版本清单（几行字的小文件，放在仓库根目录，每次发版改里面的号） */
    private const val MANIFEST = "https://raw.githubusercontent.com/1100-docx/CommissionBook/main/version.json"

    /**
     * 加速前缀。空串 = 直连 GitHub（国内多半超时，所以排最后）。
     *
     * 清单和 APK 用的是**同一组**前缀 —— 能读到清单的那个镜像，下载也走它，
     * 省得出现「检查得到、下不动」这种半截状态。
     *
     * ⚠️ **顺序是 2026-10-03 实测出来的，别凭感觉改**（下 1.89MB 的包）：
     *      gh-proxy.com   ✅ 完整 · 4.5 秒   ← 所以排第一
     *      ghproxy.net    ✅ 完整 · 17.6 秒
     *      ghfast.top     ❌ 只下到 1.08MB 就断（超时 120 秒）—— 慢到会截断
     *      直连           ❌ 小文件（version.json）行，1.89MB 的包超时
     *    好在 [download] 里有 sha256 校验：截断的包会被识别出来丢掉、自动换下一个源。
     */
    private val MIRRORS = listOf(
        "https://gh-proxy.com/",
        "https://ghproxy.net/",
        "https://ghfast.top/",
        "",
    )

    /** version.json 里长这样：{"versionCode":32,"versionName":"3.5.8","url":"...","notes":"...","sha256":"..."} */
    data class Release(
        val versionCode: Int,
        val versionName: String,
        val url: String,
        val notes: String,
        val sha256: String,
    )

    enum class InstallResult { OK, NEED_PERMISSION, FAILED }

    // ---------- 本机版本 ----------

    fun currentCode(context: Context): Int = runCatching {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
                .longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0).versionCode
        }
    }.getOrDefault(0)

    fun currentName(context: Context): String = runCatching {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0)).versionName
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0).versionName
        }
    }.getOrNull() ?: "?"

    // ---------- 联网 ----------

    /**
     * 读版本清单。镜像挨个试，全挂了返回 null。
     *
     * ⚠️ 这是个**阻塞**调用，必须丢到 IO 线程跑（[doCheck] 里已经包好了）。
     * 超时给得短（6 秒）—— 这是启动时顺手做的一件事，不能让用户对着白屏等。
     */
    fun fetch(): Release? {
        for (base in MIRRORS) {
            val text = readText(base + MANIFEST) ?: continue
            parse(text)?.let { return it }
        }
        return null
    }

    private fun readText(url: String): String? = try {
        val conn = open(url, 6000, 8000)
        if (conn.responseCode in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            null
        }
    } catch (t: Throwable) {
        // 这个源不通，交给外层换下一个
        null
    }

    /**
     * 下载 APK。同样挨个镜像试，成功了才返回文件。
     *
     * 落点选 App 自己的外部私有目录（`Android/data/<包名>/files/update/`）：
     * ① 不用申请任何存储权限；② 卸载 App 会跟着删掉，不留垃圾。
     */
    fun download(context: Context, release: Release, onProgress: (Int) -> Unit): File? {
        val dir = File(context.getExternalFilesDir(null) ?: context.cacheDir, "update")
        runCatching { dir.mkdirs() }
        runCatching { dir.listFiles()?.forEach { it.delete() } }
        val out = File(dir, "CommissionBook_${release.versionName}.apk")

        for (base in MIRRORS) {
            try {
                val conn = open(base + release.url, 8000, 20000)
                if (conn.responseCode !in 200..299) {
                    conn.disconnect()
                    continue
                }
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    FileOutputStream(out).use { fos ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var last = -1
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            fos.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val pct = ((done * 100) / total).toInt()
                                if (pct != last) {
                                    last = pct
                                    onProgress(pct)
                                }
                            }
                        }
                        fos.flush()
                    }
                }
                conn.disconnect()

                if (out.length() <= 0L) {
                    out.delete()
                    continue
                }
                // 清单里给了校验值就一定核一遍：国内镜像偶尔会给你一个半截文件
                if (release.sha256.isNotEmpty() &&
                    !sha256(out).equals(release.sha256, ignoreCase = true)
                ) {
                    out.delete()
                    continue
                }
                return out
            } catch (t: Throwable) {
                runCatching { out.delete() }
            }
        }
        return null
    }

    /**
     * 拉起系统的安装界面。
     *
     * ⚠️ 安卓 8 起，「装未知来源的 App」是一个**开关**（按 App 授权），不是装的时候弹一次。
     *    没开就把用户送到那个设置页，然后如实告诉他「回来再点一次」——
     *    假装什么都没发生、只说一句「安装失败」，那才叫让人抓瞎。
     */
    fun install(context: Context, file: File): InstallResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return InstallResult.NEED_PERMISSION
        }
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val i = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(i)
            InstallResult.OK
        } catch (t: Throwable) {
            InstallResult.FAILED
        }
    }

    // ---------- 小工具 ----------

    /**
     * 发一个请求，**自己处理跳转**（最多 5 跳）。
     *
     * 为什么不用 `instanceFollowRedirects = true` 省事：
     * 加速镜像全是 302 到真实地址，而且有的会跳到一个**不同协议/不同域**的地址，
     * 默认跟随在这些情况下会静默失败，报错还是一句「无法连接」——查都没法查。
     */
    private fun open(url: String, connectMs: Int, readMs: Int): HttpURLConnection {
        var target = url
        var hops = 0
        while (true) {
            val c = URL(target).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = connectMs
            c.readTimeout = readMs
            c.setRequestProperty("User-Agent", "CommissionBook-Android")
            c.connect()
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location")
                c.disconnect()
                if (loc.isNullOrEmpty() || hops++ >= 5) throw IllegalStateException("too many redirects")
                target = if (loc.startsWith("http")) loc else URL(URL(target), loc).toString()
                continue
            }
            return c
        }
    }

    private fun parse(text: String): Release? = try {
        val o = JSONObject(text)
        val code = o.optInt("versionCode", 0)
        val name = o.optString("versionName", "")
        val url = o.optString("url", "")
        if (code <= 0 || url.isEmpty()) null
        else Release(code, name, url, o.optString("notes", ""), o.optString("sha256", ""))
    } catch (t: Throwable) {
        null
    }

    private fun sha256(f: File): String = try {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (t: Throwable) {
        ""
    }
}

// MARK: - 检查更新这一段的状态

sealed class UpdateStatus {
    /** 正在读版本清单 */
    object Checking : UpdateStatus()

    /** 已经是最新的（或者这一版被用户跳过了） */
    object UpToDate : UpdateStatus()

    /** 清单读不到 —— 网络不通，或者四个源全挂了 */
    object Failed : UpdateStatus()

    /** 有新版本 */
    data class Available(val release: Updater.Release) : UpdateStatus()

    /** 正在下载，pct = 0..100（拿不到总长度时一直是 0） */
    data class Downloading(val pct: Int) : UpdateStatus()

    /** 下载完了，正在拉系统安装界面 */
    object Installing : UpdateStatus()

    /** 头一次装，得先允许「安装未知应用」 */
    object NeedPermission : UpdateStatus()

    /** 下载失败 —— 所有源都没拿到完整文件 */
    object DownloadFailed : UpdateStatus()

    /** 系统安装界面拉不起来（少见，可能是系统限制） */
    object InstallFailed : UpdateStatus()
}

/**
 * 跑一次「检查更新」。
 *
 * 抽成顶层函数是为了**两个地方共用**：设置里手动点，和启动时静默查一次。
 * 启动那次不要中间态（不能一开 App 就闪一个「正在检查」），传个空回调进去就行。
 */
suspend fun doCheck(
    context: Context,
    prefs: Prefs,
    onStatus: (UpdateStatus) -> Unit,
): UpdateStatus {
    onStatus(UpdateStatus.Checking)

    val r = withContext(Dispatchers.IO) { Updater.fetch() }

    // 不管成没成，都记一笔时间 —— 否则网络不通时每次冷启动都要白等 6 秒超时
    prefs.lastUpdateCheckAt = System.currentTimeMillis()

    val out = when {
        r == null -> UpdateStatus.Failed
        r.versionCode <= Updater.currentCode(context) -> UpdateStatus.UpToDate
        // 用户说过「跳过这个版本」就别再拿它烦他（下一个版本的号更大，自然又会提示）
        r.versionCode == prefs.skippedUpdateCode -> UpdateStatus.UpToDate
        else -> UpdateStatus.Available(r)
    }
    onStatus(out)
    return out
}

// MARK: - 界面

/**
 * 检查更新 / 下载 / 安装引导，全在这一个框里。
 *
 * 状态由调用方拿着（`status == null` 就什么都不画），框自己负责把状态推回去 ——
 * 这样设置页和启动时那两处都只用记住一个 `UpdateStatus?` 变量，不用各写一遍流程。
 */
@Composable
fun UpdateDialog(
    status: UpdateStatus?,
    prefs: Prefs,
    onStatus: (UpdateStatus?) -> Unit,
) {
    val s = status ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 下好还没装上时留在手里：允许「安装未知应用」回来之后要拿它再装一次
    var apk by remember { mutableStateOf<File?>(null) }
    val cs = MaterialTheme.colorScheme

    /** 下载 + 拉起安装，一步到底 */
    fun downloadAndInstall(release: Updater.Release) {
        onStatus(UpdateStatus.Downloading(0))
        scope.launch {
            val f = withContext(Dispatchers.IO) {
                Updater.download(context, release) { pct ->
                    onStatus(UpdateStatus.Downloading(pct))
                }
            }
            if (f == null) {
                onStatus(UpdateStatus.DownloadFailed)
                return@launch
            }
            apk = f
            onStatus(UpdateStatus.Installing)
            when (Updater.install(context, f)) {
                Updater.InstallResult.OK -> onStatus(null)
                Updater.InstallResult.NEED_PERMISSION -> onStatus(UpdateStatus.NeedPermission)
                Updater.InstallResult.FAILED -> onStatus(UpdateStatus.InstallFailed)
            }
        }
    }

    when (s) {
        is UpdateStatus.Checking -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text(AppCtx.s(R.string.settings_update_check)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(AppCtx.s(R.string.settings_update_checking), fontSize = 14.sp)
                }
            },
        )

        is UpdateStatus.UpToDate -> AlertDialog(
            onDismissRequest = { onStatus(null) },
            confirmButton = {
                TextButton(onClick = { onStatus(null) }) { Text(AppCtx.s(R.string.update_ok)) }
            },
            title = { Text(AppCtx.s(R.string.settings_update_check)) },
            text = {
                Text(
                    AppCtx.s(R.string.settings_update_latest, Updater.currentName(context)),
                    fontSize = 14.sp,
                )
            },
        )

        is UpdateStatus.Failed -> AlertDialog(
            onDismissRequest = { onStatus(null) },
            confirmButton = {
                TextButton(onClick = { scope.launch { doCheck(context, prefs, onStatus) } }) {
                    Text(AppCtx.s(R.string.settings_update_retry))
                }
            },
            dismissButton = {
                TextButton(onClick = { onStatus(null) }) { Text(AppCtx.s(R.string.update_close)) }
            },
            title = { Text(AppCtx.s(R.string.settings_update_check)) },
            text = { Text(AppCtx.s(R.string.settings_update_failed), fontSize = 14.sp) },
        )

        is UpdateStatus.DownloadFailed -> AlertDialog(
            onDismissRequest = { onStatus(null) },
            confirmButton = {
                TextButton(onClick = { onStatus(null) }) { Text(AppCtx.s(R.string.update_ok)) }
            },
            title = { Text(AppCtx.s(R.string.settings_update_check)) },
            text = { Text(AppCtx.s(R.string.update_download_failed), fontSize = 14.sp) },
        )

        is UpdateStatus.Available -> AlertDialog(
            // 点旁边空白 = 稍后再说（不写死在按钮上，免得三个按钮挤成一排）
            onDismissRequest = { onStatus(null) },
            title = { Text(AppCtx.s(R.string.update_title, s.release.versionName)) },
            text = {
                Column {
                    Text(
                        AppCtx.s(R.string.update_notes_title),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = cs.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (s.release.notes.isBlank()) AppCtx.s(R.string.update_no_notes) else s.release.notes,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = cs.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { downloadAndInstall(s.release) }) {
                    Text(AppCtx.s(R.string.update_download))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    prefs.skippedUpdateCode = s.release.versionCode
                    onStatus(null)
                }) { Text(AppCtx.s(R.string.update_skip)) }
            },
        )

        is UpdateStatus.Downloading -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text(AppCtx.s(R.string.settings_update_check)) },
            text = {
                Column {
                    Text(
                        AppCtx.s(R.string.update_downloading, s.pct),
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    // 拿不到总长度时（contentLength = -1）画成一条「转圈」的进度条
                    if (s.pct > 0) {
                        LinearProgressIndicator(
                            progress = { s.pct / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            },
        )

        is UpdateStatus.Installing -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text(AppCtx.s(R.string.settings_update_check)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(AppCtx.s(R.string.update_opening), fontSize = 14.sp)
                }
            },
        )

        is UpdateStatus.NeedPermission -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {
                TextButton(onClick = {
                    val f = apk
                    if (f == null) {
                        onStatus(null)
                    } else {
                        onStatus(UpdateStatus.Installing)
                        when (Updater.install(context, f)) {
                            Updater.InstallResult.OK -> onStatus(null)
                            Updater.InstallResult.NEED_PERMISSION -> onStatus(UpdateStatus.NeedPermission)
                            Updater.InstallResult.FAILED -> onStatus(UpdateStatus.InstallFailed)
                        }
                    }
                }) { Text(AppCtx.s(R.string.update_retry_install)) }
            },
            dismissButton = {
                TextButton(onClick = { onStatus(null) }) { Text(AppCtx.s(R.string.update_close)) }
            },
            title = { Text(AppCtx.s(R.string.settings_update_check)) },
            text = { Text(AppCtx.s(R.string.update_need_permission), fontSize = 14.sp) },
        )

        is UpdateStatus.InstallFailed -> AlertDialog(
            onDismissRequest = { onStatus(null) },
            confirmButton = {
                TextButton(onClick = { onStatus(null) }) { Text(AppCtx.s(R.string.update_ok)) }
            },
            title = { Text(AppCtx.s(R.string.settings_update_check)) },
            text = {
                Text(
                    AppCtx.s(R.string.update_install_failed, apk?.absolutePath ?: "?"),
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
            },
        )
    }
}
