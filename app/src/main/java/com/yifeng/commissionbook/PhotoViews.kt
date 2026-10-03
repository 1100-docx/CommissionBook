package com.yifeng.commissionbook

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    names: List<String>,
    pathFor: (String) -> String,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onOpen: (Int) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        names.forEachIndexed { i, name ->
            Box(Modifier.size(78.dp)) {
                PhotoThumb(
                    path = pathFor(name),
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
                        .clickable { onRemove(name) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = AppCtx.s(R.string.photos_remove),
                        tint = Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
        if (names.size < Photos.MAX_COUNT) {
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

/** 整屏看大图：左右翻页 + 右下角「2 / 5」+ 左上角 ✕ */
@Composable
fun PhotoViewer(
    names: List<String>,
    startIndex: Int,
    pathFor: (String) -> String,
    onDismiss: () -> Unit,
) {
    if (names.isEmpty()) return
    val pager = rememberPagerState(
        initialPage = startIndex.coerceIn(0, names.size - 1),
        pageCount = { names.size },
    )
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val bmp = remember(names[page]) { Photos.thumbnail(pathFor(names[page]), 2200) }
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                        )
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
                    .padding(12.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = AppCtx.s(R.string.common_cancel),
                    tint = Color.White,
                    modifier = Modifier.size(19.dp),
                )
            }
            if (names.size > 1) {
                Text(
                    "${pager.currentPage + 1} / ${names.size}",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 26.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.14f))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                )
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
            Icons.Filled.Collections,
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
