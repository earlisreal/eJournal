package io.earlisreal.ejournal.ui.controller;

import io.earlisreal.ejournal.model.TradeSummary;
import io.earlisreal.ejournal.service.AnalyticsService;
import io.earlisreal.ejournal.service.ServiceProvider;
import io.earlisreal.ejournal.service.StockService;
import io.earlisreal.ejournal.service.TradeLogService;
import io.earlisreal.ejournal.ui.service.UIServiceProvider;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.Initializable;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import static io.earlisreal.ejournal.util.CommonUtil.prettify;
import static io.earlisreal.ejournal.util.CommonUtil.round;

public class SwingDashboardController implements Initializable {

    public TableView<TradeSummary> openPositionTable;
    public TableColumn<TradeSummary, String> stockColumn;
    public TableColumn<TradeSummary, String> averageBuyColumn;
    public TableColumn<TradeSummary, String> sharesColumn;
    public TableColumn<TradeSummary, String> totalCostColumn;
    public TableColumn<TradeSummary, String> marketValueColumn;
    public TableColumn<TradeSummary, String> profitColumn;
    public TableColumn<TradeSummary, String> percentColumn;
    public TableColumn<TradeSummary, String> lastPriceColumn;
    public PieChart portfolioChart;
    public Label noDataLabel;

    private AnalyticsService analyticsService;
    private TradeLogService tradeLogService;
    private StockService stockService;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        tradeLogService = ServiceProvider.getTradeLogService();
        stockService = ServiceProvider.getStockService();
        analyticsService = ServiceProvider.getAnalyticsService();
    }

    public void reload() {
        initializeOpenTrades();
        initializePortfolio();
    }

    private void initializeOpenTrades() {
        openPositionTable.setItems(FXCollections.observableArrayList(tradeLogService.getOpenPositions()));
        stockColumn.setCellValueFactory(new PropertyValueFactory<>("stock"));
        averageBuyColumn.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getAverageBuy())));
        lastPriceColumn.setCellValueFactory(p -> new SimpleStringProperty(
                prettify(stockService.getPrice(p.getValue().getStock()))));
        sharesColumn.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getRemainingShares())));
        totalCostColumn.setCellValueFactory(p -> new SimpleStringProperty(
                prettify(p.getValue().getAverageBuy() * p.getValue().getRemainingShares())));
        marketValueColumn.setCellValueFactory(p -> new SimpleStringProperty(
                prettify(stockService.getPrice(p.getValue().getStock()) * p.getValue().getRemainingShares())));
        profitColumn.setCellValueFactory(p -> new SimpleStringProperty(prettify(getProfit(p.getValue()))));
        percentColumn.setCellValueFactory(p -> new SimpleStringProperty(
                round(getProfit(p.getValue()) / getCost(p.getValue()) * 100) + "%"));

        openPositionTable.setRowFactory(unused ->
                UIServiceProvider.getTradeDetailsDialogService().getTableRow(tradeLogService.getOpenPositions()));
    }

    private void initializePortfolio() {
        List<PieChart.Data> data = new ArrayList<>();
        double equity = analyticsService.getTotalEquity();
        var positions = tradeLogService.getOpenPositions();

        noDataLabel.setVisible(false);
        double sum = 0;
        for (TradeSummary summary : positions) {
            sum += summary.getPosition();
            double position = summary.getPosition();
            data.add(new PieChart.Data(summary.getStock() + getPositionPercentage(position, equity), position));
        }

        double cash = equity - sum;
        data.add(0, new PieChart.Data("Cash" + getPositionPercentage(cash, equity), cash));
        portfolioChart.setLegendVisible(false);
        portfolioChart.setData(FXCollections.observableList(data));
    }

    private double getProfit(TradeSummary summary) {
        double cost = getCost(summary);
        double current = stockService.getPrice(summary.getStock()) * summary.getRemainingShares();
        return current - cost - summary.getBroker().getFees(current, false);
    }

    private double getCost(TradeSummary summary) {
        return summary.getAverageBuy() * summary.getRemainingShares();
    }

    private String getPositionPercentage(double part, double total) {
        return " (" + prettify(part / total * 100) + "%)";
    }

}
