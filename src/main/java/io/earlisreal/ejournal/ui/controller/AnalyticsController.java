package io.earlisreal.ejournal.ui.controller;

import io.earlisreal.ejournal.model.TradeSummary;
import io.earlisreal.ejournal.service.AnalyticsService;
import io.earlisreal.ejournal.service.ServiceProvider;
import io.earlisreal.ejournal.ui.service.UIServiceProvider;
import javafx.collections.FXCollections;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

import static io.earlisreal.ejournal.util.CommonUtil.prettify;

public class AnalyticsController {

    public LineChart<String, Double> equityChart;
    public BarChart<Double, String> topWinners;
    public BarChart<Double, String> topLosers;
    public VBox successProfit;
    public VBox failLoss;
    public VBox successPercent;
    public VBox failPercent;
    public BarChart<String, Double> monthlyBarChart;

    private final AnalyticsService service;

    public AnalyticsController() {
        service = ServiceProvider.getAnalyticsService();
    }

    public void reload() {
        initializeEquityChart();
        initializeTopLoser();
        initializeTopWinner();
        initializeRemarkableTrades();
        initializeMonthlyChart();
    }

    private void initializeRemarkableTrades() {
        double maxProfit = 0;
        double maxProfitPercent = 0;
        double minProfit = 0;
        double minProfitPercent = 0;

        TradeSummary bestTradeProfit = null;
        TradeSummary bestTradePercent = null;
        TradeSummary worstTradeProfit = null;
        TradeSummary worstTradePercentage = null;
        for (TradeSummary summary : service.getWins()) {
            double profit = summary.getProfit();
            double percent = summary.getProfitPercentage();
            if (profit > maxProfit) {
                maxProfit = profit;
                bestTradeProfit = summary;
            }

            if (percent > maxProfitPercent) {
                maxProfitPercent = percent;
                bestTradePercent = summary;
            }
        }

        for (TradeSummary summary : service.getLosses()) {
            double lossPercent = summary.getProfitPercentage();
            double loss = summary.getProfit();
            if (lossPercent < minProfitPercent) {
                worstTradePercentage = summary;
                minProfitPercent = lossPercent;
            }

            if (loss < minProfit) {
                worstTradeProfit = summary;
                minProfit = loss;
            }
        }

        List<TradeSummary> summaries = new ArrayList<>();
        if (bestTradeProfit != null) summaries.add(bestTradeProfit);
        if (worstTradeProfit != null) summaries.add(worstTradeProfit);
        if (bestTradePercent != null) summaries.add(bestTradePercent);
        if (worstTradePercentage != null) summaries.add(worstTradePercentage);
        setTrade(successProfit, bestTradeProfit, summaries);
        setTrade(failLoss, worstTradeProfit, summaries);
        setTrade(successPercent, bestTradePercent, summaries);
        setTrade(failPercent, worstTradePercentage, summaries);
    }

    private void setTrade(VBox vBox, TradeSummary summary, List<TradeSummary> summaries) {
        Label stock = (Label) vBox.getChildren().get(0);
        Label percent = (Label) vBox.getChildren().get(1);
        Label value = (Label) vBox.getChildren().get(2);
        if (summary == null) {
            stock.setText("N/A");
            percent.setText("");
            value.setText("");
            vBox.setOnMouseClicked(null);
            return;
        }

        stock.setText(summary.getStock());
        percent.setText(prettify(summary.getProfitPercentage()) + "%");
        value.setText(prettify(summary.getProfit()));

        vBox.setOnMouseClicked(unused -> UIServiceProvider.getTradeDetailsDialogService().show(summary, summaries));
    }

    private void initializeEquityChart() {
        XYChart.Series<String, Double> series = new XYChart.Series<>();
        series.setName("Original Portfolio");
        series.setData(FXCollections.observableList(service.getEquityData()));
        equityChart.setData(FXCollections.observableList(List.of(series)));
    }

    private void initializeTopWinner() {
        XYChart.Series<Double, String> series = new XYChart.Series<>();
        series.setData(FXCollections.observableList(service.getTopWinners()));
        topWinners.setData(FXCollections.observableList(List.of(series)));
    }

    private void initializeTopLoser() {
        XYChart.Series<Double, String> series = new XYChart.Series<>();
        series.setData(FXCollections.observableList(service.getTopLosers()));
        topLosers.setData(FXCollections.observableList(List.of(series)));
    }

    private void initializeMonthlyChart() {
        // TODO
    }

}
