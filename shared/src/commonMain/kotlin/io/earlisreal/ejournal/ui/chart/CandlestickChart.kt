package io.earlisreal.ejournal.ui.chart

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.earlisreal.ejournal.ui.viewmodel.AnalysisState

@Composable
expect fun CandlestickChart(state: AnalysisState, modifier: Modifier = Modifier)
