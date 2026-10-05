package com.yifeng.commissionbook

import android.app.Application

/**
 * 存在的唯一理由：**保证 [AppCtx] 在任何组件把进程冷启动起来时都已经初始化**。
 *
 * ⚠️ 2026-10-05 实测踩到的大坑（3.5.22 之前的版本一直有，不是这次改出来的）：
 *
 *   到期提醒走的是 `AlarmManager → ReminderReceiver`。闹钟响的时候，
 *   App **通常根本没在运行** —— 系统是把进程从零拉起来的。
 *   而 `AppCtx.init(...)` 原来只在 `MainActivity.onCreate` 里调一次，
 *   于是这条路上 `AppCtx.s(...)` 拿不到 context，按设计返回**空串**
 *   （见 AppCtx.s 的注释），接着：
 *
 *       NotificationChannel(id, "", IMPORTANCE_DEFAULT)
 *       → createNotificationChannel 抛 IllegalArgumentException
 *       → `Unable to start receiver ...ReminderReceiver` → 进程当场崩
 *       → **提醒一条都发不出来**
 *
 *   也就是说：从多语言改造（安卓 3.5.6）起，只要 App 没在后台活着，
 *   截止日提醒就是**静默失效**的 —— 手机上什么都不会发生，用户只会觉得「这功能没做」。
 *   界面里一切正常，所以看代码看不出来，只有真机/模拟器让它真响一次才会暴露。
 *
 * 修法：把 AppCtx 的初始化提到 Application.onCreate —— 它比任何
 * Activity / BroadcastReceiver / Service 都先跑，这条路就再也不会漏了。
 * （MainActivity 里那次 init 留着也无害：同一个 context，重复 init 是幂等的。）
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // 语言偏好也在这里读：通知渠道名是本地化字符串，冷启动时必须是对的语种。
        AppCtx.init(this, Prefs(this).appLanguage)
        // 2026-10-05 晚加（3.5.29）：登记「重启后排提醒」那个持久化任务。
        // 幂等 —— 每次进程起来登记一次就行（同一 JOB_ID 会覆盖）。
        // OPPO 那类会拦开机广播的机器，就靠它把重启后的提醒捞回来，用户不用开任何权限。
        RescheduleJob.schedule(this)
    }
}
