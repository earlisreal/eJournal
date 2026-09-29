package io.earlisreal.ejournal.data.repository

import io.earlisreal.ejournal.domain.analytics.DateRangePreset
import io.earlisreal.ejournal.domain.analytics.Segment
import io.earlisreal.ejournal.domain.analytics.TagMatch
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class PortfolioFilterSettingsTest {

    @Test
    fun snapshotRoundTrips() {
        val expected = PortfolioFilterPrefs(
            preset = DateRangePreset.CUSTOM,
            customFrom = LocalDate(2024, 1, 5),
            customTo = LocalDate(2024, 2, 6),
            segment = Segment.DAY,
            selectedTagIds = setOf(3L, 5L),
            tagMatch = TagMatch.ALL,
        )

        assertEquals(expected, decodePortfolioFilterPrefs(encodePortfolioFilterPrefs(expected)))
    }

    @Test
    fun corruptFieldsRecoverIndependentlyAndUnknownFieldsAreIgnored() {
        val filters = decodePortfolioFilterPrefs(
            """{"preset":"CUSTOM","customFrom":"bad","customTo":"2024-02-06","segment":"BROKEN","selectedTagIds":[3,3,-5,"4",1.5],"tagMatch":"BROKEN","future":true}""",
        )

        assertEquals(DateRangePreset.ALL_TIME, filters.preset)
        assertEquals(null, filters.customFrom)
        assertEquals(null, filters.customTo)
        assertEquals(Segment.ALL, filters.segment)
        assertEquals(setOf(3L), filters.selectedTagIds)
        assertEquals(TagMatch.ANY, filters.tagMatch)
        assertEquals(PortfolioFilterPrefs(), decodePortfolioFilterPrefs("not-json"))
    }

    @Test
    fun reversedCustomDatesFallBackToAllTime() {
        val filters = decodePortfolioFilterPrefs(
            """{"preset":"CUSTOM","customFrom":"2024-02-06","customTo":"2024-01-05"}""",
        )

        assertEquals(DateRangePreset.ALL_TIME, filters.preset)
        assertEquals(null, filters.customFrom)
        assertEquals(null, filters.customTo)
    }
}
