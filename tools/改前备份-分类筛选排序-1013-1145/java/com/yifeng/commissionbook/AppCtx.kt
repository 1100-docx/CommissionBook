package com.yifeng.commissionbook

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes

/**
 * 全局取字符串（2026-10-03 加，多语言用）。
 *
 * **为什么需要这么个东西**：
 * Compose 的 `stringResource()` 是 **@Composable 专用**，只能写在界面函数里。
 * 而这个工程有 200 多处文案在**非界面代码**里 ——
 * 通知文案（Reminders）、话术模板（Extras）、枚举描述（Models / Terms）……
 * 那些地方调 `stringResource()` 直接编译不过，要一个个改函数签名把 Context 传进去，
 * 四五十个函数全得动，太折腾也容易改漏。
 *
 * 所以统一走这一个入口，**不区分 Composable 还是普通函数**：
 *   Compose 界面里  → AppCtx.s(R.string.ledger_title)
 *   普通函数里      → AppCtx.s(R.string.ledger_title, count)
 *
 * **代价**：它不是响应式的 —— 换了语言，已经画出来的界面**不会自己重画**。
 * ⚠️ 但这恰好就是这个 App 要的行为：逸风要的就是「切换语言之后**自动重启 App**」，
 *    重启之后所有文案自然都是新语言的。所以不走 `stringResource()` 不亏。
 *
 * ⚠️ 存的是 **applicationContext**，绝不能存 Activity —— 全局单例持有 Activity 就是内存泄漏。
 *
 * ⚠️⚠️ 但 applicationContext 有个**要命的坑**（2026-10-03 真机踩到）：
 *    它的 `resources` **不会**跟着 Activity 的 Configuration 走。
 *    所以哪怕 MainActivity.attachBaseContext 把语言塞对了，界面里所有
 *    `AppCtx.s(...)` 取出来的**还是旧语言** —— 表现就是「选了语言、重启了，只有系统弹的
 *    那几个字变了，App 自己的字一个没动」。
 *    解法：这里 init 的时候，把 applicationContext 也**包一层**
 *    `createConfigurationContext(带语言的配置)`，拿包好的那个去取串。
 */
object AppCtx {

    @Volatile
    private var app: Context? = null

    /**
     * 在 MainActivity.onCreate 里调一次即可。
     *
     * @param language 语言 tag（"" = 跟随系统），来自 [Prefs.appLanguage]。
     */
    fun init(context: Context, language: String = "") {
        val base = context.applicationContext
        app = if (language.isEmpty()) {
            base
        } else {
            val cfg = Configuration(base.resources.configuration)
            cfg.setLocales(android.os.LocaleList.forLanguageTags(language))
            base.createConfigurationContext(cfg)
        }
    }

    /**
     * 取本地化字符串。
     *
     * 拿不到 context（理论上只会在初始化之前）就返回**空串**而不是抛异常 ——
     * 界面少一行字，总好过整个 App 崩掉。
     */
    fun s(@StringRes resId: Int, vararg args: Any): String =
        app?.getString(resId, *args) ?: ""

    /**
     * 拿原始 context（2026-10-07 加，触感那边要用系统服务）。
     *
     * ⚠️ 这是个**故意留的窄口子**：只给那种「拿不到 context 就干不了事」的地方用
     *    （现在只有 [Haptics] 取 Vibrator）。别拿它去读文件、搞界面 ——
     *    要字符串走 [s]，要应用上下文走别处。
     */
    fun context(): Context? = app
}
