package io.earlisreal.ejournal.ui.controller;

import io.earlisreal.ejournal.model.TradeSummary;
import io.earlisreal.ejournal.service.ServiceProvider;
import io.earlisreal.ejournal.service.StartupListener;
import io.earlisreal.ejournal.service.StockService;
import io.earlisreal.ejournal.service.TradeLogService;
import io.earlisreal.ejournal.ui.service.UIServiceProvider;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Paint;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

import static io.earlisreal.ejournal.util.CommonUtil.*;

public class DashboardController implements Initializable, StartupListener {

    public HBox previousTradesBox;
    public Label lastClosedDate;
    public Label lastProfit;
    public Label lastPosition;
    public Label lastHolding;
    public Label lastStock;

    public TableView<TradeSummary> openPositionTable;
    public TableColumn<TradeSummary, String> stockColumn;
    public TableColumn<TradeSummary, String> averageBuyColumn;
    public TableColumn<TradeSummary, String> sharesColumn;
    public TableColumn<TradeSummary, String> totalCostColumn;
    public TableColumn<TradeSummary, String> marketValueColumn;
    public TableColumn<TradeSummary, String> profitColumn;
    public TableColumn<TradeSummary, String> percentColumn;
    public TableColumn<TradeSummary, String> lastPriceColumn;

    private TradeLogService tradeLogService;
    private StockService stockService;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        ServiceProvider.getStartupService().addStockPriceListener(this);
        tradeLogService = ServiceProvider.getTradeLogService();
        stockService = ServiceProvider.getStockService();


        initializeLastTrade();
        initializePreviousTrades();
    }

    @Override
    public void onFinish() {
        initializeOpenTrades();
    }

    public void reload() {
        initializeLastTrade();
        initializePreviousTrades();
        initializeOpenTrades();
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

        openPositionTable.setRowFactory(unused -> UIServiceProvider.getTradeDetailsDialogService().getTableRow());
    }

    private double getProfit(TradeSummary summary) {
        double cost = getCost(summary);
        double current = stockService.getPrice(summary.getStock()) * summary.getRemainingShares();
        return current - cost;
    }

    private double getCost(TradeSummary summary) {
        return summary.getAverageBuy() * summary.getRemainingShares();
    }

    private void initializeLastTrade() {
        var summaries = tradeLogService.getTradeSummaries();
        if (summaries.isEmpty()) {
            lastStock.setText("");
            lastClosedDate.setText("");
            lastPosition.setText("");
            lastHolding.setText("");
            lastProfit.setText("");
            return;
        }

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
            pane.setVisible(true);

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

            pane.setOnMouseClicked(unused -> UIServiceProvider.getTradeDetailsDialogService().show(summary));
        }

        for (int i = 0; i < panes.size() - summaries.size(); ++i) {
            panes.get(panes.size() - 1 - i).setVisible(false);
        }
    }

}
