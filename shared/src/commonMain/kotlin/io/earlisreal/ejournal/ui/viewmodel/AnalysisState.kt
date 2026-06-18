package io.earlisreal.ejournal.ui.viewmodel

import io.earlisreal.ejournal.domain.marketdata.AggregatedChart
import io.earlisreal.ejournal.domain.marketdata.ChartTimeframe
import io.earlisreal.ejournal.domain.model.ClosedPosition

data class AnalysisState(
    val position: ClosedPosition? = null,
    val currentIndex: Int = 0,
    val totalCount: Int = 0,
    val activeTimeframe: ChartTimeframe = ChartTimeframe.ONE_MIN,
    val vwapEnabled: Boolean = true,
    val chartData: AggregatedChart? = null,
    val loading: Boolean = false,
    val noDataForTimeframe: Boolean = false,
    val isDarkTheme: Boolean = true,
    val has1MinData: Boolean = true,
)
