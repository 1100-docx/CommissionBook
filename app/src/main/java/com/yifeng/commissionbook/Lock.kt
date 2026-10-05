package com.yifeng.commissionbook

import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * 指纹锁。
 *
 * 跟 iOS 版的面容锁对比：
 * - iOS：`canEvaluatePolicy` → `evaluatePolicy` → 回主线程
 * - 安卓：**系统自己弹窗**，我们只提供回调 —— 三行就够
 *
 * 系统弹的那个窗长什么样，由**手机厂商决定**：有指纹弹指纹、
 * 有面容弹面容、都没有会退成图案/密码。**通用版的意义就在这** ——
 * 我们不做界面，交给系统，所以谁装都能用。
 */
object BioLock {

    /**
     * 允许的验证方式。
     *
     * ⚠️ 「生物识别 + 设备密码」这个组合**安卓 11（API 30）才支持**。
     * 安卓 10 及以下只给 BIOMETRIC_WEAK，不然 `PromptInfo` 会直接抛异常。
     * （这台朋友的手机是安卓 11，正好卡在分界线上 —— 不分支就会有人装不上/一点就崩。）
     */
    private fun allowed(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        }

    /** 这台手机能不能用（能不能弹得出来） */
    fun available(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        return try {
            manager.canAuthenticate(allowed()) == BiometricManager.BIOMETRIC_SUCCESS
        } catch (e: Exception) {
            // 老机器上个别 ROM 会抽风，当成「用不了」处理，别让 App 挂掉
            false
        }
    }

    /** 验一次；成功了叫 onOk，没成功/取消叫 onNo */
    fun authenticate(activity: FragmentActivity, onOk: () -> Unit, onNo: (String) -> Unit) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onOk()
                }

                override fun onAuthenticationError(code: Int, msg: CharSequence) {
                    onNo(msg.toString())
                }

                override fun onAuthenticationFailed() {
                    // 单次没认出来（比如手指没放正）—— 系统会自己让你再试，
                    // 这里**什么都不做**才对；一报错就把弹窗关了才是 bug
                }
            })

        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(AppCtx.s(R.string.ledger_unlock_ledger))
            .setSubtitle(AppCtx.s(R.string.ledger_unlock_biometric))
            .setAllowedAuthenticators(allowed())

        // 安卓 10 及以下：单独告诉它「认不出也可以用锁屏密码」，不然没指纹的人会被锁在外面
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }

        prompt.authenticate(builder.build())
    }
}

/** 设置项的存盘（开关这类小东西，用系统自带的偏好存就够了） */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var lockEnabled: Boolean
        get() = sp.getBoolean("lockEnabled", false)
        set(v) = sp.edit().putBoolean("lockEnabled", v).apply()

    var autoBackup: Boolean
        get() = sp.getBoolean("autoBackup", false)
        set(v) = sp.edit().putBoolean("autoBackup", v).apply()


    /**
     * 隐私政策同意 —— 存的是**同意时那一版政策**的版本号（见 [POLICY_VERSION]）。
     *
     * 空字符串 = 还没同意过 → 启动时先弹同意页，读完划到底才进得来。
     * 以后政策改了就把 [POLICY_VERSION] 改掉，这里存的旧值对不上，会再弹一次。
     */
    var privacyAgreedVersion: String
        get() = sp.getString("privacyAgreedVersion", "") ?: ""
        set(v) = sp.edit().putString("privacyAgreedVersion", v).apply()

    /**
     * 使用模式：买家 / 画师（见 [AppMode]）。
     * 存的是 key（"buyer" / "artist"），不是显示名 —— 以后改叫法不会把存值读坏。
     */
    var appMode: String
        get() = sp.getString("appMode", AppMode.BUYER.key) ?: AppMode.BUYER.key
        set(v) = sp.edit().putString("appMode", v).apply()

    /**
     * 深浅色：跟随系统 / 浅色 / 暗色（2026-10-03 加，见 [AppAppearance]）。
     * 同样存 key（"system" / "light" / "dark"），不是显示名 —— 以后改叫法不会把存值读坏。
     *
     * 老用户升级上来没这个键 → 读出默认值 SYSTEM，行为跟升级前一模一样（跟随系统）。
     */
    var appearance: String
        get() = sp.getString("appearance", AppAppearance.SYSTEM.key) ?: AppAppearance.SYSTEM.key
        set(v) = sp.edit().putString("appearance", v).apply()

    /**
     * 启动时自动检查更新（2026-10-03 加，**只有安卓版有**这个功能）。
     *
     * 默认**开** —— 这个 App 是发给朋友用的，没法上应用商店，
     * 不主动提醒的话他们永远停在旧版本。不想要就在设置里关掉，
     * 关掉之后 App 一次网络请求都不发（隐私政策里就是这么写的，得对得上）。
     */
    var updateAutoCheck: Boolean
        get() = sp.getBoolean("updateAutoCheck", true)
        set(v) = sp.edit().putBoolean("updateAutoCheck", v).apply()

    /**
     * 上次检查更新的时刻（毫秒）。
     *
     * 用来限流 —— 别每次冷启动都去敲一次网络（见 MainActivity 里那 12 小时）。
     * ⚠️ 检查**失败**也要记：不记的话网络不通时每次开 App 都要白等一轮超时。
     */
    var lastUpdateCheckAt: Long
        get() = sp.getLong("lastUpdateCheckAt", 0L)
        set(v) = sp.edit().putLong("lastUpdateCheckAt", v).apply()

    /**
     * 「跳过这个版本」记下的 versionCode。0 = 没跳过任何版本。
     *
     * 生效范围就是这个号：出了更新的版本，号比它大，自然又会提示。
     */
    var skippedUpdateCode: Int
        get() = sp.getInt("skippedUpdateCode", 0)
        set(v) = sp.edit().putInt("skippedUpdateCode", v).apply()

    /**
     * 首启那一问「你是买家还是画师」问过没有（2026-09-30 加）。
     *
     * false = 还没问过 → 启动时（政策同意完、权限要完）弹一次。
     * 选完 / 点「稍后再说」都置 true —— 只问一次，以后在设置里改。
     */
    var modeChosen: Boolean
        get() = sp.getBoolean("modeChosen", false)
        set(v) = sp.edit().putBoolean("modeChosen", v).apply()

    /**
     * 首启「用法都在设置里」那一层引导弹窗，弹过没有（2026-10-01 加）。
     *
     * false = 还没弹过 → 政策同意完 + 模式问完，补弹一次。
     * ⚠️ 这个键是**新加的**，所以老用户升级上来也会被补弹一次
     *    （[modeChosen] 早就是 true 了，但没人给他们讲过帮助页在哪儿）。
     */
    var guideShown: Boolean
        get() = sp.getBoolean("guideShown", false)
        set(v) = sp.edit().putBoolean("guideShown", v).apply()

    /**
     * 帮助页看过没有（2026-10-01 加）。
     *
     * false = 底栏「设置」那一格挂一枚小角标「新」（见 BottomBar）；
     * 进过一次帮助页就置 true，角标消失 —— 提示过就不再烦人。
     */
    var helpSeen: Boolean
        get() = sp.getBoolean("helpSeen", false)
        set(v) = sp.edit().putBoolean("helpSeen", v).apply()

    /**
     * 语言（2026-10-03 加，多语言）。
     *
     * 空串 = 跟随系统；否则存 tag（"zh-CN" / "zh-HK" / "zh-TW" / "en-GB" / "en-US"）。
     *
     * ⚠️ **为什么自己存一份，不用 `AppCompatDelegate.setApplicationLocales`**：
     *    那套东西的存储是挂在「AppCompatActivity 的语言委托」上的，而本工程的
     *    MainActivity 继承的是 `FragmentActivity`（AppCompatActivity 的父类，
     *    **没有那套委托**）。真机实测：调了 setApplicationLocales、也 recreate 了，
     *    界面纹丝不动 —— 因为 `getApplicationLocales()` 拿回来还是空的。
     *    所以老老实实存自己的 SharedPreferences，读取时机完全可控。
     */
    var appLanguage: String
        get() = sp.getString("appLanguage", "") ?: ""
        set(v) = sp.edit().putString("appLanguage", v).apply()

    /**
     * 「通知权限问过了没有」——2026-10-05 加。
     *
     * 只用来做一件事：**自动问一次，问过就不再自动问**（用户拒了也别反复弹，
     * 之后就归他自己在设置里拨开关）。
     */

    companion object {
        /**
         * 在 `Activity.attachBaseContext` 里读语言用。
         *
         * 那个时机太早，来不及 `new Prefs(...)` 走一遍包装，所以直接开偏好文件读 ——
         * 名字和上面那个 `sp` **必须一模一样**（"settings"）。
         */
        fun languageOf(context: Context): String =
            context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getString("appLanguage", "") ?: ""
    }

    /**
     * 画师自己定的加价项清单（2026-10-01 加）。
     *
     * 存成一段 JSON 字符串（见 [encodeQuoteExtras] / [loadQuoteExtras]）。
     * 空串 = 没改过 → 用默认那三项。
     *
     * ⚠️ 跟「模式 / 震动 / 备份开关」一样是**本机设置**，不进备份文件 ——
     *    这样备份格式一个字节没动，跟 iOS 版照样互通。
     */
    var quoteExtras: String
        get() = sp.getString("quoteExtras", "") ?: ""
        set(v) = sp.edit().putString("quoteExtras", v).apply()
}

/** 开着保护时，切后台就糊 —— 安卓这边用系统开关一步搞定，还顺手挡截图 */
fun FragmentActivity.applyPrivacyShield(on: Boolean) {
    if (on) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
