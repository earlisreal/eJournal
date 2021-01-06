package io.earlisreal.ejournal.ui.controller;

import io.earlisreal.ejournal.service.AnalyticsService;
import io.earlisreal.ejournal.service.ServiceProvider;
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

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        service = ServiceProvider.getAnalyticsService();

        reload();
    }

    public void reload() {
        initializeStatistics();
        initializeEquityChart();
    }

    private void initializeStatistics() {
        String ratio = "Edge Ratio: " + round(service.getEdgeRatio()) + "\n";
        String profit = "Average Profit: " + prettify(service.getAverageProfit()) + " (" + service.getAverageProfitPercentage() + "%)\n";
        String loss = "Average Loss: " + prettify(service.getAverageLoss()) + " (" + service.getAverageLossPercentage() + "%)\n";
        String accuracy = "Accuracy: " + service.getAccuracy() + "%\n";
        String profitFactor = "Profit Factor: " + service.getProfitFactor() + "\n";
        String averageHoldingDays = "Average Holding Days: " + prettify(service.getAverageHoldingDays()) + "\n";
        analyticsLabel.setText(ratio + profit + loss + accuracy + profitFactor + averageHoldingDays);
    }

    private void initializeEquityChart() {
        XYChart.Series<String, Double> series = new XYChart.Series<>();
        series.setName("Original Portfolio");
        series.setData(FXCollections.observableList(service.getEquityData()));
        equityChart.setData(FXCollections.observableList(List.of(series)));
    }

}
