package io.earlisreal.ejournal.domain.parser

/**
 * Splits one CSV line into fields, honoring `"`-quoted fields with embedded commas
 * (e.g. moomoo's `"1,152.00"`) and `""` escaped quotes. Surrounding quotes are stripped.
 * A plain `split(",")` can't handle broker exports, so both CSV parsers use this.
 */
internal fun parseCsvLine(line: String): List<String> {
    val fields = mutableListOf<String>()
    val sb = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                sb.append('"'); i++ // escaped quote inside a quoted field
            }
            c == '"' -> inQuotes = !inQuotes
            c == ',' && !inQuotes -> { fields.add(sb.toString()); sb.clear() }
            else -> sb.append(c)
        }
        i++
    }
    fields.add(sb.toString())
    return fields
}
