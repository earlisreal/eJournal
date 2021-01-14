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
        initializeEquityChart();
    }

    private void initializeEquityChart() {
        XYChart.Series<String, Double> series = new XYChart.Series<>();
        series.setName("Original Portfolio");
        series.setData(FXCollections.observableList(service.getEquityData()));
        equityChart.setData(FXCollections.observableList(List.of(series)));
    }

}
