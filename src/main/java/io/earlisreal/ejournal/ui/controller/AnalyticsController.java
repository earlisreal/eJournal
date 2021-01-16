package io.earlisreal.ejournal.ui.controller;

import io.earlisreal.ejournal.model.TradeSummary;
import io.earlisreal.ejournal.service.AnalyticsService;
import io.earlisreal.ejournal.service.ServiceProvider;
import io.earlisreal.ejournal.service.TradeLogService;
import io.earlisreal.ejournal.ui.service.UIServiceProvider;
import javafx.collections.FXCollections;
import javafx.fxml.Initializable;
import javafx.scene.chart.*;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Paint;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

import static io.earlisreal.ejournal.util.CommonUtil.*;

public class AnalyticsController implements Initializable {

    public LineChart<String, Double> equityChart;
    public HBox previousTradesBox;
    public Label lastClosedDate;
    public Label lastProfit;
    public Label lastPosition;
    public Label lastHolding;
    public Label lastStock;
    public BarChart<Double, String> topWinners;
    public BarChart<Double, String> topLosers;

    private AnalyticsService service;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        service = ServiceProvider.getAnalyticsService();

        reload();
    }

    public void reload() {
        service.initialize();
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
