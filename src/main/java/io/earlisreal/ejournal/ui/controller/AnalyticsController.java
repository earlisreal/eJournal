package io.earlisreal.ejournal.ui.controller;

import io.earlisreal.ejournal.service.AnalyticsService;
import io.earlisreal.ejournal.service.ServiceProvider;
import javafx.collections.FXCollections;
import javafx.fxml.Initializable;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class AnalyticsController implements Initializable {

    public LineChart<String, Double> equityChart;
    public BarChart<Double, String> topWinners;
    public BarChart<Double, String> topLosers;

    private AnalyticsService service;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        service = ServiceProvider.getAnalyticsService();
        service.initialize();

        reload();
    }

    public void reload() {
        initializeEquityChart();
        initializeTopLoser();
        initializeTopWinner();
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

}
