package io.earlisreal.ejournal.domain.analytics

import io.earlisreal.ejournal.domain.model.ClosedPosition

fun filterPositions(
    positions: List<ClosedPosition>,
    range: DateRange,
    segment: Segment,
): List<ClosedPosition> = positions.filter { p ->
    val exitDate = p.exitDatetime.date
    val inRange = (range.from == null || exitDate >= range.from) &&
        (range.to == null || exitDate <= range.to)
    val inSegment = when (segment) {
        Segment.ALL -> true
        Segment.DAY -> classifyTradeType(p) == TradeType.DAY
        Segment.SWING -> classifyTradeType(p) == TradeType.SWING
    }
    inRange && inSegment
}
