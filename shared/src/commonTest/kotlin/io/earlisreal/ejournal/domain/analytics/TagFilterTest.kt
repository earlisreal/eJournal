package io.earlisreal.ejournal.domain.analytics

import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.domain.model.Tag
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class TagFilterTest {

    private val a = Tag(1, "A", "#111111")
    private val b = Tag(2, "B", "#222222")

    private fun pos(symbol: String, tags: List<Tag>) = ClosedPosition(
        symbol = symbol,
        entryDatetime = LocalDateTime.parse("2024-03-01T09:00"),
        exitDatetime = LocalDateTime.parse("2024-03-01T15:00"),
        averageEntryPrice = 10.0, averageExitPrice = 10.0,
        shares = 100.0, fees = 0.0, profitLoss = 1.0,
        tags = tags,
    )

    private val onlyA = pos("onlyA", listOf(a))
    private val onlyB = pos("onlyB", listOf(b))
    private val bothAB = pos("bothAB", listOf(a, b))
    private val none = pos("none", emptyList())
    private val all = listOf(onlyA, onlyB, bothAB, none)

    @Test
    fun emptySelectionReturnsAllUnfiltered() {
        assertEquals(all, filterByTags(all, emptySet(), TagMatch.ANY))
        assertEquals(all, filterByTags(all, emptySet(), TagMatch.ALL))
    }

    @Test
    fun anyMatchKeepsPositionsWithAtLeastOneSelectedTag() {
        assertEquals(listOf(onlyA, bothAB), filterByTags(all, setOf(a.id), TagMatch.ANY))
        assertEquals(listOf(onlyA, onlyB, bothAB), filterByTags(all, setOf(a.id, b.id), TagMatch.ANY))
    }

    @Test
    fun allMatchKeepsOnlyPositionsWithEverySelectedTag() {
        assertEquals(listOf(bothAB), filterByTags(all, setOf(a.id, b.id), TagMatch.ALL))
        assertEquals(listOf(onlyA, bothAB), filterByTags(all, setOf(a.id), TagMatch.ALL))
    }

    @Test
    fun untaggedExcludedWhenSelectionNonEmpty() {
        assertEquals(emptyList<ClosedPosition>(), filterByTags(listOf(none), setOf(a.id), TagMatch.ANY))
    }
}
