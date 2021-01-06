package io.earlisreal.ejournal.ui.controller;

import io.earlisreal.ejournal.service.AnalyticsService;
import io.earlisreal.ejournal.service.ServiceProvider;
import io.earlisreal.ejournal.service.TradeLogService;
import javafx.collections.FXCollections;
import javafx.fxml.Initializable;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

import static io.earlisreal.ejournal.util.CommonUtil.prettify;
import static io.earlisreal.ejournal.util.CommonUtil.round;

public class AnalyticsController implements Initializable {

    public Label analyticsLabel;
    public LineChart<String, Double> equityChart;

    private AnalyticsService service;
    private TradeLogService tradeLogService;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        service = ServiceProvider.getAnalyticsService();
        tradeLogService = ServiceProvider.getTradeLogService();

        reload();
    }

    public void reload() {
        service.initialize();
        initializeStatistics();
        initializeEquityChart();
    }

    private void initializeStatistics() {
        String separator = System.lineSeparator();
        String ratio = "Edge Ratio: " + round(service.getEdgeRatio()) + separator;
        String profit = "Average Profit: " + prettify(service.getAverageProfit()) + " (" + service.getAverageProfitPercentage() + "%" + separator;
        String loss = "Average Loss: " + prettify(service.getAverageLoss()) + " (" + service.getAverageLossPercentage() + "%)" + separator;
        String accuracy = "Accuracy: " + service.getAccuracy() + "%" + separator;
        String profitFactor = "Profit Factor: " + service.getProfitFactor() + separator;
        String averageHoldingDays = "Average Holding Days: " + prettify(service.getAverageHoldingDays()) + separator;
        String tradesTaken = "Trades Taken: " + prettify(service.getSummaries().size()) + separator;
        String transactions = "Transactions: " + prettify(tradeLogService.getLogs().size()) + separator;
        String losses = "Losses: " + prettify(service.getLosses().size()) + separator;
        String wins = "Wins: " + prettify(service.getWins().size()) + separator;
        analyticsLabel.setText(tradesTaken +transactions + wins + losses + ratio + profit + loss + accuracy + profitFactor + averageHoldingDays);
    }

    private void initializeEquityChart() {
        XYChart.Series<String, Double> series = new XYChart.Series<>();
        series.setName("Original Portfolio");
        series.setData(FXCollections.observableList(service.getEquityData()));
        equityChart.setData(FXCollections.observableList(List.of(series)));
    }

}
