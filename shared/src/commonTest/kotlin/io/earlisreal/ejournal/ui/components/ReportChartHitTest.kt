package io.earlisreal.ejournal.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReportChartHitTest {
    @Test fun alignedSlotsUseLowerInclusiveBoundaries() {
        assertEquals(0, reportBucketIndex(0f, 480f, 48))
        assertEquals(0, reportBucketIndex(9.99f, 480f, 48))
        assertEquals(1, reportBucketIndex(10f, 480f, 48))
        assertEquals(47, reportBucketIndex(479.99f, 480f, 48))
    }

    @Test fun axesAndEmptyDomainsCannotSelectABucket() {
        assertNull(reportBucketIndex(-1f, 480f, 48))
        assertNull(reportBucketIndex(480f, 480f, 48))
        assertNull(reportBucketIndex(0f, 0f, 48))
        assertNull(reportBucketIndex(0f, 480f, 0))
    }
}
