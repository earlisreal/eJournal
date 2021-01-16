package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.model.TradeSummary;
import javafx.scene.chart.XYChart;

import java.util.List;

public interface AnalyticsService {

    void initialize();

    double getTotalEquity();

    double getTotalProfit();

    double getTotalProfitPercentage();

    double getEdgeRatio();

    double getAverageProfit();

    double getAverageLoss();

    double getAverageProfitPercentage();

    double getAverageLossPercentage();

    double getAccuracy();

    double getProfitFactor();

    double getAverageHoldingDays();

    List<XYChart.Data<String, Double>> getEquityData();

    List<XYChart.Data<Double, String>> getTopWinners();

    List<XYChart.Data<Double, String>> getTopLosers();

    List<TradeSummary> getSummaries();

    List<TradeSummary> getLosses();

    List<TradeSummary> getWins();

}
