package io.earlisreal.ejournal.data.database

import app.cash.sqldelight.ColumnAdapter
import io.earlisreal.ejournal.domain.marketdata.Timeframe
import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.Market
import kotlinx.datetime.LocalDateTime

object DateTimeAdapter : ColumnAdapter<LocalDateTime, String> {
    override fun decode(databaseValue: String): LocalDateTime = LocalDateTime.parse(databaseValue)
    override fun encode(value: LocalDateTime): String = value.toString()
}

object ActionAdapter : ColumnAdapter<Action, String> {
    override fun decode(databaseValue: String): Action = Action.valueOf(databaseValue)
    override fun encode(value: Action): String = value.name
}

object MarketAdapter : ColumnAdapter<Market, String> {
    override fun decode(databaseValue: String): Market = Market.valueOf(databaseValue)
    override fun encode(value: Market): String = value.name
}

object TimeframeAdapter : ColumnAdapter<Timeframe, String> {
    override fun decode(databaseValue: String): Timeframe = Timeframe.valueOf(databaseValue)
    override fun encode(value: Timeframe): String = value.name
}
