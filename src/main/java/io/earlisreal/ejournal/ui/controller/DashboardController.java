package io.earlisreal.ejournal.ui.controller;

import io.earlisreal.ejournal.model.TradeSummary;
import io.earlisreal.ejournal.service.ServiceProvider;
import io.earlisreal.ejournal.service.StockService;
import io.earlisreal.ejournal.service.TradeLogService;
import io.earlisreal.ejournal.ui.service.UIServiceProvider;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Paint;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

import static io.earlisreal.ejournal.util.CommonUtil.handleException;
import static io.earlisreal.ejournal.util.CommonUtil.prettify;
import static io.earlisreal.ejournal.util.CommonUtil.round;

public class DashboardController implements Initializable {

    public HBox previousTradesBox;
    public Label lastClosedDate;
    public Label lastProfit;
    public Label lastPosition;
    public Label lastHolding;
    public Label lastStock;
    public Label lastStockName;
    public StackPane contentPane;

    private TradeLogService tradeLogService;
    private StockService stockService;
    private Parent swingDashboard;
    private SwingDashboardController swingController;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        tradeLogService = ServiceProvider.getTradeLogService();
        stockService = ServiceProvider.getStockService();

        try {
            FXMLLoader swingLoader = new FXMLLoader(getClass().getResource("/fxml/swing-dashboard.fxml"));
            swingDashboard = swingLoader.load();
            swingController = swingLoader.getController();

            contentPane.getChildren().add(swingDashboard);
        } catch (IOException e) {
            handleException(e);
        }

        initializeLastTrade();
        initializePreviousTrades();
    }

    public void reload() {
        initializeLastTrade();
        initializePreviousTrades();
    }

    private void initializeLastTrade() {
        var summaries = tradeLogService.getTradeSummaries();
        if (summaries.isEmpty()) {
            lastStockName.setText("");
            lastStock.setText("");
            lastClosedDate.setText("");
            lastPosition.setText("");
            lastHolding.setText("");
            lastProfit.setText("");
            return;
        }

        TradeSummary lastTrade = summaries.get(0);
        lastStockName.setText(stockService.getName(lastTrade.getStock()));
        lastStock.setText(lastTrade.getStock());
        lastClosedDate.setText(lastTrade.getCloseDate().toString());
        lastPosition.setText(prettify(lastTrade.getPosition()));
        lastHolding.setText(String.valueOf(lastTrade.getHoldingPeriod()));
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

            TradeSummary summary = summaries.get(i);
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

            pane.setOnMouseClicked(unused -> UIServiceProvider.getTradeDetailsDialogService().show(summary, summaries, "Recent Trades"));
        }

        for (int i = 0; i < panes.size() - summaries.size(); ++i) {
            panes.get(panes.size() - 1 - i).setVisible(false);
        }
    }

}
