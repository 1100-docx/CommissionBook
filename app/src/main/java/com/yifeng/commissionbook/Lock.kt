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
            .setTitle("解锁约稿账本")
            .setSubtitle("用指纹 / 面容 / 密码解锁")
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

    var reminderEnabled: Boolean
        get() = sp.getBoolean("reminderEnabled", true)
        set(v) = sp.edit().putBoolean("reminderEnabled", v).apply()

    /**
     * 隐私政策同意 —— 存的是**同意时那一版政策**的版本号（见 [POLICY_VERSION]）。
     *
     * 空字符串 = 还没同意过 → 启动时先弹同意页，读完划到底才进得来。
     * 以后政策改了就把 [POLICY_VERSION] 改掉，这里存的旧值对不上，会再弹一次。
     */
    var privacyAgreedVersion: String
        get() = sp.getString("privacyAgreedVersion", "") ?: ""
        set(v) = sp.edit().putString("privacyAgreedVersion", v).apply()
}

/** 开着保护时，切后台就糊 —— 安卓这边用系统开关一步搞定，还顺手挡截图 */
fun FragmentActivity.applyPrivacyShield(on: Boolean) {
    if (on) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
