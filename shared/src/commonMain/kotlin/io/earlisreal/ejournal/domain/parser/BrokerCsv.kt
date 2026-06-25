package io.earlisreal.ejournal.domain.parser

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * Shared CSV-cleaning helpers used by the broker parsers. Broker exports format money with `$`,
 * thousands commas, leading `-$`, or accounting parentheses, and dates in several US/ISO forms — these
 * normalize them. All are pure and null-safe: a value they cannot parse returns null so the caller can
 * skip or default it rather than throwing.
 */

private val PLACEHOLDERS = setOf("", "--", "n/a", "processing")

/** Parses a money cell to a Double. Strips `$`/commas, treats `(x)` as `-x`. Null for blanks/placeholders/non-numeric. */
internal fun cleanMoney(raw: String?): Double? {
    if (raw == null) return null
    var s = raw.trim()
    if (s.lowercase() in PLACEHOLDERS) return null
    val negative = s.startsWith("(") && s.endsWith(")")
    if (negative) s = s.substring(1, s.length - 1)
    s = s.replace("$", "").replace(",", "").trim()
    if (s.isEmpty()) return null
    val v = s.toDoubleOrNull() ?: return null
    return if (negative) -v else v
}

/** Parses a US (`MM/DD/YYYY`, `MM/DD/YY`, `M/D/YYYY`) or ISO (`YYYY-MM-DD`) date; takes the first date of an "x as of y" cell. */
internal fun parseUsDate(raw: String): LocalDate? =
    isoDate(raw)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

/** Parses a date with optional time + trailing timezone token (e.g. "12/23/2021 09:53:38 EST"); date-only defaults to midnight. */
internal fun parseUsDateTime(raw: String): LocalDateTime? {
    var s = raw.trim()
    if (s.isEmpty()) return null
    val asOf = s.indexOf(" as of ")
    if (asOf >= 0) s = s.substring(0, asOf).trim()
    val tokens = s.split(" ").filter { it.isNotBlank() }
    val isoD = isoDate(tokens.getOrElse(0) { "" }) ?: return null
    val isoT = tokens.getOrNull(1)?.let { normalizeTime(it) ?: return null } ?: "00:00:00"
    return runCatching { LocalDateTime.parse("${isoD}T$isoT") }.getOrNull()
}

/** Normalizes a date cell to an ISO `YYYY-MM-DD` string, or null. */
private fun isoDate(raw: String): String? {
    var s = raw.trim()
    if (s.isEmpty()) return null
    val asOf = s.indexOf(" as of ")
    if (asOf >= 0) s = s.substring(0, asOf).trim()
    if (Regex("""\d{4}-\d{2}-\d{2}""").matches(s)) return s
    val p = s.split("/")
    if (p.size != 3) return null
    val mm = p[0].trim().toIntOrNull() ?: return null
    val dd = p[1].trim().toIntOrNull() ?: return null
    val yRaw = p[2].trim()
    val yi = yRaw.toIntOrNull() ?: return null
    if (mm !in 1..12 || dd !in 1..31) return null
    val yyyy = if (yRaw.length <= 2) 2000 + yi else yi
    return "${yyyy.toString().padStart(4, '0')}-${mm.toString().padStart(2, '0')}-${dd.toString().padStart(2, '0')}"
}

/** Normalizes "HH", "HH:MM", or "HH:MM:SS" to "HH:MM:SS", or null. */
private fun normalizeTime(t: String): String? {
    val p = t.split(":")
    val hh = p.getOrNull(0)?.toIntOrNull() ?: return null
    val mi = p.getOrNull(1)?.toIntOrNull() ?: 0
    val ss = p.getOrNull(2)?.toIntOrNull() ?: 0
    if (hh !in 0..23 || mi !in 0..59 || ss !in 0..59) return null
    return "${hh.toString().padStart(2, '0')}:${mi.toString().padStart(2, '0')}:${ss.toString().padStart(2, '0')}"
}

/** A located header row plus the data lines that follow it. */
data class HeaderLocation(
    val columns: List<String>,
    /** normalized (trimmed, lowercased) column name -> index */
    val index: Map<String, Int>,
    /** raw lines after the header (blank lines NOT removed — callers skip them) */
    val dataLines: List<String>,
)

/**
 * Decodes [content] (stripping a UTF-8 BOM and tolerating CRLF), finds the first non-blank line for which
 * [isHeader] is true, and returns its parsed columns + a name->index map + the lines after it. Used by both
 * `detect()` (non-null result == recognized) and `parse()`. Returns null if no header matches.
 */
internal fun locateHeader(content: ByteArray, isHeader: (String) -> Boolean): HeaderLocation? {
    val text = content.decodeToString().removePrefix("﻿")
    val lines = text.split("\n").map { it.removeSuffix("\r") }
    val headerIdx = lines.indexOfFirst { it.isNotBlank() && isHeader(it) }
    if (headerIdx < 0) return null
    val columns = parseCsvLine(lines[headerIdx]).map { it.trim() }
    val index = columns.withIndex().associate { (i, name) -> name.trim().lowercase() to i }
    return HeaderLocation(columns, index, lines.drop(headerIdx + 1))
}

/** Reads this row's cell for column [name] (case-insensitive), trimmed; null if the column or cell is absent. */
internal fun List<String>.field(index: Map<String, Int>, name: String): String? =
    index[name.trim().lowercase()]?.let { getOrNull(it)?.trim() }
