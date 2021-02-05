package io.earlisreal.ejournal.ui.controller;

import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.model.TradeSummary;
import io.earlisreal.ejournal.service.PlotService;
import io.earlisreal.ejournal.service.ServiceProvider;
import io.earlisreal.ejournal.service.StockService;
import io.earlisreal.ejournal.util.Pair;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static io.earlisreal.ejournal.util.CommonUtil.*;

public class TradeDetailsController {

    private final StockService stockService;
    private final PlotService plotService;

    public ImageView plotImageView;
    public TableView<TradeLog> logTable;
    public TableColumn<TradeLog, LocalDate> logDate;
    public TableColumn<TradeLog, String> logAction;
    public TableColumn<TradeLog, String> logPrice;
    public TableColumn<TradeLog, String> logShares;
    public TableColumn<TradeLog, String> logFees;
    public TableColumn<TradeLog, String> logNet;
    public AnchorPane anchorPane;
    public ProgressIndicator loadingProgress;
    public Label loadingLabel;

    public TableView<Pair<String, String>> statisticTable;
    public TableColumn<Pair<String, String>, String> statisticColumn;
    public TableColumn<Pair<String, String>, String> valueColumn;
    public Button nextButton;
    public Button previousButton;

    private List<TradeSummary> summaries;
    private int index;

    public TradeDetailsController() {
        stockService = ServiceProvider.getStockService();
        plotService = ServiceProvider.getPlotService();

        summaries = new ArrayList<>();
    }

    public void setSummaries(List<TradeSummary> summaries) {
        this.summaries = summaries;
        boolean disabled = summaries.size() == 1;
        nextButton.setDisable(disabled);
        previousButton.setDisable(disabled);
    }

    public void nextTrade(ActionEvent event) {
        index = (index + 1) % summaries.size();
        show();
    }

    public void previousTrade(ActionEvent event) {
        if (--index < 0) index = summaries.size() - 1;
        show();
    }

    public void show(TradeSummary summary) {
        index = summaries.indexOf(summary);
        show();
    }

    public void show() {
        showLoading();
        updateImage(getCurrentSummary());

        initializeStatistics(getCurrentSummary());
        initializeLogs(getCurrentSummary());
    }

    private void initializeStatistics(TradeSummary summary) {
        List<Pair<String, String>> list = new ArrayList<>();
        list.add(new Pair<>("Stock", summary.getStock()));
        list.add(new Pair<>("Open", summary.getOpenDate().toString()));
        list.add(new Pair<>("Average Buy", prettify(summary.getAverageBuy())));
        list.add(new Pair<>("Total Shares", prettify(summary.getShares())));
        list.add(new Pair<>("Position", prettify(summary.getPosition())));

        if (summary.isClosed()) {
            list.add(new Pair<>("Closed", summary.getCloseDate().toString()));
            list.add(new Pair<>("Holding Days", String.valueOf(summary.getTradeLength())));
            list.add(new Pair<>("Average Sell", prettify(summary.getAverageSell())));
            list.add(new Pair<>("Profit", prettify(summary.getProfit())));
            list.add(new Pair<>("Profit Percentage", prettify(summary.getProfitPercentage()) + "%"));
        }
        else {
            double soldShares = summary.getShares() - summary.getRemainingShares();
            double profit = ((summary.getTotalSell() / soldShares) - summary.getAverageBuy()) * soldShares;
            list.add(new Pair<>("Realized Profit", prettify(profit)));
            list.add(new Pair<>("Realized Profit %", round(profit / (soldShares * summary.getAverageBuy()) * 100) + "%"));

            String hold = "";
            Period period = summary.getOpenDate().until(LocalDate.now());
            if (period.getYears() > 0) {
                hold += period.getYears() + " Years ";
            }
            if (period.getMonths() > 0) {
                hold += period.getMonths() + " Months ";
            }
            hold += period.getDays() + " Days";
            list.add(new Pair<>("Holding Days", hold));

            double cost = summary.getAverageBuy() * summary.getRemainingShares();
            double unrealizedProfit = stockService.getPrice(summary.getStock()) * summary.getRemainingShares() - cost;
            list.add(new Pair<>("Unrealized Profit", prettify(unrealizedProfit)));
            list.add(new Pair<>("Unrealized Profit %", round(unrealizedProfit / cost * 100) + "%"));
        }

        statisticTable.setItems(FXCollections.observableList(list));
        statisticColumn.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getT()));
        valueColumn.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getU()));
    }

    private void initializeLogs(TradeSummary tradeSummary) {
        logTable.setItems(FXCollections.observableList(tradeSummary.getLogs()));
        logDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        logAction.setCellValueFactory(t -> new SimpleStringProperty(t.getValue().isBuy() ? "BUY" : "SELL"));
        logPrice.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getPrice())));
        logShares.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getShares())));
        logNet.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getNetAmount())));
        logFees.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getFees())));
    }

    public void updateImage(TradeSummary tradeSummary) {
        CompletableFuture.supplyAsync(() -> {
            try {
                Path imagePath = plotService.plot(tradeSummary);
                tradeSummary.setImageUrl(imagePath.toUri().toURL().toString());
                return tradeSummary;
            } catch (IOException e) {
                handleException(e);
            }
            return null;
        }).thenAccept(summary -> {
            if (!summaries.get(index).equals(tradeSummary)) {
                return;
            }

            if (tradeSummary.getImageUrl() == null) return;

            plotImageView.setImage(new Image(tradeSummary.getImageUrl()));
            plotImageView.setVisible(true);
            loadingLabel.setVisible(false);
            loadingProgress.setVisible(false);
        });

    }

    public void showLoading() {
        plotImageView.setVisible(false);
        loadingLabel.setVisible(true);
        loadingProgress.setVisible(true);
    }

    private TradeSummary getCurrentSummary() {
        return summaries.get(index);
    }

}
