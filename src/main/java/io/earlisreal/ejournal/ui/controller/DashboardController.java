package io.earlisreal.ejournal.ui.controller;

import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.model.TradeSummary;
import io.earlisreal.ejournal.service.ServiceProvider;
import io.earlisreal.ejournal.service.StockService;
import io.earlisreal.ejournal.service.TradeLogService;
import io.earlisreal.ejournal.ui.service.UIServiceProvider;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Paint;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

import static io.earlisreal.ejournal.util.CommonUtil.*;

public class DashboardController implements Initializable {

    public HBox previousTradesBox;
    public Label lastClosedDate;
    public Label lastProfit;
    public Label lastPosition;
    public Label lastHolding;
    public Label lastStock;
    public TableView<TradeSummary> openPositionTable;

    private TradeLogService tradeLogService;
    private StockService stockService;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        tradeLogService = ServiceProvider.getTradeLogService();
        stockService = ServiceProvider.getStockService();

        reload();
    }

    public void reload() {
        initializeLastTrade();
        initializePreviousTrades();
        initializeOpenTrades();
    }

    private void initializeOpenTrades() {
        var openPositions = tradeLogService.getOpenPositions();
        // TODO: Listen to Startup Fetch of Stock price
        for (TradeSummary log : openPositions) {
//            stockService.getPrice(log.getStock());
        }
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

            pane.setOnMouseClicked(event -> {
                try {
                    UIServiceProvider.getTradeDetailsDialogService().show(summary);
                } catch (IOException e) {
                    handleException(e);
                }
            });
        }

        for (int i = 0; i < panes.size() - summaries.size(); ++i) {
            panes.get(panes.size() - 1 - i).setVisible(false);
        }
    }

}
