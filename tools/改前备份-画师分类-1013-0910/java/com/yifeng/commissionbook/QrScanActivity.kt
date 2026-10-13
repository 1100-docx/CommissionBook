package com.yifeng.commissionbook

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Camera
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView

/*
 * 扫码取景页（2026-10-08 加）。
 *
 * 逸风 2026-10-08 发了一张 兽频道「扫一扫」的截图，说「扫码页面参考这个」。照它做的：
 *
 *   ┌─────────────────────────┐
 *   │ (←)                 (⟲) │   ← 左上白圆=返回，右上白圆=翻转镜头
 *   │                         │
 *   │        （相机取景）      │   ← 全黑，不画取景框
 *   │                         │
 *   │         🔦              │
 *   │      轻触照亮            │   ← 手电筒，点一下开/关
 *   │                         │
 *   │  ╭───────────────────╮  │
 *   │  │ [▣]  扫对面那台手机的 │ │  ← 深色胶囊栏：左=二维码、中=两行说明、右=相册
 *   │  │      二维码        [▢]│ │
 *   │  ╰───────────────────╯  │
 *   └─────────────────────────┘
 *
 * ⚠️ 相机预览、自动对焦、解码、连续扫描这些**不用我们写**：zxing-android-embedded 的
 *    [CaptureActivity] 全包了，我们只是**换掉它的内容**（覆盖 `initializeContent()`），
 *    再把上面那层 UI 接到它自己的方法上。所以别去引 CameraX —— 白重写一遍。
 *
 * ⚠️ 两个真机踩出来的坑（2026-10-08 编译时才发现，记在这儿省得下次再摸一遍）：
 *   ① 这个版本（4.3.0）**没有 `getLayoutId()`** —— 换布局要覆盖
 *      `initializeContent()`，在里头 `setContentView` 再把那个 DecoratedBarcodeView 交回去。
 *   ② **也没有 `switchCamera()`** —— 翻镜头得自己来：先 `pause()`，
 *      改 `CameraSettings.requestedCameraId`，再 `resume()`。
 *      手电筒倒是有现成的 `setTorchOn()` / `setTorchOff()`。
 *
 * ⚠️ 兽频道中间那个胶囊是「拍照给 AI」（它是个 AI App）。我们这里换成**两台手机怎么连**
 *    的提示 —— 这才是这个功能里用户真会踩的坑。
 */

class QrScanActivity : CaptureActivity() {

    /** 相册那张图正在处理 —— 防连点 */
    private var pickingGallery = false

    /**
     * 换掉 zxing 默认那套界面，用我们自己画的（见 res/layout/activity_qr_scan.xml）。
     *
     * ⚠️ 必须把 id 为 `zxing_barcode_scanner` 的那个 DecoratedBarcodeView 交回去 ——
     *    CaptureActivity 拿到它才会去开相机、开始解码。交错了就是一进去黑屏。
     */
    override fun initializeContent(): DecoratedBarcodeView {
        setContentView(R.layout.activity_qr_scan)
        return findViewById(R.id.zxing_barcode_scanner)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 面包屑（2026-10-09 加）：万一这台机器上又出岔子，
        // 崩溃记录里「最后停在」会写「二维码 · 扫码页」——
        // 一眼就能分出是卡在扫码这一步，还是卡在后头联网那步（3.9.3 那次就是后者）。
        runCatching { CrashLog.breadcrumb(this, "二维码 · 扫码页") }

        val scanner = findViewById<DecoratedBarcodeView>(R.id.zxing_barcode_scanner)

        // 默认那层「对准二维码」的提示条、还有那圈白色取景框，都不要 —— 参考图是干净的
        runCatching { scanner?.setStatusText("") }
        runCatching { scanner?.viewFinder?.visibility = View.GONE }
        runCatching { scanner?.statusView?.visibility = View.GONE }

        findViewById<View>(R.id.qr_back).setOnClickListener { finish() }

        findViewById<View>(R.id.qr_flip).setOnClickListener { flipCamera(scanner) }

        val torchText = findViewById<TextView>(R.id.qr_torch_text)
        var lit = false
        findViewById<View>(R.id.qr_torch_wrap).setOnClickListener {
            lit = !lit
            // ⚠️ 开/关失败（有些机器没闪光灯）要把状态改回去，
            //    不然按钮上的字跟事实对不上，用户会以为灯坏了
            val ok = runCatching {
                if (lit) scanner?.setTorchOn() else scanner?.setTorchOff()
                true
            }.getOrDefault(false)
            if (!ok) lit = false
            torchText.text = AppCtx.s(if (lit) R.string.qr_scan_torch_off else R.string.qr_scan_torch)
        }

        findViewById<View>(R.id.qr_gallery).setOnClickListener { pickFromGallery() }
    }

    /**
     * 翻到下一个摄像头（前置/后置）。
     *
     * ⚠️ 这个版本的库里没有现成的翻转方法，得自己按这套来：
     *    **先 pause、改设置、再 resume** —— 顺序反了相机会开着就用旧 id 起一遍，
     *    表现是「按了没反应」或者直接黑掉。
     */
    private fun flipCamera(scanner: DecoratedBarcodeView?) {
        if (scanner == null) return
        val count = runCatching { Camera.getNumberOfCameras() }.getOrDefault(0)
        if (count < 2) {
            toast(AppCtx.s(R.string.qr_scan_flip_none))
            return
        }
        runCatching {
            val settings = scanner.cameraSettings
            val next = (settings.requestedCameraId + 1).mod(count)
            scanner.pause()
            settings.requestedCameraId = next
            scanner.resume()
        }.onFailure { toast(AppCtx.s(R.string.qr_scan_flip_none)) }
    }

    /**
     * 从相册挑一张图来解二维码（有时候对方发过来的是一张截图）。
     *
     * ⚠️ 走系统图片选择器，**不要相册权限** —— 跟 App 里别处选图一个路子。
     */
    private fun pickFromGallery() {
        if (pickingGallery) return
        pickingGallery = true
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        runCatching { startActivityForResult(intent, REQ_GALLERY) }.onFailure {
            pickingGallery = false
            toast(AppCtx.s(R.string.qr_scan_gallery_fail))
        }
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_GALLERY) return
        pickingGallery = false
        val uri: Uri = data?.data ?: return
        val text = decodeFromImage(uri)
        if (text == null) {
            toast(AppCtx.s(R.string.qr_scan_gallery_fail))
            return
        }
        deliver(text)
    }

    /** 把一张图片里的二维码解出来。解不出返回 null。 */
    private fun decodeFromImage(uri: Uri): String? = runCatching {
        val bmp: Bitmap = contentResolver.openInputStream(uri)?.use { input ->
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            BitmapFactory.decodeStream(input, null, opts)
        } ?: return null

        val w = bmp.width
        val h = bmp.height
        if (w <= 0 || h <= 0) return null
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)

        val source = RGBLuminanceSource(w, h, pixels)
        MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source))).text
    }.getOrNull()

    /**
     * 把扫到的内容交回去。
     *
     * ⚠️ `"SCAN_RESULT"` 这个键名是 zxing 的约定，宿主（MainActivity 的 scanQr）就按
     *    这个名字取值 —— 这里故意写死字面量，免得为引一个常量把库的类名绑死。
     */
    private fun deliver(text: String) {
        setResult(RESULT_OK, Intent().putExtra("SCAN_RESULT", text))
        finish()
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private companion object {
        const val REQ_GALLERY = 1001
    }
}
