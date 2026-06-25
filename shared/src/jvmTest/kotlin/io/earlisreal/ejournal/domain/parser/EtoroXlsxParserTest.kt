package io.earlisreal.ejournal.domain.parser

import io.earlisreal.ejournal.domain.model.Action
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the JVM XLSX-reading layer and its integration with the eToro semantics, using a synthetic
 * workbook built in-test (so no personal statement is committed). The pure mapping logic is tested
 * exhaustively in commonTest's EtoroStatementTest.
 */
class EtoroXlsxParserTest {

    private val parser = EtoroXlsxParser()
    private val portfolioId = 9L

    private val activityHeader = listOf(
        "Date", "Type", "Details", "Amount", "Units / Contracts",
        "Realized Equity Change", "Realized Equity", "Balance", "Position ID", "Asset type", "NWA",
    )

    private fun etoroBytes(activityRows: List<List<String>>): ByteArray = buildXlsx(
        listOf(
            "Account Summary" to listOf(listOf("Account Summary (USD)", "Total (USD)")),
            "Closed Positions" to listOf(listOf("Position ID", "Action")),
            "Account Activity" to (listOf(activityHeader) + activityRows),
            "Financial Summary" to listOf(listOf("Item", "Amount in (USD)")),
        )
    )

    // --- reader: sheet names + cell values (shared strings, numerics, and a middle gap) ---

    @Test
    fun readsSheetNamesInWorkbookOrder() {
        val wb = Xlsx.read(etoroBytes(emptyList()))
        assertEquals(listOf("Account Summary", "Closed Positions", "Account Activity", "Financial Summary"), wb.sheetNames)
    }

    @Test
    fun readsCellsResolvingSharedStringsAndNumerics() {
        val bytes = buildXlsx(listOf("S" to listOf(listOf("Open Position", "1000.00", "5"))))
        val row = Xlsx.read(bytes).rows("S").single()
        assertEquals("Open Position", row[0]) // shared string
        assertEquals("1000", row[1])           // numeric, canonicalized
        assertEquals("5", row[2])
    }

    @Test
    fun keepsColumnAlignmentWhenAMiddleCellIsEmpty() {
        // Empty cells are omitted from the XML; the reader must re-align via each cell's A1 reference.
        val bytes = buildXlsx(listOf("S" to listOf(listOf("a", "", "c"))))
        val row = Xlsx.read(bytes).rows("S").single()
        assertEquals(listOf("a", "", "c"), row)
    }

    // --- detection ---

    @Test
    fun detectsEtoroWorkbook() {
        assertTrue(parser.detect(etoroBytes(emptyList())))
    }

    @Test
    fun doesNotDetectCsvBytes() {
        val ibkrCsv = "Trades,Header,DataDiscriminator,Asset Category,Currency,Symbol".encodeToByteArray()
        assertFalse(parser.detect(ibkrCsv))
    }

    @Test
    fun doesNotDetectNonEtoroWorkbook() {
        val other = buildXlsx(listOf("Sheet1" to listOf(listOf("a", "b"))))
        assertFalse(parser.detect(other))
    }

    // --- metadata ---

    @Test
    fun exposesBrokerMetadata() {
        assertEquals("eToro", parser.brokerName)
        assertEquals(listOf("xlsx"), parser.supportedExtensions)
    }

    // --- end-to-end parse through the reader ---

    @Test
    fun parsesActivitySheetEndToEnd() {
        val bytes = etoroBytes(
            listOf(
                listOf("12/02/2021 02:44:21", "Deposit", "50000.00 USDPHP", "1034.02", "-", "", "", "", "-", "-", "0"),
                listOf("25/03/2021 16:41:08", "Open Position", "AAPL/USD", "1000.00", "5", "", "", "", "1", "Stocks", "0"),
                listOf("22/04/2021 17:19:58", "Position closed", "AAPL/USD", "1100.00", "5", "", "", "", "1", "Stocks", "0"),
            )
        )
        val result = parser.parse(bytes, portfolioId)
        assertEquals(2, result.transactions.size)
        assertEquals(1, result.skipped.nonTrade) // the deposit

        val buy = result.transactions.first { it.action == Action.BUY }
        assertEquals("AAPL", buy.symbol)
        assertEquals(200.0, buy.price)
        assertEquals(5.0, buy.shares)
        assertEquals(0.0, buy.fees)

        val sell = result.transactions.first { it.action == Action.SELL }
        assertEquals(220.0, sell.price)
        assertEquals("etoro:AAPL:2021-04-22T17:19:58:SELL:5.0#0", sell.externalId)
    }

    @Test
    fun parseReturnsEmptyForNonXlsxBytes() {
        val result = parser.parse("not a zip".encodeToByteArray(), portfolioId)
        assertEquals(0, result.transactions.size)
    }

    // --- minimal OOXML workbook builder -------------------------------------------------------------

    private fun buildXlsx(sheets: List<Pair<String, List<List<String>>>>): ByteArray {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        fun isNum(s: String) = s.isNotBlank() && s.toDoubleOrNull() != null
        fun colRef(i: Int): String {
            var n = i + 1
            val sb = StringBuilder()
            while (n > 0) { val r = (n - 1) % 26; sb.insert(0, 'A' + r); n = (n - 1) / 26 }
            return sb.toString()
        }

        // Shared-string table: every non-numeric, non-empty cell value, de-duplicated, insertion-ordered.
        val shared = LinkedHashMap<String, Int>()
        for ((_, rows) in sheets) for (row in rows) for (v in row) {
            if (v.isNotEmpty() && !isNum(v)) shared.getOrPut(v) { shared.size }
        }

        val ns = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        val rns = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        val pns = "http://schemas.openxmlformats.org/package/2006/relationships"

        fun sheetXml(rows: List<List<String>>): String {
            val sb = StringBuilder("""<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="$ns"><sheetData>""")
            rows.forEachIndexed { ri, row ->
                sb.append("""<row r="${ri + 1}">""")
                row.forEachIndexed { ci, v ->
                    if (v.isEmpty()) return@forEachIndexed // omit empty cells, exercising gap re-alignment
                    val ref = "${colRef(ci)}${ri + 1}"
                    if (isNum(v)) {
                        sb.append("""<c r="$ref"><v>${v.toDouble().let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }}</v></c>""")
                    } else {
                        sb.append("""<c r="$ref" t="s"><v>${shared[v]}</v></c>""")
                    }
                }
                sb.append("</row>")
            }
            sb.append("</sheetData></worksheet>")
            return sb.toString()
        }

        val workbookSheets = sheets.mapIndexed { i, (name, _) ->
            """<sheet name="${esc(name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>"""
        }.joinToString("")
        val workbookXml =
            """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="$ns" xmlns:r="$rns"><sheets>$workbookSheets</sheets></workbook>"""

        val rels = StringBuilder("""<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="$pns">""")
        sheets.forEachIndexed { i, _ ->
            rels.append("""<Relationship Id="rId${i + 1}" Type="$rns/worksheet" Target="worksheets/sheet${i + 1}.xml"/>""")
        }
        rels.append("""<Relationship Id="rIdSS" Type="$rns/sharedStrings" Target="sharedStrings.xml"/></Relationships>""")

        val ssXml = StringBuilder(
            """<?xml version="1.0" encoding="UTF-8"?><sst xmlns="$ns" count="${shared.size}" uniqueCount="${shared.size}">"""
        )
        shared.keys.forEach { ssXml.append("<si><t xml:space=\"preserve\">${esc(it)}</t></si>") }
        ssXml.append("</sst>")

        val entries = LinkedHashMap<String, String>()
        entries["xl/workbook.xml"] = workbookXml
        entries["xl/_rels/workbook.xml.rels"] = rels.toString()
        entries["xl/sharedStrings.xml"] = ssXml.toString()
        sheets.forEachIndexed { i, (_, rows) -> entries["xl/worksheets/sheet${i + 1}.xml"] = sheetXml(rows) }

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((path, content) in entries) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
