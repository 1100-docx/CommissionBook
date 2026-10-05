package com.yifeng.commissionbook

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Calendar

/**
 * 截止日提醒。
 *
 * 跟 iOS 版对齐的口径：**到期前 3 天、1 天各一次，晚上 20:00**；
 * 已交付的、已归档的**不提醒**。
 *
 * 安卓这边做法：用 `AlarmManager` 给每条约稿定两个闹钟，
 * 到点由系统喊醒 `ReminderReceiver`，它负责弹通知。
 * （没用 WorkManager —— 这个需求是「几点几分响一次」，
 *  用闹钟比用「定时任务」直白，而且不引额外依赖。）
 */
object Reminders {

    const val CHANNEL_ID = "deadline"
    // ⚠️ 2026-10-03：从 const val 降级成普通 val —— 多语言改造后它取的是
    //    AppCtx.s(R.string.…)，函数调用不能当编译期常量。
    // ⚠️ 必须做成 getter：写成 `val CHANNEL_NAME = AppCtx.s(...)` 的话，
    //    这个 object 一旦被加载就求值一次 —— 那会儿 AppCtx 可能还没 init，
    //    通知栏里那条频道名会变成空白。
    val CHANNEL_NAME: String get() = AppCtx.s(R.string.settings_deadline_reminder)
    private val DAYS_BEFORE = intArrayOf(3, 1)
    private const val HOUR = 20

    /** 把所有闹钟重排一遍（每次数据一变、App 一启动都调它，最简单可靠） */
    fun reschedule(context: Context, items: List<Commission>, enabled: Boolean) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // 先全撤掉，再按当前数据重定 —— 避免「改了截止日，老闹钟还在」这种鬼事
        for (c in items) {
            for (d in DAYS_BEFORE) {
                am.cancel(pending(context, c, d))
            }
        }
        if (!enabled) return

        val now = System.currentTimeMillis()
        for (c in items) {
            if (c.archived || c.status == CommissionStatus.DELIVERED) continue
            val deadline = c.deadlineMillis ?: continue
            for (d in DAYS_BEFORE) {
                val at = triggerAt(deadline, d)
                if (at > now) {
                    android.util.Log.i("CommissionBook", AppCtx.s(R.string.notify_set_alarm, c.title, d, java.util.Date(at)))
                    // ⚠️ 2026-10-05 改：以前只调 setAndAllowWhileIdle（不精确），
                    //    系统给的窗口是 ±1 小时 —— 20:00 的提醒可能 20:50 才到。
                    //    现在优先用精确闹钟（配合 USE_EXACT_ALARM，见 AndroidManifest）；
                    //    真拿不到权限（canScheduleExactAlarms() == false）就退回老办法，
                    //    宁可晚一点，也不能崩。
                    val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
                    if (exact) {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context, c, d))
                    } else {
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context, c, d))
                    }
                } else {
                    android.util.Log.i("CommissionBook", AppCtx.s(R.string.notify_skip_past, c.title, d, java.util.Date(at)))
                }
            }
        }
    }

    /** 截止日往前 d 天的 20:00 */
    private fun triggerAt(deadlineMillis: Long, d: Int): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = deadlineMillis
        cal.add(Calendar.DAY_OF_YEAR, -d)
        cal.set(Calendar.HOUR_OF_DAY, HOUR)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun pending(context: Context, c: Commission, d: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            // 2026-10-05 通知文案不带画师名/标题之后，这里只需要「还剩几天」。
            putExtra("days", d)
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
        // requestCode 要把「哪条 + 哪一档」都编进去，不然会互相覆盖
        return PendingIntent.getBroadcast(context, c.id.hashCode() * 10 + d, intent, flags)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 2026-10-05 起这里只读「还剩几天」：
        // 画师名 / 稿件标题 / 模式都不再进通知（会露在锁屏上）。
        val days = intent.getIntExtra("days", 0)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    Reminders.CHANNEL_ID,
                    Reminders.CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
        }

        val tap = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // ⚠️ 2026-10-05 改：正文不再带画师名、稿件标题、状态 ——
        //    通知是顶在锁屏上的，旁边人瞄一眼就知道「谁的单子、画的什么、给到哪一步」。
        //    逸风原话：「最好把画师名字也隐藏」。现在只说有一单要到期了 + 还剩几天。
        //    代价：同一天有好几单到期时，几条通知长得一模一样，只能点进去看。
        val text = if (days <= 1) AppCtx.s(R.string.notify_due_tomorrow) else AppCtx.s(R.string.notify_three_days_left)

        val n = NotificationCompat.Builder(context, Reminders.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(AppCtx.s(R.string.notify_commission_due_soon))
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()

        nm.notify(System.currentTimeMillis().toInt(), n)
    }
}
