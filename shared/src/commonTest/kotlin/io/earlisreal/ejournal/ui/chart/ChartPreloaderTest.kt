package io.earlisreal.ejournal.ui.chart

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChartPreloaderTest {

    @Test
    fun warmBuildsOnceViaDispatcher() {
        var builds = 0
        val p = ChartPreloader(create = { builds++; "bridge" }, onUiThread = { it() })
        p.warm()
        p.warm()
        assertEquals(1, builds)
    }

    @Test
    fun takeReturnsInstanceOnceThenNull() {
        val p = ChartPreloader(create = { "bridge" }, onUiThread = { it() })
        p.warm()
        assertEquals("bridge", p.take())
        assertNull(p.take())
    }

    @Test
    fun takeBeforeWarmReturnsNull() {
        val p = ChartPreloader(create = { "bridge" }, onUiThread = { it() })
        assertNull(p.take())
    }

    @Test
    fun warmAfterTakeDoesNotRebuild() {
        var builds = 0
        val p = ChartPreloader(create = { builds++; "bridge" }, onUiThread = { it() })
        p.warm()
        p.take()
        p.warm()
        assertEquals(1, builds)
    }
}
