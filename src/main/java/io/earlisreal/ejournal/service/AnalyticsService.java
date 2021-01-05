package io.earlisreal.ejournal.service;

import javafx.scene.chart.XYChart;

import java.time.LocalDate;
import java.util.List;

public interface AnalyticsService {

    void initialize();

    void initialize(LocalDate startDate, LocalDate endDate);

    double getEdgeRatio();

    double getAverageProfit();

    double getAverageLoss();

    double getAverageProfitPercentage();

    double getAverageLossPercentage();

    double getAccuracy();

    double getProfitFactor();

    double getAverageHoldingDays();

    List<XYChart.Data<String, Double>> getEquityData();

}
