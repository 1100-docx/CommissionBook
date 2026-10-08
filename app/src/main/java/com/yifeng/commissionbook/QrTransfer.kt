package com.yifeng.commissionbook

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.delay
import java.io.BufferedInputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URL
import java.util.UUID

/*
 * 二维码传输（2026-10-08 加，安卓侧）。
 *
 * 逸风 2026-10-08 定的规矩（比 10-07 那版收窄了不少）：
 *   「我想的就是局域网扫码传输」
 *   「扫码之后先确定是不是在同一个局域网里，如果不是，就弹窗提示不在同一 Wi-Fi 下，
 *     请将两部手机连接至同一 Wi-Fi；如果是在同一 Wi-Fi 下就直接传输并恢复」
 *   「每张二维码都只有 5 分钟时效，不能分享使用」
 *   「截屏也不行」
 *
 * 现在这套是**两条锁**（2026-10-08 晚改过一次，见下面那段）：
 *  ① **只走局域网**（10-07 那版的「内联码」已经砍掉）：码里放的是
 *     `http://<旧手机局域网地址>:<随机端口>/cb/<一次性口令>`，
 *     两台手机不在同一个 Wi-Fi 就根本连不上 —— 光这一点，截屏发到外网就没用了。
 *  ② **只认一次 + 5 分钟到点自毁**：成功传完一份，这一整个会话立刻作废
 *     （之后再来的一律 410）；整场 5 分钟，到点自己关。
 *
 * ⚠️ **为什么没有「口令每 10 秒轮换」了**（这条 10-08 晚撤掉，别以为是漏了）：
 *    那一版是为了「截屏也不行」加的 —— 二维码本身是张图，静态的怎么设计都会被拍走，
 *    唯一能让它失效的办法就是**让它变得比传播还快**。
 *    但逸风装机实测后说：「五分钟倒计时内也会一直变，这不应该，应该五分钟之后才变」。
 *    **这两条要求本来就是互斥的**：码不变 → 截图在 5 分钟内有效；
 *    码一直变 → 看着像个故障。他选了「码要稳定」，所以轮换去掉。
 *    代价得说清楚：**这 5 分钟里，如果有人截了屏、又正好跟你在同一个 Wi-Fi、
 *    而且你还没扫过，理论上他能抢在你前面把备份拉走。**
 *    哪天想把天平扳回"防抢"那一侧，把轮换加回来就行（3.9.1 那版就在 git 里）。
 *
 * ⚠️ 「是不是同一个局域网」**没有 API 能直接问**（读 SSID 要走定位权限，Android 10 起
 *    还被限得厉害）。唯一可靠且不要额外权限的判断就是**真的去连一下对面那个地址** ——
 *    连不上 = 不在同一个网。所以 [fetch] 里「先 connect、再读」是故意分成两段的。
 *
 * ⚠️ 它给 App 带来的新东西：**相机权限**（扫码）。隐私政策里那条要跟着更新。
 *    网络权限早就有（自动更新那会儿加的）。
 *
 * ⚠️ 服务是**一次性的**：传完 / 到点就自己关，绝不会在后台挂着。
 */

/** 整张码的总寿命：5 分钟（逸风定）。到点整张作废，中途**不再换码**。 */
private const val SESSION_TTL_MS = 5 * 60 * 1000L

/** 探对面那台在不在同一个网 —— 要短，用户在取景页等着呢 */
private const val PROBE_TIMEOUT_MS = 4000

object QrTransfer {

    /** 当前这场传输（一次只有一场）。没在传就是 null。 */
    @Volatile
    private var session: Session? = null

    /**
     * 上一次「连不上」的具体原因 —— 一句原话（2026-10-08 晚加）。
     *
     * ⚠️ 为什么要把这种技术原话抛到界面上：这一轮的 bug（安卓明文 HTTP 被系统拦掉）
     *    症状就是「不在同一个 Wi-Fi」，**跟真的不在同一个网长得一模一样** ——
     *    来回猜了两轮。有了这句，下一次一眼就能看出是谁的问题。
     */
    @Volatile
    var lastFailureReason: String? = null
        private set

    /**
     * 旧手机点「出码」时调一次 —— 起小服务、生成第一代口令。
     *
     * 起不来（比如没连 Wi-Fi，拿不到局域网地址）返回 null，
     * 调用方去提示「两台手机先连上同一个 Wi-Fi」。
     *
     * ⚠️ **有副作用**（开端口 + 起两个线程），只能调一次，别放进重组里。
     *
     * @param onUsed    对面真把数据拉走了 —— 界面该把码换成「已经扫过了」
     * @param onExpired 到点了 —— 界面该换成「这张码已经失效了」
     */
    fun start(backupText: String, onUsed: () -> Unit, onExpired: () -> Unit): Session? {
        stop()
        val s = LanServer.start(backupText, SESSION_TTL_MS, onUsed, onExpired) ?: return null
        session = s
        return s
    }

    /** 收工（用户把 Sheet 关了 / 界面销毁了）—— 立刻收掉服务，别留后台 */
    fun stop() {
        runCatching { session?.stop() }
        session = null
    }

    /**
     * 扫到的内容 → 备份文本。
     *
     * 认不出就返回 null（扫的不是我们家的码）。
     */
    sealed interface Result {
        data class Ok(val text: String) : Result

        /** 连不上 —— 十有八九不在同一个局域网。这条要**弹窗**，不是小提示。 */
        object NotReachable : Result

        /** 对面的码已经被人用过了（只能一次） */
        object Used : Result

        /** 码是旧的（截屏截到的、或者对方已经换到下一代口令了） */
        object Stale : Result

        /** 连上了、也读到了，但内容坏了 */
        object BadData : Result

        /** 扫的压根不是我们家的码 */
        object NotOurs : Result
    }

    fun unpack(scanned: String): Result {
        val s = scanned.trim()
        if (!(s.startsWith("http://") || s.startsWith("https://"))) return Result.NotOurs
        return when (val r = fetch(s)) {
            is Fetch.Ok -> if (looksLikeBackup(r.text)) Result.Ok(r.text) else Result.BadData
            Fetch.Unreachable -> Result.NotReachable
            Fetch.Used -> Result.Used
            Fetch.Stale -> Result.Stale
            Fetch.Broken -> Result.BadData
        }
    }

    /** 粗看一眼像不像我们的备份 —— 别把随便一个网页当成备份灌进去 */
    private fun looksLikeBackup(text: String): Boolean =
        text.trimStart().startsWith("{") && text.contains("commissions")

    private sealed interface Fetch {
        data class Ok(val text: String) : Fetch
        object Unreachable : Fetch
        object Used : Fetch
        object Stale : Fetch
        object Broken : Fetch
    }

    /**
     * 去旧手机那把整份备份拉回来。
     *
     * ⚠️ **为什么不用 `HttpURLConnection`**（2026-10-08 晚的真凶，一定别再改回去）：
     *    安卓 9（API 28）起，`targetSdk >= 28` 的 App **默认禁止一切明文 HTTP**
     *    （`android:usesCleartextTraffic` 默认 false）。我们这包 targetSdk 是 36，
     *    于是 `http://192.168.x.x:port/...` 这种请求**在发出第一个字节之前就被系统拦掉**，
     *    抛的是 `IOException: Cleartext HTTP traffic ... not permitted`。
     *    它被下面那个 catch 当成"连不上"，界面就弹「不在同一个 Wi-Fi」——
     *    **症状跟真不在同一个网一模一样，所以查了很久。**
     *
     *    方向性的证据本来就摆着：**iOS 扫安卓通、安卓扫 iOS 不通**。
     *    如果是地址/网络的问题，两边都会挂；只有一边挂 = 问题在**那一边自己身上**。
     *
     *    修法是**不碰 HTTP 栈，直接裸 TCP socket 手拼一条 HTTP/1.1 GET** ——
     *    明文策略只约束 HTTP 库（HttpURLConnection / OkHttp / Cronet），
     *    裸 socket 不在它的管辖里。顺带还多两个好处：
     *      · 不用为了这条局域网请求去全局放行明文（App 对外的明文口子一个都没开）
     *      · 失败原因能拿到具体到 errno 的那句原话（见 [lastFailureReason]）
     *
     * ⚠️ connect 那一步和后面读的状态码是**故意分开**的：
     *    connect 失败 = 「连不上」（多半不在同一个网）；连上了才轮到解读对面回的状态码。
     */
    private fun fetch(url: String): Fetch {
        lastFailureReason = null

        val uri = runCatching { URI(url) }.getOrNull()
            ?: return unreachable("地址不合法：$url")
        val host = uri.host ?: return unreachable("地址里取不到 IP / 主机名：$url")
        val port = if (uri.port > 0) uri.port else 80
        val path = uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/"

        val sock = Socket()
        try {
            sock.soTimeout = 20000
            sock.connect(InetSocketAddress(host, port), PROBE_TIMEOUT_MS)

            val req = buildString {
                append("GET $path HTTP/1.1\r\n")
                append("Host: $host:$port\r\n")
                append("User-Agent: CommissionBook\r\n")
                append("Accept: */*\r\n")
                append("Connection: close\r\n")   // 对面发完就关，我们靠它知道读完了
                append("\r\n")
            }
            val out = sock.getOutputStream()
            out.write(req.toByteArray(Charsets.ISO_8859_1))
            out.flush()

            // 对面 `Connection: close`，所以 readBytes 会一直读到连接关闭为止
            val raw = sock.getInputStream().readBytes()
            val text = String(raw, Charsets.UTF_8)
            val sep = text.indexOf("\r\n\r\n")
            val head = if (sep >= 0) text.substring(0, sep) else text
            val body = if (sep >= 0) text.substring(sep + 4) else ""

            // 状态行：HTTP/1.1 200 OK
            val code = head.lineSequence().firstOrNull()
                ?.split(' ')?.getOrNull(1)?.toIntOrNull() ?: 0

            return when {
                code == 410 -> Fetch.Used
                code == 403 -> Fetch.Stale
                code != 200 -> Fetch.Broken.also { lastFailureReason = "对面回的是 HTTP $code" }
                body.isBlank() -> Fetch.Broken.also { lastFailureReason = "读回来是空的" }
                else -> Fetch.Ok(body)
            }
        } catch (e: Exception) {
            return unreachable("${e.javaClass.simpleName}：${e.message.orEmpty()}（连 $host:$port）")
        } finally {
            runCatching { sock.close() }
        }
    }

    /** 记一笔「为什么连不上」，界面会把这句原话带给用户（排查用，见 [lastFailureReason]） */
    private fun unreachable(reason: String): Fetch {
        lastFailureReason = reason
        return Fetch.Unreachable
    }

    /**
     * 造二维码位图。口令一代一换，这张图也就一代一换（见 [Session.url]）。
     *
     * ⚠️ 纠错级别挑 **L**（最低）：级别越高越好看，但能装的数据越少 ——
     *    码里就一条短地址，L 级完全够，而且**码面格子更大、更好扫**。
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

    /** 一次传输会话（旧手机那一端）。口令轮换、一次性、到点自毁，都在这个对象身上。 */
    class Session internal constructor(
        private val host: String,
        private val port: Int,
        val expiresAt: Long,
        private val onUsed: () -> Unit,
        private val onExpired: () -> Unit,
    ) {
        /**
         * 这张码里用的口令。**整场 5 分钟就这一个，不再变** ——
         * 原因见文件顶上那段 ⚠️（逸风要「五分钟之后才变」，不再每 10 秒轮换）。
         */
        private val token: String = newToken()

        @Volatile
        var used: Boolean = false
            private set

        @Volatile
        private var stopped = false

        private var reaper: Thread? = null

        /** 二维码里该放的地址。整场不变 —— 界面不用为它重画。 */
        val url: String get() = "http://$host:$port/cb/$token"

        val expired: Boolean get() = System.currentTimeMillis() >= expiresAt

        /** 还能用多久（毫秒，不小于 0） */
        val remainingMs: Long get() = (expiresAt - System.currentTimeMillis()).coerceAtLeast(0L)

        internal fun matches(tok: String): Boolean = tok == token

        internal fun markUsed() {
            if (used || stopped) return
            used = true
            runCatching { onUsed() }
        }

        internal fun beginTimers() {
            reaper = Thread {
                while (!stopped && !expired) sleepQuietly(500)
                if (!stopped) {
                    stop()
                    runCatching { onExpired() }
                }
            }.also { it.isDaemon = true; it.start() }
        }

        fun stop() {
            stopped = true
            runCatching { reaper?.interrupt() }
            runCatching { LanServer.close(port) }
        }
    }
}

private fun newToken(): String =
    UUID.randomUUID().toString().replace("-", "").take(16)

private fun sleepQuietly(ms: Long) {
    runCatching { Thread.sleep(ms) }
}

/**
 * 旧手机那边临时起的小服务。
 *
 * 自己拿 [ServerSocket] 拼 HTTP，不引框架 —— 我们要发的就一个 GET、一个响应体，
 * 用不上路由、静态文件那些东西。
 *
 * 状态码约定（新手机那边按它出不同的话）：
 *   200 = 给你，而且这一次之后这场就作废了
 *   403 = 口令不对（多半是张截屏，或者对面已经换到下一代了）
 *   410 = 这场已经用过了
 */
private object LanServer {

    private var server: ServerSocket? = null

    fun start(
        text: String,
        ttlMs: Long,
        onUsed: () -> Unit,
        onExpired: () -> Unit,
    ): QrTransfer.Session? {
        closeAll()
        val ip = localIp() ?: return null
        val ss = runCatching { ServerSocket(0) }.getOrNull() ?: return null

        val session = QrTransfer.Session(
            host = ip,
            port = ss.localPort,
            expiresAt = System.currentTimeMillis() + ttlMs,
            onUsed = onUsed,
            onExpired = onExpired,
        )
        server = ss
        session.beginTimers()

        Thread {
            val body = text.toByteArray()
            val deadline = session.expiresAt
            while (!ss.isClosed && System.currentTimeMillis() < deadline) {
                runCatching { ss.soTimeout = 1000 }
                val sock = runCatching { ss.accept() }.getOrNull() ?: continue
                runCatching { respond(sock, body, session) }
            }
            runCatching { ss.close() }
        }.also { it.isDaemon = true; it.start() }

        return session
    }

    fun close(port: Int) {
        runCatching { server?.close() }
        server = null
    }

    private fun closeAll() {
        runCatching { server?.close() }
        server = null
    }

    /**
     * 就一回：读掉请求头（只挑出请求行看口令），然后把整份备份写回去。
     */
    private fun respond(sock: Socket, body: ByteArray, session: QrTransfer.Session) = sock.use { s ->
        s.soTimeout = 10000
        val input = s.getInputStream()

        // 请求行形如：GET /cb/<口令> HTTP/1.1 —— 只认这一行，别的不看
        val head = StringBuilder()
        val buf = ByteArray(512)
        var guard = 0
        while (guard++ < 64 && head.length < 4096) {
            val n = runCatching { input.read(buf) }.getOrDefault(-1)
            if (n <= 0) break
            head.append(String(buf, 0, n, Charsets.ISO_8859_1))
            if (head.contains("\r\n\r\n")) break
        }
        val requestLine = head.toString().substringBefore("\r\n")
        val path = requestLine.split(' ').getOrNull(1).orEmpty()
        val token = path.substringAfterLast('/').substringBefore('?')

        val status: Int
        val payload: ByteArray
        when {
            session.used -> {
                status = 410
                payload = ByteArray(0)
            }
            !session.matches(token) -> {
                // 口令对不上：截屏截到的是旧码，或者对面压根不是这一场
                status = 403
                payload = ByteArray(0)
            }
            else -> {
                status = 200
                payload = body
                // 只有真发出去（200）才作废 —— 被 403/410 挡掉的不算
                session.markUsed()
            }
        }

        val reason = when (status) {
            200 -> "OK"
            403 -> "Forbidden"
            else -> "Gone"
        }
        val out = s.getOutputStream()
        val header = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${payload.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        out.write(header.toByteArray(Charsets.ISO_8859_1))
        if (payload.isNotEmpty()) out.write(payload)
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
 * 旧手机那张出码 Sheet（**弹出来，不占满屏**）。
 *
 * 两件事都要在界面上说出来，用户才知道这码该怎么用、什么时候会失效：
 *   · 倒计时（5 分钟）
 *   · 「只能扫一次」
 *
 * ⚠️ [QrTransfer.start] 有副作用（开端口），只能放 `LaunchedEffect` 里跑一次。
 *    放进 `remember` 都会在重组里重跑，端口一遍遍开了又关，对面永远连不上。
 *
 * ⚠️ 那条 1 秒的 ticker **留着** —— 不是为了换码（现在整场就一个码，不再轮换），
 *    是为了让**倒计时那行字**每秒重画一次。
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun QrShowSheet(
    backupText: String,
    onClose: () -> Unit,
) {
    var session by remember { mutableStateOf<QrTransfer.Session?>(null) }
    var failed by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(backupText) {
        val s = QrTransfer.start(
            backupText = backupText,
            onUsed = { },
            onExpired = { },
        )
        if (s == null) failed = true else session = s
    }

    // 1 秒一跳：走倒计时（顺便让「该换码了」这件事被重组看到）
    LaunchedEffect(session) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    val s = session
    val expired = s != null && now >= s.expiresAt
    val used = s != null && s.used

    // 到点就把服务收掉；关 Sheet 时也收（DisposableEffect 不写了，这里一并管）
    LaunchedEffect(expired) { if (expired) s?.stop() }

    val url = if (s != null && !expired && !used) s.url else null
    val bmp = remember(url) { url?.let { QrTransfer.qrBitmap(it) } }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = {
            QrTransfer.stop()
            onClose()
        },
        // ⚠️ 逸风 2026-10-08：这张 Sheet 要**弹全屏**。
        //    理由很实在 —— 码越大越好扫；小半屏再加底下那行字，扫的人得凑很近，
        //    反而更容易手抖扫不上。
        //    `skipPartiallyExpanded` 必须开：不开的话它先停在半屏，还得手动往上拖。
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            // 铺满全屏之后内容**竖着居中**，别都堆在顶上
            verticalArrangement = Arrangement.Center,
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

                used -> Text(
                    text = AppCtx.s(R.string.qr_show_used),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )

                expired -> Text(
                    text = AppCtx.s(R.string.qr_show_expired),
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
                            .size(300.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(androidx.compose.ui.graphics.Color.White)
                            .padding(6.dp),
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = AppCtx.s(
                            R.string.qr_show_lan_hint,
                            clockOf(s?.remainingMs ?: 0L),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        // ⚠️ 逸风 2026-10-08：这行**不要红字**（原话「下面的文字可以不用红色吗」）。
                        //    它其实不是报错，是一句使用说明（要同一个 Wi-Fi + 还剩多久），
                        //    用报错的红色反而吓人。改成跟正文一致的次要色。
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }
            }
        }
    }
}

/** 还剩多久，写成 `4:32` —— 秒数补两位，别让那行字一会儿宽一会儿窄 */
private fun clockOf(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "$m:${s.toString().padStart(2, '0')}"
}
