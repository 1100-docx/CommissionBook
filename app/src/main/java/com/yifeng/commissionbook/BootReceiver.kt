package com.yifeng.commissionbook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机之后，把截止提醒的闹钟重新排一遍。
 *
 * 为什么需要它（2026-10-05 加，安卓 3.5.29）：
 *   闹钟是交给系统 `AlarmManager` 记着的 —— **手机一重启，这些闹钟就全没了**。
 *   系统不会替我们记（iOS 会，所以 iOS 版没这个问题）。
 *   在这之前只有「打开 App」那一步会重排，于是重启之后只要不开 App，提醒就再也不出现。
 *   逸风 2026-10-05 晚问的：「现在就补上吧」。
 *
 * 收两个广播：
 *   ① `BOOT_COMPLETED`         手机开机完成
 *   ② `MY_PACKAGE_REPLACED`    App 自己更新完成（覆盖安装之后补排一次，买个保险）
 *
 * ⚠️ 三个前提，别踩：
 *   - Manifest 里要有 `RECEIVE_BOOT_COMPLETED`（普通权限，安装即授予，不用问用户）
 *   - 这个 App **不能被「强行停止」过** —— 被强停的应用收不到开机广播，这是安卓的规矩。
 *     正常使用碰不到；真碰上了，打开一次 App 也会补回来（AppState.init 里那一排）。
 *   - `AppCtx` 由 [App]（Application）初始化，比任何 receiver 都早 —— 所以这里能放心用
 *     `Prefs` / `Store`（它们内部都要 AppCtx，3.5.23 那个冷启动崩溃就是栽在这上面）。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> return
        }

        val prefs = Prefs(context)
        // 开关关着就什么都别做 —— 别替用户「偷偷」把提醒又排上
        if (!prefs.reminderEnabled) return

        val store = Store(context)
        Reminders.reschedule(context, store.loadCommissions(), true)
    }
}
