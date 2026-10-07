package com.yifeng.commissionbook

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

/*
 * 极简 .xlsx 写出器（2026-10-07 加，导出 Excel 报表用）。
 *
 * ⚠️ **这个文件里不许出现任何 Android 的东西**（`Context` / `R` / `AppCtx` 都不行）——
 *    原因有两个，都很实在：
 *    ① 它得能被单独编译、单独跑一遍验证（报表最怕的就是"看着像那么回事、用 Excel 一开就报错"）；
 *    ② iOS 那边是**照着它重写的一模一样的逻辑**，纯逻辑才好对照着搬。
 *    所以文案（表头、合计那几行）全部由调用方 [Report] 传进来，这里只管「把格子拼成文件」。
 *
 * .xlsx 就是一个 ZIP，里面几份 XML：
 *   [Content_Types].xml / _rels/.rels / xl/workbook.xml / xl/_rels/workbook.xml.rels
 *   / xl/worksheets/sheet1.xml
 *
 * ⚠️ 用 **STORE（不压缩）**：要写的东西就几十 KB，省掉压缩能少一大截代码，
 *    也不用担心两个平台的 deflate 实现不一致。
 * ⚠️ 字符串走 **inlineStr**（`<is><t>`），不用 sharedStrings 那张表 ——
 *    少一个部件、少一处可能对不上的索引。
 * ⚠️ 数字写成**真数字**（`<v>`），别写成文本，不然在 Excel 里没法求和。
 */
object XlsxWriter {

    /** 一个格子。就三种，够用了。 */
    sealed interface Cell {
        /** 文本（走 inlineStr） */
        data class Text(val v: String) : Cell
        /** 数字（写成真数字） */
        data class Number(val v: Double) : Cell
        /**
         * 公式（比如 `SUM(D2:D9)`）。
         *
         * ⚠️ **`cached` 一定要给**（2026-10-07 逸风真机反馈后加的）。
         *    不带的话：文件里只有一个公式、没有结果值，Excel 自己会算、
         *    可**手机上那些看图/看表的预览器不算** —— 打开就是一片空白格子，
         *    用户看到的是"合计没导出来"。
         *    写上 `cached` 就等于"顺手把算好的结果也存一份"：
         *    会算的地方（Excel/WPS）照旧按公式算，不会算的地方至少显示得出数字。
         */
        data class Formula(val v: String, val cached: Double? = null) : Cell
    }

    /**
     * 拼出一份 .xlsx 的字节。
     *
     * @param sheetName 工作表名字（Excel 规定 ≤31 字，且不能含 `[ ] : * ? / \`）
     * @param rows 一行一个 List；传空 List 就是空行
     */
    fun build(sheetName: String, rows: List<List<Cell>>): ByteArray {
        val zip = ZipBuilder()
        zip.add("[Content_Types].xml", CONTENT_TYPES.toByteArray(Charsets.UTF_8))
        zip.add("_rels/.rels", RELS.toByteArray(Charsets.UTF_8))
        zip.add("xl/workbook.xml", workbookXml(sheetName).toByteArray(Charsets.UTF_8))
        zip.add("xl/_rels/workbook.xml.rels", WORKBOOK_RELS.toByteArray(Charsets.UTF_8))
        zip.add("xl/worksheets/sheet1.xml", sheetXml(rows).toByteArray(Charsets.UTF_8))
        return zip.finish()
    }

    private fun escape(s: String): String = buildString(s.length + 8) {
        s.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                // ⚠️ 换行/制表符在 XML 里会被折叠，转成空格最省事：
                //    备注里带换行的话，不处理会变成一坨挨在一起的字
                '\n', '\r', '\t' -> append(' ')
                else -> if (ch.code < 0x20) append(' ') else append(ch)
            }
        }
    }

    /** 0 → A，25 → Z，26 → AA */
    private fun colName(index: Int): String {
        var n = index
        val sb = StringBuilder()
        while (true) {
            sb.insert(0, ('A' + n % 26))
            n = n / 26 - 1
            if (n < 0) break
        }
        return sb.toString()
    }

    private fun sheetXml(rows: List<List<Cell>>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        // ⚠️ `<dimension>` 一定要给（2026-10-07 加）：它写着"这张表用到了哪一块"。
        //    手机上的预览器（那种不带公式引擎的看图/看表 App）会**照它**决定画多大一片网格 ——
        //    少了这一句，有的预览器干脆当空表，一个格子都不显示。
        //    位置有讲究：必须在 `<cols>` **前面**（schema 里 dimension 排第一）。
        val maxCols = rows.maxOfOrNull { it.size } ?: 0
        val dim = if (maxCols == 0 || rows.isEmpty()) "A1" else "A1:${colName(maxCols - 1)}${rows.size}"
        append("<dimension ref=\"$dim\"/>")
        // 列宽：内容/备注宽一点，金额那几列窄一点，打开就有个能看的样子
        append("<cols>")
        append("<col min=\"1\" max=\"1\" width=\"6\"/>")
        append("<col min=\"2\" max=\"3\" width=\"18\"/>")
        append("<col min=\"4\" max=\"6\" width=\"12\"/>")
        append("<col min=\"7\" max=\"7\" width=\"10\"/>")
        append("<col min=\"8\" max=\"9\" width=\"13\"/>")
        append("<col min=\"10\" max=\"10\" width=\"8\"/>")
        append("<col min=\"11\" max=\"11\" width=\"26\"/>")
        append("</cols>")
        append("<sheetData>")

        rows.forEachIndexed { r, cells ->
            if (cells.isEmpty()) {
                append("<row r=\"${r + 1}\"/>")
                return@forEachIndexed
            }
            append("<row r=\"${r + 1}\">")
            cells.forEachIndexed { c, cell ->
                val ref = "${colName(c)}${r + 1}"
                when (cell) {
                    is Cell.Text ->
                        append("<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                            .append(escape(cell.v))
                            .append("</t></is></c>")

                    is Cell.Number ->
                        append("<c r=\"$ref\"><v>${trimNumber(cell.v)}</v></c>")

                    is Cell.Formula -> {
                        append("<c r=\"$ref\"><f>${escape(cell.v)}</f>")
                        // 顺手把算好的结果也存一份 —— 不算公式的预览器（手机上的看图/看表 App）靠它
                        cell.cached?.let { append("<v>${trimNumber(it)}</v>") }
                        append("</c>")
                    }
                }
            }
            append("</row>")
        }

        append("</sheetData></worksheet>")
    }

    /** 金额写成 `12` 而不是 `12.0`（好看，也不至于在 Excel 里显示成浮点尾巴） */
    private fun trimNumber(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

    private fun workbookXml(sheetName: String): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"" +
            " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
            "<sheets><sheet name=\"${escape(sheetName)}\" sheetId=\"1\" r:id=\"rId1\"/></sheets>" +
            // ⚠️ 逼 Excel/WPS 打开时重算一遍：我们写的缓存值只是给"不会算的预览器"兜底，
            //    真算过的地方还是让它按公式来 —— 用户在 Excel 里删几行，合计得跟着变。
            "<calcPr calcId=\"0\" fullCalcOnLoad=\"1\"/>" +
            "</workbook>"

    private const val CONTENT_TYPES =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
            "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
            "</Types>"

    private const val RELS =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
            "</Relationships>"

    private const val WORKBOOK_RELS =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
            "</Relationships>"
}

/**
 * 极简 ZIP 打包器（**只支持 STORE，不压缩**）。
 *
 * ⚠️ 时间戳写死成 1980-01-01（DOS 那套的 0 值）：反正是给人看的报表，
 *    用当前时间还得处理时区，不值当。
 * ⚠️ 那两个 `write*` 是**自己往流里写**的（不返回值）——
 *    写成 `out.write(short(20))` 那种套娃会因为返回 `Unit` 而编译不过，踩过。
 */
private class ZipBuilder {

    private class Entry(val name: String, val data: ByteArray, val crc: Long, val offset: Int)

    private val out = ByteArrayOutputStream()
    private val entries = ArrayList<Entry>()

    fun add(name: String, data: ByteArray) {
        val crc = CRC32().apply { update(data) }.value
        val nameBytes = name.toByteArray(Charsets.UTF_8)

        entries.add(Entry(name, data, crc, out.size()))

        writeInt(0x04034b50)        // local file header
        writeShort(20)              // version needed
        writeShort(0)               // flags
        writeShort(0)               // method = 0（store）
        writeShort(0)               // mod time
        writeShort(0x21)            // mod date
        writeInt(crc.toInt())
        writeInt(data.size)         // compressed size
        writeInt(data.size)         // uncompressed size
        writeShort(nameBytes.size)
        writeShort(0)               // extra
        out.write(nameBytes)
        out.write(data)
    }

    fun finish(): ByteArray {
        val cdStart = out.size()

        entries.forEach { e ->
            val nameBytes = e.name.toByteArray(Charsets.UTF_8)
            writeInt(0x02014b50)    // central directory header
            writeShort(20)          // version made by
            writeShort(20)          // version needed
            writeShort(0)           // flags
            writeShort(0)           // method
            writeShort(0)           // mod time
            writeShort(0x21)        // mod date
            writeInt(e.crc.toInt())
            writeInt(e.data.size)
            writeInt(e.data.size)
            writeShort(nameBytes.size)
            writeShort(0)           // extra
            writeShort(0)           // comment
            writeShort(0)           // disk
            writeShort(0)           // internal attrs
            writeInt(0)             // external attrs
            writeInt(e.offset)
            out.write(nameBytes)
        }

        val cdSize = out.size() - cdStart
        writeInt(0x06054b50)        // end of central directory
        writeShort(0)
        writeShort(0)
        writeShort(entries.size)
        writeShort(entries.size)
        writeInt(cdSize)
        writeInt(cdStart)
        writeShort(0)               // comment length

        return out.toByteArray()
    }

    private fun writeShort(v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
    }

    private fun writeInt(v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
        out.write((v shr 16) and 0xFF)
        out.write((v shr 24) and 0xFF)
    }
}
