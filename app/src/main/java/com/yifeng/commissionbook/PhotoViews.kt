package com.yifeng.commissionbook

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 参考图那几块界面（2026-10-03 加）。
 *
 * 三样东西：
 *  - [PhotoThumb]  一张缩略小方块（解码是**按需缩**的，不直接把几 MB 的原图塞进内存）
 *  - [PhotoStrip]  表单里那一排「缩略图 + 加图钮」
 *  - [PhotoViewer] 点开看大图（整屏、左右翻，跟系统相册一个手感）
 *
 * ⚠️ 缩略图**每次重组都重解一遍**是有意为之：一条单最多 6 张、每张解一次几毫秒，
 *    比在这儿维护一个位图缓存简单得多，也不会因为缓存没清干净把内存吃满。
 */

@Composable
fun PhotoThumb(path: String?, size: Dp, modifier: Modifier = Modifier) {
    val bmp = remember(path) { path?.let { Photos.thumbnail(it, 320) } }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // 图没了（换手机恢复了一份「只带名字」的备份，或者文件被清掉了）——
            // 画个占位，别显示成一块白，让人以为是自己点错了
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.ImageNotSupported,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    AppCtx.s(R.string.photos_missing),
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp),
                )
            }
        }
    }
}

/**
 * 表单里那一排：已有的缩略图（右上角 ✕ 能删、点开看大图）+ 一颗「加图」。
 * 到 [Photos.MAX_COUNT] 张就不显示「加图」了（比点了再弹「满了」客气）。
 */
@Composable
fun PhotoStrip(
    items: List<Photo>,
    pathFor: (String) -> String,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onOpen: (Int) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        // 有说明的那张会高一点，所以顶部对齐（不然缩略图会互相错位）
        verticalAlignment = Alignment.Top,
    ) {
        items.forEachIndexed { i, item ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(78.dp)) {
                    PhotoThumb(
                        path = pathFor(item.name),
                        size = 78.dp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(cs.surfaceVariant.copy(alpha = 0.5f))
                            .border(1.dp, cs.outlineVariant, RoundedCornerShape(12.dp))
                            .clickable { onOpen(i) },
                    )
                    // ✕：半透明黑底小白叉，压在右上角
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f))
                            .clickable { onRemove(item.name) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = AppCtx.s(R.string.photos_remove),
                            tint = Color.White,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }

            }
        }
        if (items.size < Photos.MAX_COUNT) {
            Column(
                Modifier
                    .size(78.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(cs.primary.copy(alpha = 0.07f))
                    .border(1.dp, cs.primary.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .clickable { onAdd() },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Outlined.AddPhotoAlternate,
                    contentDescription = AppCtx.s(R.string.photos_add),
                    tint = cs.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    AppCtx.s(R.string.photos_add),
                    fontSize = 10.sp,
                    color = cs.primary,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

/**
 * 整屏看大图：左右翻页 + 底下「2 / 5」+ 那颗「加说明 / 改说明」+ 左上角 ✕
 *
 * 说明就在这儿写（2026-10-04 加）—— 缩略图太小放不下输入框，
 * 而且写说明的时候眼睛正看着这张图，放这儿最顺。跟 iOS 端同一套交互。
 */
@Composable
fun PhotoViewer(
    items: List<Photo>,
    startIndex: Int,
    pathFor: (String) -> String,
    onDismiss: () -> Unit,
    /**
     * 这一单的水印（2026-10-07 加）。
     * null = 不加印。**看图时盖一层、分享时烧进去，两处用同一套画法**（见 [Watermark]）。
     */
    watermark: WatermarkStyle? = null,
) {
    if (items.isEmpty()) return
    val pager = rememberPagerState(
        initialPage = startIndex.coerceIn(0, items.size - 1),
        pageCount = { items.size },
    )

    // ⚠️ `decorFitsSystemWindows = false` 是 2026-10-05 为「加说明跟三大金刚键重合」加的：
    //    这是个 Dialog（**另一个窗口**）。默认那个窗口自己 fit 系统栏，于是窗口里读到的
    //    `WindowInsets.navigationBars` 是 0 —— 底下加多少 padding 都没用（逸风报「问题依旧」
    //    就是这个原因，改 padding 改不动）。
    //    关掉这个 fit，窗口真·边到边，insets 才会如实报进来，
    //    下面那行 `windowInsetsPadding(WindowInsets.navigationBars)` 才生效。
    //    ⚠️ 代价：顶上那颗 ✕ 也会跟着钻到状态栏底下，所以它那边补了 statusBars（见下）。
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val bmp = remember(items[page].name) { Photos.thumbnail(pathFor(items[page].name), 2200) }
                    if (bmp != null) {
                        // ⚠️ 水印得**正好盖在照片上**，所以不能直接铺满这一整块黑底 ——
                        //    先按 Fit 算出照片真实的显示尺寸，再在这个尺寸上盖一层。
                        //    （铺满整块的话，竖图和横图上水印的疏密会跟导出那张对不上。）
                        BoxWithConstraints(
                            Modifier.fillMaxSize().padding(8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            val density = LocalDensity.current
                            val availW = with(density) { maxWidth.toPx() }
                            val availH = with(density) { maxHeight.toPx() }
                            val scale = minOf(availW / bmp.width, availH / bmp.height)
                            val dw = (bmp.width * scale).toInt().coerceAtLeast(1)
                            val dh = (bmp.height * scale).toInt().coerceAtLeast(1)

                            Box(
                                Modifier.size(
                                    with(density) { dw.toDp() },
                                    with(density) { dh.toDp() },
                                )
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                if (watermark != null) {
                                    // 水印层**固定按 1000 宽**渲一张（省内存），再拉伸铺满这块 ——
                                    // 排布是按「短边的比例」算的，缩放之后跟原图那张一模一样，
                                    // 所以这块小屏预览跟分享出去那张是同一个密度。
                                    val layer = remember(items[page].name, watermark, dw, dh) {
                                        Watermark.overlay(
                                            w = 1000,
                                            h = (1000f * dh / dw).toInt().coerceAtLeast(1),
                                            style = watermark,
                                        )
                                    }
                                    if (layer != null) {
                                        Image(
                                            bitmap = layer.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            AppCtx.s(R.string.photos_missing),
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                        )
                    }
                }
            }
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    // 窗口边到边之后顶栏不再自动让位，这颗 ✕ 得自己躲状态栏 ——
                    // 跟底部那摞躲导航栏是一对，别只修一边。
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(12.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = AppCtx.s(R.string.common_cancel),
                    tint = Color.White,
                    modifier = Modifier.size(19.dp),
                )
            }

            // 底部那一摞：说明（有才显示）→ 页码 → 「加说明 / 改说明」
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // ⚠️ 必须让开系统导航栏 —— 安卓 15 起内容被强制画到系统栏底下（边到边），
                    //    系统自带的底栏会自己躲，这一摞是自己搭的，不躲就顶到三大金刚键上。
                    //    逸风 2026-10-05 真机报「图片备注按钮太靠下，会跟三大金刚键重合」——
                    //    就是漏了这一行（1.7 那次主底栏栽过同一个坑，见 MainActivity 的注释）。
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (items.size > 1) {
                        Text(
                            "${pager.currentPage + 1} / ${items.size}",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.14f))
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                        )
                    }

                    // 分享这张图（2026-10-07 加）。
                    // ⚠️ 水印一开，**发出去的就是带水印那张** —— 这才是水印的用处所在：
                    //    不然图根本出不去，水印只能在这台手机上自己看。
                    val ctx = LocalContext.current
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.14f))
                            .clickable {
                                val photo = items[pager.currentPage]
                                // ⚠️ 分享要用**大图**：屏幕上那张是 2200 的缩略图，
                                //    发出去得重新取一张（上限 4096），再往上烧水印。
                                val full = Photos.thumbnail(pathFor(photo.name), 4096)
                                if (full == null) {
                                    toastNow(ctx, AppCtx.s(R.string.photos_missing))
                                } else {
                                    val out = watermark?.let { Watermark.stamp(full, it) } ?: full
                                    val name = photo.name.substringBeforeLast('.') + ".png"
                                    val intent = sharePng(ctx, out, name)
                                    if (intent == null) {
                                        toastNow(ctx, AppCtx.s(R.string.common_share_failed_2))
                                    } else {
                                        ctx.startActivity(
                                            Intent.createChooser(intent, AppCtx.s(R.string.wm_share_photo))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Share,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            AppCtx.s(R.string.wm_share_photo),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                }
            }
        }
    }

}

/** 卡片上那枚小角标：一张图 + 张数。没图就什么都不画（布局一点不动） */
@Composable
fun PhotoBadge(count: Int) {
    if (count <= 0) return
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.Collections,
            contentDescription = AppCtx.s(R.string.photos_count_badge, count),
            tint = cs.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(3.dp))
        Text("$count", fontSize = 12.sp, color = cs.onSurfaceVariant)
    }
}

/** 表单里那段小灰字说明（「选图走系统选择器，不用相册权限」那句） */
@Composable
fun PhotoHint(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * 水印小样（2026-10-07 加）：一块灰底当"照片"，上面盖一层真水印。
 *
 * ⚠️ 用的是 **[Watermark.overlay]** —— 跟看图页、分享出去那张**同一个函数**。
 *    所以这块小样长什么样，发出去的就长什么样（别为了"好看"在这儿另画一套）。
 * 底色调成中性灰是有意的：白色水印、黑色水印在这块底上都看得出来。
 */
@Composable
fun WmPreviewCard(text: String, colorArgb: Int, percent: Int) {
    val style = Watermark.styleOf(text, colorArgb, percent)
    val density = LocalDensity.current
    val wPx = with(density) { 320.dp.toPx() }.toInt().coerceAtLeast(1)
    val hPx = with(density) { 130.dp.toPx() }.toInt().coerceAtLeast(1)
    val layer = remember(wPx, hPx, style) { Watermark.overlay(wPx, hPx, style) }

    Box(
        Modifier
            .fillMaxWidth()
            .height(130.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF8A93A0))
    ) {
        if (layer != null) {
            Image(
                bitmap = layer.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
