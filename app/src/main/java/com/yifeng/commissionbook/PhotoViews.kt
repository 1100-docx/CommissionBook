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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.launch

/**
 * 参考图那几块界面（2026-10-03 加）。
 *
 * 三样东西：
 *  - [PhotoThumb]  一张缩略小方块（解码是**按需缩**的，不直接把几 MB 的原图塞进内存）
 *  - [PhotoStrip]  表单里那一排「缩略图 + 加图钮」
 *  - [PhotoViewer] 点缩略图弹 Sheet 看大图（左右翻，顶栏 ✕ 在左 / ✓ 在右）
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
 * 看大图（2026-10-07 改成 Sheet）。
 *
 * 点缩略图 → 底下弹起这张 Sheet：里头是大图、能左右翻，顶栏 ✕ 在左、页码居中、✓ 在右。
 * 跟 iOS 那边同一套（iOS 走系统 sheet + 原生 toolbar 两颗按钮）。
 *
 * ⚠️ 以前是个**全屏 Dialog**，顶上那颗 ✕、底下页码、还有那颗分享都得自己搭，
 *    结果按钮一次次顶到系统导航栏上（逸风连报两次）。换成 ModalBottomSheet 之后
 *    底部让位、圆角、动画全归系统管，自己不再碰 inset。
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // 关的时候先让 sheet 自己滑下去、动画走完再摘掉 —— 直接 onDismiss() 是「啪」一下硬切。
    val close: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    // ⚠️ 2026-10-07 大改：原来是「全屏 Dialog + 自己搭顶栏」，逸风真机报
    //    「分享按钮又跟三大金刚键重合了」。他的提法：**点缩略图弹 Sheet，里面是大图，
    //    顶上 ✕ 在左、对号在右**。改成 ModalBottomSheet 白捡三样：
    //      ① 底部让位（`contentWindowInsets`）由 sheet 自己算，三大金刚键那档事不用管了；
    //      ② 顶上是系统那套圆角 + 把手，天然原生；
    //      ③ 不用再折腾 `decorFitsSystemWindows` / Dialog 窗口读不到 insets 那些坑。
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Color.Black,
        contentColor = Color.White,
    ) {
        // ⚠️ 高度写死：不写的话 sheet 会「图片多高它就多高」，左右翻页时上下乱跳。
        val screenH = LocalConfiguration.current.screenHeightDp.dp
        Column(Modifier.fillMaxWidth().height(screenH * 0.86f)) {

            // 顶栏：左上角一颗 ✕、页码居中。
            // ⚠️ 2026-10-07：本来左右各一颗（✕ 在左、✓ 在右），逸风问「作用一样吗，一样就删掉一个」——
            //    确实都是 close，所以只留左边这颗 ✕。
            //    右边那个 `Spacer(size = 48.dp)` 是**占位**，跟左边那颗 IconButton 对称，页码才真居中；
            //    想换成只留右边那颗对号：把 ✕ 那颗删了、占位挪到左边、图标换 `Icons.Outlined.Check`。
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = close) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = AppCtx.s(R.string.common_cancel),
                        tint = Color.White,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (items.size > 1) {
                    Text(
                        "${pager.currentPage + 1} / ${items.size}",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(48.dp))
            }

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
