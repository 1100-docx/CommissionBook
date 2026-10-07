package com.yifeng.commissionbook

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/*
 * 二维码传输（2026-10-07 加，安卓侧）。
 *
 * 逸风原话：「加个"扫码传输"或者"二维码导入"的功能：旧手机生成二维码，新手机扫码就能导入，
 *           比传文件直观多了，双端都做」+「类似于手机搬家，点击二维码传输会弹窗提示询问
 *           只是新手机还是旧手机，如果是新手机就扫描，旧手机就 Sheet 弹出生成的二维码」
 *          +「AB都做吧，如果文件过大就提示使用局域网连接传输」
 *          +「如果不在同一个局域网里也要给出提示哦」
 *
 * 所以这里其实有**两条路**，一条都不用用户选，代码按数据大小自己挑：
 *
 *   ① 内联二维码（"B"）：整份备份 → gzip → base64 → **直接塞进二维码里**。
 *      纯离线：不联网、不开服务、两台手机挨着就能走。
 *      ⚠️ 但二维码有物理上限（版本 40、L 级纠错约 2953 字节），塞不下就自动转 ②。
 *
 *   ② 局域网直传（"A"）：旧手机起一个**临时**小 HTTP 服务，二维码里放的是一条
 *      `http://<手机在局域网里的地址>:<随机端口>/cb`；新手机扫码后直接从旧手机把
 *      整份备份（含参考图）拉过去。
 *      ⚠️ 需要两台手机连**同一个 Wi-Fi** —— 连不上时的提示是专门写过的
 *      （见 [QR_FAIL_HINT] 那一段），别改成「传输失败」这种没用的废话。
 *
 * ⚠️ 它给 App 带来的新东西：**相机权限**（扫码）。隐私政策里那条要跟着更新。
 *    网络权限早就有（自动更新那会儿加的），所以 ② 不用额外申请。
 *
 * ⚠️ 服务是**一次性的**：传完一份就自己关掉，最长挂 5 分钟。
 *    别做成常驻 —— 这个 App 的卖点之一是「不在后台偷偷干活」。
 */

/** 内联二维码的前缀。扫码结果以它开头 = 数据就在码里，不用联网。 */
private const val PREFIX_INLINE = "CB1:"

/** 内联二维码的字符上限。留了余量：太满的码在旧手机镜头上很难对焦。 */
private const val INLINE_LIMIT = 1500

/** 旧手机那个小服务最多挂多久（毫秒）—— 超了自生自灭，不留后台 */
private const val SERVE_TIMEOUT_MS = 5 * 60 * 1000L

object QrTransfer {

    /** 这一趟到底走哪条路 */
    enum class Route { INLINE, LAN }

    /** 旧手机准备好的一份「码里要装的东西」 */
    data class Ready(val route: Route, val code: String)

    /**
     * 把备份文本打包成「二维码里该装的东西」。
     *
     * 先试内联；塞不下就起局域网服务。所以这个函数**可能有副作用**（会在 ② 里开端口），
     * 调用点只在「旧手机点了出码」那一下。
     *
     * @return null = 连服务都起不来（比如没连 Wi-Fi），调用方去提示用户。
     */
    fun pack(text: String): Ready? {
        val squeezed = runCatching {
            val raw = ByteArrayOutputStream()
            GZIPOutputStream(raw).use { it.write(text.toByteArray()) }
            Base64.encodeToString(raw.toByteArray(), Base64.NO_WRAP)
        }.getOrNull()

        if (squeezed != null && PREFIX_INLINE.length + squeezed.length <= INLINE_LIMIT) {
            return Ready(Route.INLINE, PREFIX_INLINE + squeezed)
        }
        // 太大了（多半是备份里带着参考图）—— 转局域网直传
        val url = LanServer.start(text) ?: return null
        return Ready(Route.LAN, url)
    }

    /** 传输结束（新手机那边导入完了 / 用户把 Sheet 关了）—— 把服务收掉 */
    fun finish() = LanServer.stop()

    /**
     * 扫到的内容 → 备份文本。
     *
     * 认得出两种情况，都不认就返回 null：
     *  · `CB1:…`         → 当场解开
     *  · `http://…`      → 去旧手机那把数据拉回来
     *
     * ⚠️ 回来的错要分清楚：**拉不到**（多半不在同一个 Wi-Fi）和**拉到了但读不懂**
     *    是两回事，提示不一样。所以这里用 [Result] 而不是裸字符串。
     */
    sealed interface Result {
        data class Ok(val text: String) : Result
        /** 内联码解开了，但内容坏了 */
        object BadData : Result
        /** 网络那步没成 —— 十有八九不在同一个局域网 */
        object NotReachable : Result
        /** 扫的压根不是我们家的码 */
        object NotOurs : Result
    }

    fun unpack(scanned: String): Result {
        val s = scanned.trim()
        return when {
            s.startsWith(PREFIX_INLINE) -> {
                val body = s.removePrefix(PREFIX_INLINE)
                val text = runCatching {
                    val bytes = Base64.decode(body, Base64.NO_WRAP)
                    GZIPInputStream(bytes.inputStream()).use { it.readBytes().decodeToString() }
                }.getOrNull()
                if (text == null) Result.BadData else Result.Ok(text)
            }

            s.startsWith("http://") || s.startsWith("https://") -> {
                download(s)?.let { Result.Ok(it) } ?: Result.NotReachable
            }

            else -> Result.NotOurs
        }
    }

    /** 去旧手机那把整份备份拉回来。拉不到返回 null（调用方给「同一个 Wi-Fi」的提示）。 */
    private fun download(url: String): String? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 20000
            requestMethod = "GET"
        }
        BufferedInputStream(conn.inputStream).use { it.readBytes().decodeToString() }
    }.getOrNull()

    /**
     * 造二维码位图。
     *
     * ⚠️ 纠错级别特意挑 **L**（最低）：级别越高越好看，但能装的数据越少 ——
     *    我们要的是「尽量装得下」，不是「脏了一点还能扫」。
     * ⚠️ MARGIN 给 1：留白不够的话有些扫码器认不出（标准要求 4，但屏幕上 1 也够，还能省地方）。
     */
    fun qrBitmap(text: String, size: Int = 760): Bitmap? = runCatching {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
            EncodeHintType.MARGIN to 1,
        )
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        bmp
    }.getOrNull()
}

/**
 * 旧手机那边临时起的小服务。
 *
 * 自己拿 [ServerSocket] 拼 HTTP，不引框架 —— 我们要发的就一个 GET、一个响应体，
 * 用不上路由、静态文件那些东西。
 *
 * ⚠️ 三条讲究：
 *  ① 端口用 0 让系统随便给（省得跟别的 App 撞车）；
 *  ② 只认「一次」传输就自己关 —— 传完还挂着，用户会以为 App 在后台联网；
 *  ③ `soTimeout` 必须有：不然 accept 会一直挂着，[stop] 都叫不醒它。
 */
private object LanServer {

    private var server: ServerSocket? = null
    private var worker: Thread? = null

    @Volatile private var payload: ByteArray = ByteArray(0)

    /** 起服务，返回二维码里该放的那条地址；起不来（没连 Wi-Fi 之类）返回 null */
    fun start(text: String): String? {
        stop()

        val ip = localIp() ?: return null
        val ss = runCatching { ServerSocket(0) }.getOrNull() ?: return null
        server = ss
        payload = text.toByteArray()

        val url = "http://$ip:${ss.localPort}/cb"
        worker = Thread {
            val deadline = System.currentTimeMillis() + SERVE_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline && !ss.isClosed) {
                runCatching { ss.soTimeout = 20000 }
                val sock = runCatching { ss.accept() }.getOrNull() ?: continue
                runCatching { respond(sock) }
            }
            runCatching { ss.close() }
        }.also {
            it.isDaemon = true
            it.start()
        }
        return url
    }

    fun stop() {
        runCatching { server?.close() }
        server = null
        worker = null
        payload = ByteArray(0)
    }

    /** 就一回：读掉请求头（不解析），把整份备份写回去 */
    private fun respond(sock: Socket) = sock.use { s ->
        s.soTimeout = 10000
        val input = s.getInputStream()
        // 请求头读完（读到空行）就够了 —— 我们不看它要什么，就那一个端点
        val buf = ByteArray(1024)
        var guard = 0
        while (guard++ < 64) {
            val n = runCatching { input.read(buf) }.getOrDefault(-1)
            if (n <= 0) break
            if (String(buf, 0, n, Charsets.ISO_8859_1).contains("\r\n\r\n")) break
        }

        val body = payload
        val head = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        val out = s.getOutputStream()
        out.write(head.toByteArray(Charsets.ISO_8859_1))
        out.write(body)
        out.flush()
    }

    /**
     * 本机在局域网里的地址（192.168 / 10. / 172.16-31 那几段）。
     * 拿不到就是「没连 Wi-Fi」，调用方回去走「连同一个 Wi-Fi」的提示。
     *
     * ⚠️ 不用 `WifiManager` 那套（Android 10 起拿它读地址一大堆限制），
     *    直接翻网卡最省事，也不挑系统版本。
     */
    private fun localIp(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { nif ->
            if (!nif.isUp || nif.isLoopback) return@forEach
            nif.inetAddresses.toList().forEach { addr ->
                if (!addr.isLoopbackAddress && addr is Inet4Address) {
                    val host = addr.hostAddress ?: return@forEach
                    if (host.startsWith("192.168.") ||
                        host.startsWith("10.") ||
                        host.startsWith("172.")
                    ) return host
                }
            }
        }
        null
    }.getOrNull()
}

// ---------- 界面那两块（弹窗 + 出码 Sheet） ----------

/** 点「二维码传输」先问这一句：这台是新手机还是旧手机 */
@Composable
fun QrRoleDialog(
    onNew: () -> Unit,
    onOld: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AppCtx.s(R.string.qr_role_title)) },
        text = { Text(AppCtx.s(R.string.qr_role_desc)) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onNew) {
                Text(AppCtx.s(R.string.qr_role_new))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onOld) {
                Text(AppCtx.s(R.string.qr_role_old))
            }
        },
    )
}

/**
 * 旧手机那张出码 Sheet。
 *
 * ⚠️ 打包（[QrTransfer.pack]）要放在 `LaunchedEffect` 里只跑一次 ——
 *    它是**有副作用**的（局域网那条路会开端口）。放进 `remember` 都会在重组里重跑，
 *    端口就一遍遍开了又关，扫码的人永远连不上。
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun QrShowSheet(
    backupText: String,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var ready by remember { mutableStateOf<QrTransfer.Ready?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(backupText) {
        val r = QrTransfer.pack(backupText)
        if (r == null) failed = true else ready = r
    }

    val bmp = remember(ready) { ready?.let { QrTransfer.qrBitmap(it.code) } }

    androidx.compose.material3.ModalBottomSheet(onDismissRequest = {
        QrTransfer.finish()
        onClose()
    }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = AppCtx.s(R.string.qr_show_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = AppCtx.s(R.string.qr_show_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(20.dp))

            when {
                failed -> Text(
                    text = AppCtx.s(R.string.qr_show_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )

                bmp == null -> Text(
                    text = AppCtx.s(R.string.qr_show_cant_draw),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )

                else -> {
                    // 二维码底下垫一层白：暗色模式下黑码白底才扫得出来
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(260.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(androidx.compose.ui.graphics.Color.White)
                            .padding(6.dp),
                    )
                    Spacer(Modifier.height(14.dp))
                    val isLan = ready?.route == QrTransfer.Route.LAN
                    Text(
                        text = AppCtx.s(
                            if (isLan) R.string.qr_show_lan_hint else R.string.qr_show_inline_hint
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isLan) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }
            }
        }
    }
}
