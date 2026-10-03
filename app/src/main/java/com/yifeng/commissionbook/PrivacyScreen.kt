package com.yifeng.commissionbook

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// MARK: - 隐私政策（内嵌版）
//
// 2026-09-29 加。逸风要把隐私政策内嵌进 App，两处用到：
//   ① **首次安装启动**：必须读完、划到最底下，才点得动「已阅读并同意」→ 才进得去
//   ② 设置页 →「隐私政策」：随时能翻回来看（离线文本，一个字都不联网）
//
// 内容必须和 App 的真实行为一条条对得上，写得越硬越有底气。
// ⚠️ 2026-10-03 改口：这一版加了「检查更新」，App 有了 INTERNET 权限。
//    以前那句「连 INTERNET 权限都没申请，「不联网」是系统层面根本做不到」
//    **不能再写了** —— 那是假话。现在的写法是「只在一个地方联网，且不上传任何数据」，
//    并把这唯一一件事、用的什么权限、在哪儿能关掉，全列在第一条里。
//    改动记录：政策版本号 POLICY_VERSION 也跟着改了，老用户下次启动会重读一遍。

/**
 * 这一版政策的版本号。
 *
 * 存进 [Prefs.privacyAgreedVersion]：**以后政策改了就改这个字符串**，
 * App 下次启动会自动再弹一次同意页（改了什么也没瞒着人）。
 */
const val POLICY_VERSION = "2026-10-03.3"

/** 政策正文：一段标题 + 若干段正文 */
private data class PolicySection(val heading: String, val body: List<String>)

/** 权限表的一行 */
private data class PolicyPermission(val name: String, val why: String, val refuse: String)

// ⚠️ 2026-10-03 两个错都在这儿，别改回去：
//   ① 一度是 `const val` —— 多语言改造后要取 `AppCtx.s(R.string.…)`，
//      那是函数调用，`const` 要求编译期常量，留着直接编译不过，所以降成普通 `val`。
//   ② 更要命的：原本文案是**写死的中文**，压根没进语言包 ——
//      逸风切到英文看隐私政策，看到一半蹦出「最后更新：2026 年 10 月 3 日…」
//      和「适用版本：安卓版 3.5.7…」，整页就这两行是中文。
//      **隐私政策是政策，必须五种语言都读得懂**，所以这两条现在也是资源了。
private val POLICY_UPDATED get() = AppCtx.s(R.string.privacy_policy_updated)

/**
 * 「适用版本」那行。
 *
 * ⚠️ 2026-10-03 又踩一次：原来是 `AppCtx.s(R.string.privacy_policy_scope)`，
 *    版本号**写死在四份语言包里**（3.5.8 / code 32）——
 *    这次升到 3.5.9，政策上还印着 3.5.8，等于政策说了假话。
 *    现在版本号从 [BuildConfig] 现取，升版本不用再去动四份语言包。
 *    另一处 `get()` 也是故意的：`AppCtx.s` 是运行时取串，
 *    用 `val`（带缓存语义的顶层属性）会在第一次访问时把当时那门语言钉死。
 */
private val POLICY_SCOPE get() = AppCtx.s(
    R.string.privacy_policy_scope,
    BuildConfig.VERSION_NAME,
    BuildConfig.VERSION_CODE,
)

/** 一句话总结（单独拿出来，放最上面高亮） */
private val POLICY_SUMMARY get() = AppCtx.s(R.string.privacy_intro)

private val policySections = listOf(
    PolicySection(
        AppCtx.s(R.string.privacy_section1_title),
        listOf(
            AppCtx.s(R.string.privacy_no_account),
            AppCtx.s(R.string.privacy_no_device_id),
            AppCtx.s(R.string.privacy_no_behavior),
            AppCtx.s(R.string.privacy_no_ads),
            AppCtx.s(R.string.privacy_no_server),
            AppCtx.s(R.string.privacy_no_internet_permission),
            AppCtx.s(R.string.privacy_permission_detail),
            // 2026-10-03 加：这一版起有联网了（检查更新），单独列出来讲清楚 ——
            // 唯一一件事、用的哪个权限、请求里带了什么、在哪儿能关掉。
            AppCtx.s(R.string.privacy_net_title),
            AppCtx.s(R.string.privacy_net_body),
            AppCtx.s(R.string.privacy_net_permission),
            AppCtx.s(R.string.privacy_net_no_upload),
            AppCtx.s(R.string.privacy_net_control),
            // 2026-10-03 补：逸风追问「会收集其他信息吗」——
            //   前一条只说「不带你的数据」，对，但没说完。这两条把
            //   ① 联网本身必然暴露的（IP / 文件名 / 时间 / 请求头里的 App 名）
            //   ② 国内必须过第三方加速镜像 这件事
            //   都摊开写清楚。政策宁可写得难看，也别写得让人以为有多干净。
            AppCtx.s(R.string.privacy_net_visible),
            AppCtx.s(R.string.privacy_net_mirror),
            // 2026-10-03 第三版：参考图。特意说了两件事 ——
            //   ① 权限：不申请相册 / 存储，走系统图片选择器；
            //   ② 备份会变大：图会 base64 进备份文件。这条不写清楚，
            //      以后他看到备份突然几十 MB 会觉得「怎么变味了」。
            AppCtx.s(R.string.privacy_photos_title),
            AppCtx.s(R.string.privacy_photos_body),
        ),
    ),
    PolicySection(
        AppCtx.s(R.string.privacy_data_storage_title),
        listOf(
            AppCtx.s(R.string.privacy_data_storage_body),
            AppCtx.s(R.string.privacy_data_storage_access),
            AppCtx.s(R.string.privacy_data_storage_uninstall),
            AppCtx.s(R.string.privacy_data_storage_backup),
        ),
    ),
    PolicySection(
        AppCtx.s(R.string.privacy_data_delete_title),
        listOf(
            AppCtx.s(R.string.privacy_data_delete_items),
            AppCtx.s(R.string.privacy_data_delete_uninstall),
            AppCtx.s(R.string.privacy_data_delete_no_copy),
        ),
    ),
    PolicySection(
        AppCtx.s(R.string.privacy_cloud_backup_title),
        listOf(
            AppCtx.s(R.string.privacy_cloud_backup_body),
            AppCtx.s(R.string.privacy_cloud_backup_control),
        ),
    ),
    PolicySection(
        AppCtx.s(R.string.privacy_export_title),
        listOf(
            AppCtx.s(R.string.privacy_export_file),
            AppCtx.s(R.string.privacy_export_share_warning),
        ),
    ),
    PolicySection(
        AppCtx.s(R.string.privacy_minors_title),
        listOf(AppCtx.s(R.string.privacy_minors_body)),
    ),
    PolicySection(
        AppCtx.s(R.string.privacy_changes_title),
        listOf(AppCtx.s(R.string.privacy_changes_body)),
    ),
    PolicySection(
        AppCtx.s(R.string.privacy_contact_title),
        listOf(
            AppCtx.s(R.string.privacy_contact_body),
            AppCtx.s(R.string.privacy_contact_email),
            "· GitHub Issues：github.com/1100-docx/CommissionBook/issues",
            AppCtx.s(R.string.privacy_contact_email_note),
        ),
    ),
)

private val policyPermissions = listOf(
    // 2026-10-03 加的这两条排在前面 —— 它们是这一版新增的、也是唯一跟「联不联网」
    // 有关的权限，先摆出来最省得人翻。
    PolicyPermission(AppCtx.s(R.string.settings_network_title), AppCtx.s(R.string.settings_network_desc), AppCtx.s(R.string.settings_network_off)),
    PolicyPermission(AppCtx.s(R.string.settings_install_title), AppCtx.s(R.string.settings_install_desc), AppCtx.s(R.string.settings_install_off)),
    PolicyPermission(AppCtx.s(R.string.settings_biometric_title), AppCtx.s(R.string.settings_biometric_desc), AppCtx.s(R.string.settings_biometric_off)),
    PolicyPermission(AppCtx.s(R.string.settings_biometric_legacy_title), AppCtx.s(R.string.settings_biometric_legacy_desc), AppCtx.s(R.string.settings_biometric_legacy_off)),
    PolicyPermission(AppCtx.s(R.string.settings_notify_title), AppCtx.s(R.string.settings_notify_desc), AppCtx.s(R.string.settings_notify_off)),
    PolicyPermission(AppCtx.s(R.string.settings_vibrate_title), AppCtx.s(R.string.settings_vibrate_desc), AppCtx.s(R.string.settings_vibrate_off)),
    // 2026-10-03 第三版加：参考图用的系统选择器。**「不申请」也是一种要报备的事** ——
    // 权限表里明写着一条「相册 / 存储：不申请」，比只在正文里提一句更让人放心。
    PolicyPermission(AppCtx.s(R.string.settings_photos_title), AppCtx.s(R.string.settings_photos_desc), AppCtx.s(R.string.settings_photos_off)),
)

// MARK: - 正文（两处共用）

@Composable
private fun PolicyBody(scroll: ScrollState, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme

    Column(
        modifier
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {

        // ① 一句话 —— 高亮，一眼看完
        SoftCard(Modifier.fillMaxWidth()) {
            Text(AppCtx.s(R.string.common_one_sentence), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = cs.primary)
            Spacer(Modifier.height(6.dp))
            Text(
                POLICY_SUMMARY,
                fontSize = 15.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Medium,
                color = cs.onSurface,
            )
        }

        // ② 正文各段（一、二、三…）
        //
        // ⚠️ 「四、用到的权限」原来是硬塞在正文最前面的（想让他先看到权限），
        // 结果读起来是「四、一、二、三…」—— 逸风 2026-09-29 指出。
        // 现在老实按号排：三、数据怎么删 之后就是它（index == 2）。
        policySections.forEachIndexed { idx, s ->
            SoftCard(Modifier.fillMaxWidth()) {
                Text(s.heading, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
                Spacer(Modifier.height(8.dp))
                s.body.forEachIndexed { i, p ->
                    if (i > 0) Spacer(Modifier.height(8.dp))
                    Text(p, fontSize = 13.sp, lineHeight = 21.sp, color = cs.onSurfaceVariant)
                }
            }
            if (idx == 2) PermissionCard()
        }

        // ④ 适用范围（正文最后一块 —— 划到这里就算看完了）
        SoftCard(Modifier.fillMaxWidth()) {
            Text(
                AppCtx.s(R.string.common_that_is_all),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                POLICY_SCOPE + AppCtx.s(R.string.privacy_intro_2),
                fontSize = 12.sp,
                lineHeight = 19.sp,
                color = cs.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(10.dp))
    }
}

/**
 * 「四、用到的权限，各自干什么」
 *
 * 单独抽出来，是为了让它能插在「三、数据怎么删」后面（号是四，就排在第四位）。
 */
@Composable
private fun PermissionCard() {
    val cs = MaterialTheme.colorScheme

    SoftCard(Modifier.fillMaxWidth()) {
        Text(
            AppCtx.s(R.string.privacy_permissions_title),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = cs.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(AppCtx.s(R.string.privacy_permissions_only_four), fontSize = 12.sp, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        policyPermissions.forEachIndexed { i, p ->
            if (i > 0) Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .padding(top = 3.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(cs.primary.copy(alpha = 0.14f))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text(p.name, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = cs.primary)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.why, fontSize = 13.sp, lineHeight = 20.sp, color = cs.onSurface)
                    Text(
                        AppCtx.s(R.string.privacy_deny_consequences) + p.refuse,
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            AppCtx.s(R.string.privacy_fingerprint_note) +
                AppCtx.s(R.string.privacy_notification_note),
            fontSize = 12.sp,
            lineHeight = 19.sp,
            color = cs.onSurfaceVariant,
        )
    }
}

// MARK: - ① 首次启动的同意页

/**
 * 第一次装完打开 App 会停在这一页：**必须划到最底下，按钮才点得动**。
 *
 * 这是应用市场要的合规姿势：用户有机会真正读完，而不是闭眼点同意。
 * 不同意的人也得有条路走 —— 所以下面还有个「不同意」（退出 App），
 * 不能只有「同意」一个按钮。
 */
@Composable
fun PrivacyConsentScreen(onAgree: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val scroll = rememberScrollState()

    // 划到底了没？（留 8px 容差，别让人死命蹭最后那一下）
    val reachedBottom by remember {
        derivedStateOf { scroll.value >= scroll.maxValue - 8 }
    }

    // 这一步没有「退回去」可去，返回键就先按住不放（不然一不小心就退出去了）
    BackHandler { }

    ScreenSurface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {

            Column(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 10.dp),
            ) {
                Text(
                    AppCtx.s(R.string.common_welcome),
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface,
                )
                Text(
                    AppCtx.s(R.string.privacy_before_start),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }

            PolicyBody(scroll, Modifier.weight(1f).fillMaxWidth())

            // 底部：同意 / 不同意
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(cs.surface.copy(alpha = 0.97f))
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (reachedBottom) AppCtx.s(R.string.privacy_read_done_agree) else AppCtx.s(R.string.privacy_not_scrolled_yet),
                    fontSize = 12.sp,
                    color = if (reachedBottom) cs.primary else cs.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                Button(
                    onClick = {
                        lightTick(haptic)
                        onAgree()
                    },
                    enabled = reachedBottom,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                ) {
                    Text(
                        if (reachedBottom) AppCtx.s(R.string.privacy_read_and_agreed) else AppCtx.s(R.string.privacy_scroll_to_bottom),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                TextButton(
                    onClick = { (context as? Activity)?.finish() },
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Text(
                        AppCtx.s(R.string.privacy_disagree_exit),
                        fontSize = 13.sp,
                        color = cs.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

// MARK: - ② 设置页里翻看的版本

/**
 * 只读版：从设置页进来，盖住全屏（底部导航栏也一起盖掉），
 * 返回键 / 左上角箭头都能回去。
 */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current

    // 系统返回键也走同一条路（不然会直接退出 App，体验很怪）
    BackHandler { onBack() }

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
                CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, AppCtx.s(R.string.common_back)) {
                    lightTick(haptic)
                    onBack()
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(AppCtx.s(R.string.privacy_policy_title), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
                    Text(
                        POLICY_UPDATED,
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            PolicyBody(rememberScrollState(), Modifier.weight(1f).fillMaxWidth())

            // 底部给系统导航栏留位置（安卓 15 起边到边，不留会贴着金刚键）
            Box(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            )
        }
    }
}
