package io.earlisreal.ejournal.domain.parser

import kotlin.test.Test
import kotlin.test.assertEquals

class CsvTest {

    @Test
    fun splitsSimpleUnquotedFields() {
        assertEquals(listOf("a", "b", "c"), parseCsvLine("a,b,c"))
    }

    @Test
    fun keepsCommasInsideQuotedFields() {
        assertEquals(listOf("a,b", "c"), parseCsvLine("\"a,b\",c"))
    }

    @Test
    fun stripsSurroundingQuotes() {
        assertEquals(listOf("Buy", "CAKE", "Cheesecake Factory"), parseCsvLine("\"Buy\",\"CAKE\",\"Cheesecake Factory\""))
    }

    @Test
    fun keepsThousandsSeparatorInsideQuotedNumber() {
        assertEquals(
            listOf("Buy", "CAKE", "76.80", "15", "1,152.00"),
            parseCsvLine("\"Buy\",\"CAKE\",\"76.80\",\"15\",\"1,152.00\""),
        )
    }

    @Test
    fun preservesEmptyFields() {
        assertEquals(listOf("a", "", "c"), parseCsvLine("a,,c"))
        assertEquals(listOf("", "", ""), parseCsvLine("\"\",\"\",\"\""))
    }

    @Test
    fun preservesTrailingEmptyField() {
        assertEquals(listOf("a", "b", ""), parseCsvLine("a,b,"))
    }
}
