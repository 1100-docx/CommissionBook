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
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ImageNotSupported
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
                            Icons.Filled.Close,
                            contentDescription = AppCtx.s(R.string.photos_remove),
                            tint = Color.White,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
                // 这张图的说明（没写就不占位）—— 说明怎么加：点开大图，底下那颗按钮
                if (item.caption.isNotBlank()) {
                    Text(
                        item.caption,
                        fontSize = 10.sp,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(78.dp).padding(top = 3.dp),
                    )
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
    onSetCaption: (Int, String) -> Unit,
) {
    if (items.isEmpty()) return
    val pager = rememberPagerState(
        initialPage = startIndex.coerceIn(0, items.size - 1),
        pageCount = { items.size },
    )
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val current = items.getOrNull(pager.currentPage)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val bmp = remember(items[page].name) { Photos.thumbnail(pathFor(items[page].name), 2200) }
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

            // 底部那一摞：说明（有才显示）→ 页码 → 「加说明 / 改说明」
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!current?.caption.isNullOrBlank()) {
                    Text(
                        current.caption,
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.16f))
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                }
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
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.22f))
                            .clickable {
                                draft = current?.caption.orEmpty()
                                editing = true
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            AppCtx.s(
                                if (current?.caption.isNullOrBlank()) R.string.photos_note_add
                                else R.string.photos_note_edit
                            ),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }

    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(AppCtx.s(R.string.photos_note_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        placeholder = { Text(AppCtx.s(R.string.photos_note_placeholder), fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        AppCtx.s(R.string.photos_note_desc),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSetCaption(pager.currentPage, draft.trim())
                    editing = false
                }) { Text(AppCtx.s(R.string.common_save)) }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) { Text(AppCtx.s(R.string.common_cancel)) }
            },
        )
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
