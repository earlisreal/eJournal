package io.earlisreal.ejournal.ui.controller;

import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.model.TradeSummary;
import io.earlisreal.ejournal.service.PlotService;
import io.earlisreal.ejournal.service.ServiceProvider;
import io.earlisreal.ejournal.service.StockService;
import io.earlisreal.ejournal.util.Pair;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

import static io.earlisreal.ejournal.util.CommonUtil.handleException;
import static io.earlisreal.ejournal.util.CommonUtil.prettify;
import static io.earlisreal.ejournal.util.CommonUtil.round;

public class TradeDetailsController {

    private final StockService stockService;
    private final PlotService plotService;

    public ImageView plotImageView;
    public TableView<TradeLog> logTable;
    public TableColumn<TradeLog, String> logDate;
    public TableColumn<TradeLog, String> logAction;
    public TableColumn<TradeLog, String> logPrice;
    public TableColumn<TradeLog, String> logShares;
    public TableColumn<TradeLog, String> logFees;
    public TableColumn<TradeLog, String> logNet;
    public AnchorPane anchorPane;
    public ProgressIndicator loadingProgress;
    public Label loadingLabel;

    public TableView<List<Pair<String, String>>> statisticTable;
    public TableColumn<List<Pair<String, String>>, String> statisticColumn;
    public TableColumn<List<Pair<String, String>>, String> valueColumn;
    public TableColumn<List<Pair<String, String>>, String> statisticColumn1;
    public TableColumn<List<Pair<String, String>>, String> valueColumn1;
    public TableColumn<List<Pair<String, String>>, String> statisticColumn2;
    public TableColumn<List<Pair<String, String>>, String> valueColumn2;
    public Button nextButton;
    public Button previousButton;
    public Label ofLabel;

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

    public void nextTrade() {
        index = (index + 1) % summaries.size();
        show();
    }

    public void previousTrade() {
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

        ofLabel.setText(index + 1 + " of " + summaries.size() + " Trade" + (summaries.size() > 1 ? "s" : ""));
    }

    private void initializeStatistics(TradeSummary summary) {
        List<Pair<String, String>> list = new ArrayList<>();
        list.add(new Pair<>("Stock", summary.getStock()));
        list.add(new Pair<>("Name", stockService.getName(summary.getStock())));
        list.add(new Pair<>("Type", summary.getTradeType()));

        list.add(new Pair<>("Open", prettify(summary.getOpenDate())));
        if (summary.isClosed()) {
            list.add(new Pair<>("Closed", prettify(summary.getCloseDate())));
            list.add(new Pair<>("Holding Period", String.valueOf(summary.getHoldingPeriod())));

            list.add(new Pair<>("Total Shares", prettify(summary.getShares())));
            list.add(new Pair<>("Position", prettify(summary.getPosition())));
            list.add(new Pair<>("Profit %", prettify(summary.getProfitPercentage()) + "%"));

            list.add(new Pair<>("Average Buy", prettify(summary.getAverageBuy())));
            list.add(new Pair<>("Average Sell", prettify(summary.getAverageSell())));
            list.add(new Pair<>("Profit", prettify(summary.getProfit())));
        }
        else {
            String hold = "";
            Period period = summary.getOpenDate().toLocalDate().until(LocalDate.now());
            if (period.getYears() > 0) {
                hold += period.getYears() + " Years ";
            }
            if (period.getMonths() > 0) {
                hold += period.getMonths() + " Months ";
            }
            hold += period.getDays() + " Days";

            double cost = summary.getAverageBuy() * summary.getRemainingShares();
            double unrealizedProfit = stockService.getPrice(summary.getStock()) * summary.getRemainingShares() - cost;

            double soldShares = summary.getShares() - summary.getRemainingShares();
            double profit = ((summary.getTotalSell() / soldShares) - summary.getAverageBuy()) * soldShares;

            list.add(new Pair<>("Holding Days", hold));
            list.add(new Pair<>("Unrealized Profit", prettify(unrealizedProfit)));

            list.add(new Pair<>("Total Shares", prettify(summary.getShares())));
            list.add(new Pair<>("Position", prettify(summary.getPosition())));
            list.add(new Pair<>("Unrealized Profit %", round(unrealizedProfit / cost * 100) + "%"));

            list.add(new Pair<>("Average Buy", prettify(summary.getAverageBuy())));
            list.add(new Pair<>("Realized Profit", prettify(profit)));
            list.add(new Pair<>("Realized Profit %", round(profit / (soldShares * summary.getAverageBuy()) * 100) + "%"));
        }

        List<List<Pair<String, String>>> pairs = new ArrayList<>();
        List<Pair<String, String>> pair = new ArrayList<>();
        for (int i = 0; i < list.size(); ++i) {
            if (i % 3 == 0) {
                pair = new ArrayList<>();
                pairs.add(pair);
            }
            pair.add(list.get(i));
        }

        statisticTable.setItems(FXCollections.observableList(pairs));
        statisticColumn.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().get(0).getT()));
        valueColumn.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().get(0).getU()));
        statisticColumn1.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().get(1).getT()));
        valueColumn1.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().get(1).getU()));
        statisticColumn2.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().get(2).getT()));
        valueColumn2.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().get(2).getU()));
    }

    private void initializeLogs(TradeSummary tradeSummary) {
        logTable.setItems(FXCollections.observableList(tradeSummary.getLogs()));
        logDate.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getDate())));
        logAction.setCellValueFactory(t -> new SimpleStringProperty(t.getValue().getAction()));
        logPrice.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getPrice())));
        logShares.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getShares())));
        logNet.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getNetAmount())));
        logFees.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getFees())));
    }

    public void updateImage(TradeSummary tradeSummary) {
        Service<Path> service = new Service<>() {
            @Override
            protected Task<Path> createTask() {
                return new Task<>() {
                    @Override
                    protected Path call() {
                        try {
                            return plotService.plot(tradeSummary);
                        } catch (IOException e) {
                            handleException(e);
                        }
                        return null;
                    }
                };
            }
        };

        service.setOnSucceeded(event -> {
            if (!summaries.get(index).equals(tradeSummary)) {
                return;
            }
            Path imagePath = (Path) event.getSource().getValue();
            if (imagePath == null) {
                // TODO : Add text to display fail
                System.out.println("Failed to plot image");
                return;
            }
            try {
                plotImageView.setImage(new Image(imagePath.toUri().toURL().toString()));
                plotImageView.setVisible(true);
                loadingLabel.setVisible(false);
                loadingProgress.setVisible(false);
            } catch (MalformedURLException e) {
                handleException(e);
            }
        });

        service.setOnFailed(event -> {
            var exception = event.getSource().getException();
            handleException(exception);
        });

        service.start();
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
