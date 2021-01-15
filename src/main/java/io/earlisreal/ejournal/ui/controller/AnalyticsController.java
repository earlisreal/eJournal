package io.earlisreal.ejournal.ui.controller;

import io.earlisreal.ejournal.model.TradeSummary;
import io.earlisreal.ejournal.service.AnalyticsService;
import io.earlisreal.ejournal.service.ServiceProvider;
import io.earlisreal.ejournal.service.TradeLogService;
import javafx.collections.FXCollections;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Paint;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

import static io.earlisreal.ejournal.util.CommonUtil.prettify;
import static io.earlisreal.ejournal.util.CommonUtil.round;

public class AnalyticsController implements Initializable {

    public LineChart<String, Double> equityChart;
    public HBox previousTradesBox;
    public Label lastClosedDate;
    public Label lastProfit;
    public Label lastPosition;
    public Label lastHolding;
    public Label lastStock;

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
        initializeLastTrade();
        initializePreviousTrades();
    }

    private void initializeLastTrade() {
        var summaries = tradeLogService.getTradeSummaries();
        TradeSummary lastTrade = summaries.get(summaries.size() - 1);
        lastStock.setText(lastTrade.getStock());
        lastClosedDate.setText(lastTrade.getCloseDate().toString());
        lastPosition.setText(prettify(lastTrade.getPosition()));
        lastHolding.setText(String.valueOf(lastTrade.getTradeLength()));
        lastProfit.setText(prettify(lastTrade.getProfit()));
    }

    private void initializePreviousTrades() {
        var summaries = tradeLogService.getTradeSummaries();
        var panes = previousTradesBox.getChildren();
        for (int i = 0; i < Math.min(panes.size(), summaries.size()); ++i) {
            Pane pane = (Pane) panes.get(i);
            var labels = pane.getChildren();
            Label win = (Label) labels.get(0);
            Label stock = (Label) labels.get(1);
            Label profit = (Label) labels.get(2);

            TradeSummary summary = summaries.get(summaries.size() - 1 - i);
            stock.setText(summary.getStock());
            profit.setText(round(summary.getProfitPercentage()) + "%");
            boolean isWin = summary.getProfit() > 0;
            if (isWin) {
                win.setText("WIN");
                profit.setTextFill(Paint.valueOf("green"));
                win.setTextFill(Paint.valueOf("green"));
            }
            else {
                win.setText("LOSS");
                profit.setTextFill(Paint.valueOf("red"));
                win.setTextFill(Paint.valueOf("red"));
            }
        }
    }

    private void initializeEquityChart() {
        XYChart.Series<String, Double> series = new XYChart.Series<>();
        series.setName("Original Portfolio");
        series.setData(FXCollections.observableList(service.getEquityData()));
        equityChart.setData(FXCollections.observableList(List.of(series)));
    }

}
