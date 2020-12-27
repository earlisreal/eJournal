package io.earlisreal.ejournal.service;

public interface AnalyticsService {

    double getEdgeRatio();

    double getAverageProfit();

    double getAverageLoss();

    double getAverageProfitPercentage();

    double getAverageLossPercentage();

    double getAccuracy();

    double getProfitFactor();

    double getAverageHoldingDays();

}
