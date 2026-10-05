package com.yifeng.commissionbook

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.Build
import android.widget.Toast

// MARK: - 反馈与建议（2026-09-29 加）
//
// 这个 App 不申请联网权限，所以「反馈」不可能是「点一下传出去」——
// 只能是：把邮箱和一段写好的内容递到你手上，你自己用邮件 App 发（或复制到微信 / QQ 都行）。
//
// 为什么要做：逸风说「给个反馈渠道」。他邮箱 error1100@icloud.com。

/** 收反馈的邮箱 —— 以后要换只改这一行 */
const val FEEDBACK_EMAIL = "error1100@icloud.com"

/** 邮件主题：带上版本号，收到的人一眼知道是哪个包 */
fun feedbackSubject(version: String) = AppCtx.s(R.string.help_feedback_subject, version)

/** 邮件正文：先替他把「机型 / 系统 / 版本」填好，省得来回问 */
fun feedbackBody(version: String) = """
说一下我想说的：
（哪儿不对劲、想要什么功能、想夸想骂都行）


——————————
下面这几行是自动填的，不想发可以删掉：
版本：约稿账本 安卓 $version
机型：${Build.MANUFACTURER} ${Build.MODEL}
系统：安卓 ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）
""".trimIndent()

@Composable
fun FeedbackScreen(onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // 系统返回键也走同一条路（不然会直接退出 App）
    BackHandler { onBack() }

    val versionName = remember {
        runCatching {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(context.packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            info.versionName ?: "?"
        }.getOrDefault("?")
    }

    fun copyEmail() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(AppCtx.s(R.string.help_feedback_email), FEEDBACK_EMAIL))
        Toast.makeText(context, AppCtx.s(R.string.help_email_copied), Toast.LENGTH_LONG).show()
    }

    fun openMail() {
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
            .putExtra(Intent.EXTRA_SUBJECT, feedbackSubject(versionName))
            .putExtra(Intent.EXTRA_TEXT, feedbackBody(versionName))
        try {
            context.startActivity(i)
        } catch (e: ActivityNotFoundException) {
            // 手机上没装邮件 App —— 别让人卡在这儿，兜底给复制
            copyEmail()
        }
    }

    ScreenSurface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {

            // 顶栏：返回 + 标题
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIconButton(Icons.AutoMirrored.Outlined.ArrowBack, AppCtx.s(R.string.common_back)) {
                    lightTick(haptic)
                    onBack()
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(AppCtx.s(R.string.help_feedback_title), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
                    Text(
                        AppCtx.s(R.string.help_feedback_hint),
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                // ① 邮箱 + 复制
                SoftCard(Modifier.fillMaxWidth()) {
                    Text(AppCtx.s(R.string.help_send_to_email), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = cs.primary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        FEEDBACK_EMAIL,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface,
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { lightTick(haptic); copyEmail() }) {
                        Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp), tint = cs.primary)
                        Spacer(Modifier.width(6.dp))
                        Text(AppCtx.s(R.string.common_copy_email), fontSize = 14.sp)
                    }
                }

                Spacer(Modifier.height(14.dp))

                // ② 为什么不能「点一下就发」
                SoftCard(Modifier.fillMaxWidth()) {
                    Text(AppCtx.s(R.string.help_manual_send_why), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = cs.primary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        AppCtx.s(R.string.help_no_network_permission) +
                            AppCtx.s(R.string.help_no_one_tap_report) +
                            AppCtx.s(R.string.help_mail_app_prefilled) +
                            AppCtx.s(R.string.help_delete_if_unwanted),
                        fontSize = 13.sp,
                        lineHeight = 21.sp,
                        color = cs.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(14.dp))

                // ③ 其他路子
                SoftCard(Modifier.fillMaxWidth()) {
                    Text(AppCtx.s(R.string.help_other_ways), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = cs.primary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        AppCtx.s(R.string.help_copy_email_wechat) +
                            AppCtx.s(R.string.help_github_issue),
                        fontSize = 13.sp,
                        lineHeight = 21.sp,
                        color = cs.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(10.dp))
            }

            // ③ 底部固定：写邮件
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(cs.surface.copy(alpha = 0.97f))
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Button(
                    onClick = { lightTick(haptic); openMail() },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                ) {
                    Text(AppCtx.s(R.string.help_send_feedback_email), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    AppCtx.s(R.string.help_opens_mail_app),
                    fontSize = 11.5.sp,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
