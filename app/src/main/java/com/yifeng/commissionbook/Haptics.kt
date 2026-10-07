package com.yifeng.commissionbook

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/*
 * 触感（2026-10-07 加，安卓侧）。
 *
 * 逸风原话：「我发现 Android 版软件没有震动欸，加上震动，同样是轻中重三档调节」。
 *
 * ⚠️ 为什么原来的写法在他手机上是「没震动」：
 *    原来 [lightTick] 走的是 Compose 的 `HapticFeedbackType.TextHandleMove` ——
 *    这条路震不震、震多响，由**厂商 ROM 的「系统触感反馈」开关和它自己的映射表**决定。
 *    ColorOS 上这个映射要么被关掉、要么弱到感觉不出来，所以他那台一点反应都没有。
 *    这里改成**直接调 Vibrator**：VIBRATE 权限早就声明了，只要震子能转就震得动，
 *    跟系统那个开关没关系。
 *
 * 四档怎么实现（2026-10-07 从「三档 + 一个开关」扩成四档）：
 *    优先用**振幅可控**的 one-shot（真的能区分轻重），
 * 硬件不支持振幅就退回系统的三种预设（点一下 / 点 / 重按）。
 */

/** 触感档位。存的是 key，不是显示名 —— 以后改叫法不会把存值读坏（跟 AppAppearance 一个规矩）。 */
enum class HapticLevel(val key: String, val labelRes: Int) {

    // ⚠️ OFF 必须排第一个：这个顺序 = 设置页那四颗胶囊的顺序。
    //    2026-10-07 逸风原话：「建议双端振动都做四个档，关/轻/中/重」——
    //    所以「关」并成第一档，原来那个独立开关撤掉（双端一致）。
    //    ⚠️ key 是**存进 SharedPreferences 的值**，写成 "off" 这种英文、别用显示名，
    //       以后改叫法不会把老用户的设置读坏（老值 "medium" 照样认得）。
    OFF("off", R.string.haptic_off),
    LIGHT("light", R.string.haptic_light),
    MEDIUM("medium", R.string.haptic_medium),
    HEAVY("heavy", R.string.haptic_heavy);

    // ⚠️ 显示名必须是计算属性：enum 常量初始化时 AppCtx 还没准备好（老坑，见 Theme.kt）
    val label: String get() = AppCtx.s(labelRes)

    /** 时长（毫秒）：轻档短促，重档稍长 —— 手感差别一半靠时长 */
    val durationMs: Long
        get() = when (this) {
            OFF -> 0L
            LIGHT -> 10L
            MEDIUM -> 18L
            HEAVY -> 26L
        }

    /** 振幅（1~255）—— 另一半靠这个 */
    val amplitude: Int
        get() = when (this) {
            OFF -> 0
            LIGHT -> 70
            MEDIUM -> 150
            HEAVY -> 255
        }

    /** 震子不支持振幅控制时的退路：系统预设（API 29+ 才有） */
    val predefined: Int
        get() = when (this) {
            OFF -> VibrationEffect.EFFECT_TICK
            LIGHT -> VibrationEffect.EFFECT_TICK
            MEDIUM -> VibrationEffect.EFFECT_CLICK
            HEAVY -> VibrationEffect.EFFECT_HEAVY_CLICK
        }

    companion object {
        fun from(key: String): HapticLevel = entries.firstOrNull { it.key == key } ?: MEDIUM
    }
}

object Haptics {

    /**
     * 当前档位。App 启动时由 [AppState] 灌一次；设置页一改，[AppState.switchHaptic] 立刻跟进。
     *
     * 为什么缓存在内存里而不是每次现读 SharedPreferences：
     * 这个是**每次点击都要走**的路径，挡在触摸反馈上的东西越少越好。
     */
    @Volatile
    var level: HapticLevel = HapticLevel.MEDIUM

    private fun vibrator(): Vibrator? = runCatching {
        val ctx = AppCtx.context() ?: return null
        if (Build.VERSION.SDK_INT >= 31) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    /** 上一次震的时刻。用来吃掉「全局那只耳朵 + 按钮自己那次」撞在一起的重复。 */
    private var lastTickAt = 0L

    /**
     * 震一下。
     *
     * @return true = 已经震了；false = 这台机器震不了（没震子 / 取不到服务），
     *         调用方这时可以退回 Compose 那条老路当兜底。
     *
     * ⚠️ 全程 runCatching：有些机型/模拟器上 `vibrate()` 会抛（权限被 ROM 收走之类），
     *    触感这种东西**再怎么样也不能把界面搞崩**，震不动就算了。
     */
    fun tick(): Boolean = runCatching {
        // 「关」档：什么都不做，但要 **return true** ——
        // 返回 false 的话调用方会退回 Compose 那条老路当兜底，那不就又震上了吗。
        if (level == HapticLevel.OFF) return true

        // ⚠️ 60ms 内重复调用直接当成功返回、不再震（2026-10-07 加）。
        //    起因：全局触感（MainActivity.dispatchTouchEvent）和按钮自己的
        //    lightTick() 会**同时**落在同一根手指上，只差几毫秒 ——
        //    不去重的话按一下震两声，比不震更难受。
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastTickAt < 60L) return true
        lastTickAt = now

        val v = vibrator() ?: return false
        if (!v.hasVibrator()) return false

        val lv = level
        val effect = when {
            v.hasAmplitudeControl() ->
                VibrationEffect.createOneShot(lv.durationMs, lv.amplitude)

            Build.VERSION.SDK_INT >= 29 ->
                VibrationEffect.createPredefined(lv.predefined)

            else ->
                VibrationEffect.createOneShot(lv.durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
        }

        v.vibrate(effect)
        true
    }.getOrDefault(false)
}
