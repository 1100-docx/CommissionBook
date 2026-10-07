package com.yifeng.commissionbook

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * 导出 Excel 报表（2026-10-07 加，双端都做）。
 *
 * 逸风：「加入导出Excel报表功能吧，双端都做」（他朋友也赞成这个功能）。
 *
 * 这个文件只干一件事：**把账本翻译成一张表的格子**（哪一列放什么、文案是什么语言）。
 * 真正把格子拼成 .xlsx 的活儿在 [XlsxWriter] 里（那边是纯逻辑、不碰 Android，
 * 方便单独跑一遍验证，iOS 也是照着它搬的）。
 *
 * ⚠️ 为什么不用 Apache POI 那一套：加进来能把包从 2.2MB 顶到 10MB+，
 *    而这个 App 的卖点之一就是「小、干净、不联网」。我们只要一张能打开的表，
 *    用不上公式引擎和图表。
 */
object Report {

    /** 报表文件名：约稿账本报表-2026-10-07.xlsx */
    fun fileName(at: Date = Date()): String {
        val f = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        return "${AppCtx.s(R.string.report_file_prefix)}-${f.format(at)}.xlsx"
    }

    /**
     * 把账本打成一份 .xlsx 的字节。
     *
     * ⚠️ 列表顺序**跟着传来的顺序走**，这里不排序 —— 界面上是什么顺序，
     *    导出来就什么顺序。用户在界面上排好了序，导出又给他重排一次会很烦。
     */
    fun buildXlsx(items: List<Commission>): ByteArray {
        val rows = ArrayList<List<XlsxWriter.Cell>>()

        // ① 表头
        rows.add(
            listOf(
                AppCtx.s(R.string.report_col_index),
                AppCtx.s(R.string.report_col_artist),
                AppCtx.s(R.string.report_col_title),
                AppCtx.s(R.string.report_col_total),
                AppCtx.s(R.string.report_col_paid),
                AppCtx.s(R.string.report_col_unpaid),
                AppCtx.s(R.string.report_col_status),
                AppCtx.s(R.string.report_col_date),
                AppCtx.s(R.string.report_col_deadline),
                AppCtx.s(R.string.report_col_archived),
                AppCtx.s(R.string.report_col_note),
            ).map { XlsxWriter.Cell.Text(it) }
        )

        // ② 数据
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        items.forEachIndexed { i, c ->
            rows.add(
                listOf(
                    XlsxWriter.Cell.Number((i + 1).toDouble()),
                    XlsxWriter.Cell.Text(c.artist),
                    XlsxWriter.Cell.Text(c.title),
                    XlsxWriter.Cell.Number(c.total),
                    XlsxWriter.Cell.Number(c.deposit),
                    XlsxWriter.Cell.Number(c.unpaid),
                    XlsxWriter.Cell.Text(c.status.shown),
                    XlsxWriter.Cell.Text(dayFmt.format(Date(c.dateMillis))),
                    XlsxWriter.Cell.Text(c.deadlineMillis?.let { dayFmt.format(Date(it)) } ?: ""),
                    XlsxWriter.Cell.Text(AppCtx.s(if (c.archived) R.string.common_yes else R.string.common_no)),
                    XlsxWriter.Cell.Text(c.note),
                )
            )
        }

        // ③ 空一行 + 合计四行。
        //    合计用**公式**算，不写死数字 —— 这样用户在 Excel 里删掉几行，合计会自己跟着变。
        rows.add(emptyList())
        val first = 2
        val last = items.size + 1
        fun sumCol(ref: String) = if (items.isEmpty()) "0" else "SUM($ref$first:$ref$last)"

        // ⚠️ 合计要**同时**给"公式"和"算好的数"——
        //    只给公式的话，手机上那些不算公式的预览器打开就是一片空白
        //    （2026-10-07 逸风真机反馈："总价合计那三格是空的"）。见 Cell.Formula 的注释。
        val sumTotal = items.sumOf { it.total }
        val sumPaid = items.sumOf { it.deposit }
        val sumUnpaid = items.sumOf { it.unpaid }

        rows.add(
            listOf(
                XlsxWriter.Cell.Text(AppCtx.s(R.string.report_sum_count)),
                XlsxWriter.Cell.Number(items.size.toDouble()),
            )
        )
        rows.add(
            listOf(
                XlsxWriter.Cell.Text(AppCtx.s(R.string.report_sum_total)),
                XlsxWriter.Cell.Formula(sumCol("D"), sumTotal),
            )
        )
        rows.add(
            listOf(
                XlsxWriter.Cell.Text(AppCtx.s(R.string.report_sum_paid)),
                XlsxWriter.Cell.Formula(sumCol("E"), sumPaid),
            )
        )
        rows.add(
            listOf(
                XlsxWriter.Cell.Text(AppCtx.s(R.string.report_sum_unpaid)),
                XlsxWriter.Cell.Formula(sumCol("F"), sumUnpaid),
            )
        )

        return XlsxWriter.build(AppCtx.s(R.string.report_sheet_name), rows)
    }
}
