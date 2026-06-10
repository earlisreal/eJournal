package io.earlisreal.ejournal.domain.analytics

import io.earlisreal.ejournal.domain.model.ClosedPosition

enum class TradeType { DAY, SWING }

fun classifyTradeType(position: ClosedPosition): TradeType =
    if (position.entryDatetime.date == position.exitDatetime.date) TradeType.DAY else TradeType.SWING
