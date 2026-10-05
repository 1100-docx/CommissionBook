package com.yifeng.commissionbook

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

/**
 * 「手机重启之后，把提醒闹钟排回来」—— 走 `JobScheduler` 的**持久化任务**，不走开机广播。
 *
 * ## 为什么不用开机广播（2026-10-05 晚，3.5.29 的第二次尝试）
 *
 * [BootReceiver] 那条路**代码上完全正确**（注册对、`RECEIVE_BOOT_COMPLETED` granted、`exported=true`），
 * 但在逸风那台 OPPO 上，系统日志白纸黑字写着：
 *
 * ```
 * SKIPPED ... #0: (manifest)  name=com.yifeng.commissionbook.BootReceiver  exported=true
 *   reason: oplus startup
 * ```
 *
 * —— ColorOS 的「启动管理」把开机广播**直接跳过**了，压根没交给 App。
 * 唯一解法是让用户去开「允许自启动 / 允许后台运行 / 允许关联启动」。
 * 逸风原话：「**但是这样的话应用一启动就需要自启动权限还有关联启动权限**」——
 * 他说得对：一个记账 App 为了重排提醒去要自启动（还搭上完全无关的「关联启动」），
 * 代价太大、观感也差（朋友装的时候一样会犯嘀咕）。**所以不要求用户开任何东西。**
 *
 * ## 改成 JobScheduler 之后
 *
 * - `setPersisted(true)` 的任务登记在**系统**里，设备重启后由系统服务自己恢复，
 *   不经过「应用接收开机广播」那条会被厂商拦的路 → 用户什么都不用开；
 * - 时机上晚几分钟完全无所谓 —— 我们要的是「重启后把闹钟补回来」，不是「准点」；
 * - [BootReceiver] 那条**留着当双保险**：在没被厂商拦的机器上（原生安卓等）它先跑，更快。
 *
 * ## 兜底仍然在
 *
 * **打开 App 就会重排**（见 [AppState.init]）。就算厂商把这两条路都掐死，
 * 最坏也只是「重启后需要打开一次 App」—— 不会再糟。
 */
class RescheduleJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        // 开关关着就什么都不做 —— 别背着用户把提醒偷偷排上
        if (Prefs(this).reminderEnabled) {
            Reminders.reschedule(this, Store(this).loadCommissions(), true)
        }
        jobFinished(params, false)   // false = 这活儿干完了，别重试
        return false                 // 已经在本线程干完，不需要再占一个工作线程
    }

    override fun onStopJob(params: JobParameters?): Boolean = false   // 被系统掐了就掐了，不补跑
}

/** 登记那个「重启后排提醒」的持久化任务。**幂等**：同一个 JOB_ID 重复登记只是覆盖。 */
object RescheduleJob {

    private const val JOB_ID = 4201

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, RescheduleJobService::class.java))
            .setPersisted(true)          // ‼️ 关键：任务存进系统，重启后由系统恢复
            .setMinimumLatency(5_000)    // 别在开机那一秒跟一堆启动项挤
            .build()
        // 有些 ROM 会限制任务数，排不上也不该崩 —— 反正打开 App 还有兜底
        runCatching { scheduler.schedule(job) }
    }
}
