package io.earlisreal.ejournal.ui.controller;

import io.earlisreal.ejournal.model.TradeSummary;
import io.earlisreal.ejournal.service.AnalyticsService;
import io.earlisreal.ejournal.service.ServiceProvider;
import io.earlisreal.ejournal.service.StockService;
import io.earlisreal.ejournal.service.TradeLogService;
import javafx.fxml.Initializable;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.net.URL;
import java.util.ResourceBundle;

public class DayTradeDashboardController implements Initializable {

    public TableView<TradeSummary> tradesTable;
    public TableColumn<TradeSummary, String> openColumn;
    public TableColumn<TradeSummary, String> stockColumn;
    public TableColumn<TradeSummary, String> typeColumn;
    public TableColumn<TradeSummary, String> averageColumn;
    public TableColumn<TradeSummary, String> sharesColumn;
    public TableColumn<TradeSummary, String> totalCostColumn;
    public TableColumn<TradeSummary, String> profitColumn;
    public TableColumn<TradeSummary, String> percentColumn;

    public PieChart accuracyPie;
    public Label accuracyLabel;

    public Label dateLabel;
    public Label timeLabel;
    public Label gainLossLabel;
    public Label profitLabel;
    public Label lossLabel;

    private AnalyticsService analyticsService;
    private TradeLogService tradeLogService;
    private StockService stockService;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        tradeLogService = ServiceProvider.getTradeLogService();
        stockService = ServiceProvider.getStockService();
        analyticsService = ServiceProvider.getAnalyticsService();

        reload();
    }

    public void reload() {

    }

}
