package io.earlisreal.ejournal.domain.parser

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** A workbook read from an .xlsx file: its sheet names (in workbook order) and each sheet's rows of cell strings. */
internal class XlsxWorkbook(
    val sheetNames: List<String>,
    private val sheets: Map<String, List<List<String>>>,
) {
    fun rows(sheetName: String): List<List<String>> = sheets[sheetName] ?: emptyList()
}

/**
 * A deliberately minimal, zero-dependency reader for the slice of the OOXML (xlsx) spec eToro statements
 * use — enough to pull tabular cell strings out without dragging in Apache POI (mirroring how the CSV
 * parsers hand-roll [parseCsvLine] rather than taking a CSV library). It resolves the shared-string table,
 * returns numeric cells as their literal text, and uses each cell's `A1` reference to keep columns aligned
 * when blank cells are omitted from the XML. Styles, number formats, and date serials are out of scope —
 * eToro stores dates as text, so no serial conversion is needed.
 */
internal object Xlsx {

    /** Parses [bytes] as an xlsx. Throws if it is not a zip or lacks `xl/workbook.xml`. */
    fun read(bytes: ByteArray): XlsxWorkbook {
        val parts = unzip(bytes)
        val workbookXml = parts["xl/workbook.xml"] ?: error("not an xlsx: missing xl/workbook.xml")

        val rels = parseRels(parts["xl/_rels/workbook.xml.rels"])
        val shared = parseSharedStrings(parts["xl/sharedStrings.xml"])

        val sheetNames = mutableListOf<String>()
        val sheets = LinkedHashMap<String, List<List<String>>>()
        for (sheet in parseDoc(workbookXml).elements("sheet")) {
            val name = sheet.getAttribute("name")
            val target = rels[sheet.getAttribute("r:id")] ?: continue
            sheetNames += name
            val sheetXml = parts["xl/$target"] ?: parts[target]
            sheets[name] = if (sheetXml != null) parseSheet(sheetXml, shared) else emptyList()
        }
        return XlsxWorkbook(sheetNames, sheets)
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val parts = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) parts[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        return parts
    }

    /** Relationship id -> target path (relative to `xl/`). */
    private fun parseRels(xml: ByteArray?): Map<String, String> {
        if (xml == null) return emptyMap()
        return parseDoc(xml).elements("Relationship")
            .associate { it.getAttribute("Id") to it.getAttribute("Target").removePrefix("/") }
    }

    /** Shared-string table, indexed by position; each `<si>` is the concatenation of its `<t>` runs. */
    private fun parseSharedStrings(xml: ByteArray?): List<String> {
        if (xml == null) return emptyList()
        return parseDoc(xml).elements("si").map { si -> si.elements("t").joinToString("") { it.textContent } }
    }

    private fun parseSheet(xml: ByteArray, shared: List<String>): List<List<String>> =
        parseDoc(xml).elements("row").map { row ->
            val cells = row.elements("c")
            val byColumn = HashMap<Int, String>()
            var maxCol = -1
            for (c in cells) {
                val col = columnIndex(c.getAttribute("r"))
                val value = cellValue(c, shared)
                byColumn[col] = value
                if (col > maxCol) maxCol = col
            }
            (0..maxCol).map { byColumn[it] ?: "" }
        }

    private fun cellValue(c: Element, shared: List<String>): String = when (c.getAttribute("t")) {
        "s" -> c.elements("v").firstOrNull()?.textContent?.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
        "inlineStr" -> c.elements("is").flatMap { it.elements("t") }.joinToString("") { it.textContent }
        else -> c.elements("v").firstOrNull()?.textContent ?: "" // numeric, boolean, or formula string
    }

    /** "C5" / "AA12" -> zero-based column index (2 / 26). */
    private fun columnIndex(ref: String): Int {
        var idx = 0
        for (ch in ref) {
            if (!ch.isLetter()) break
            idx = idx * 26 + (ch.uppercaseChar() - 'A' + 1)
        }
        return idx - 1
    }

    private fun parseDoc(xml: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false // match elements/attrs by their qualified name (e.g. "r:id")
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(xml))
    }
}

/** Direct child elements with the given tag name. */
private fun Document.elements(tag: String): List<Element> = documentElement.descendants(tag)

private fun Element.elements(tag: String): List<Element> = descendants(tag)

/**
 * Descendant elements whose local name (any prefix stripped) equals [tag]. Matching on local name
 * tolerates both the default-namespace form (`<sheet>`) and the explicit-prefix form (`<x:sheet>`) that
 * .NET producers such as the one eToro uses emit — `isNamespaceAware = false` keeps the prefix in `nodeName`.
 */
private fun Element.descendants(tag: String): List<Element> {
    val nodes = getElementsByTagName("*")
    return (0 until nodes.length)
        .mapNotNull { nodes.item(it) as? Element }
        .filter { it.nodeName.substringAfterLast(':') == tag }
}
