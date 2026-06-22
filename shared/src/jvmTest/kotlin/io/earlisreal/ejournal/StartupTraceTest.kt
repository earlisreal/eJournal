package io.earlisreal.ejournal

import kotlin.test.Test
import kotlin.test.assertEquals

class StartupTraceTest {

    @Test
    fun rendersPerStepDeltaAndCumulativeTotalInMillis() {
        val out = renderTrace(listOf("a" to 0L, "b" to 5_000_000L, "c" to 12_000_000L))
        assertEquals("a +0.0ms (0.0ms)  |  b +5.0ms (5.0ms)  |  c +7.0ms (12.0ms)", out)
    }

    @Test
    fun handlesEmptyMarks() {
        assertEquals("no marks", renderTrace(emptyList()))
    }
}
