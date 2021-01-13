package io.earlisreal.ejournal.ui.controller;


import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.model.TradeSummary;
import io.earlisreal.ejournal.service.PlotService;
import io.earlisreal.ejournal.service.ServiceProvider;
import io.earlisreal.ejournal.service.TradeLogService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDate;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.earlisreal.ejournal.util.CommonUtil.handleException;
import static io.earlisreal.ejournal.util.CommonUtil.prettify;

public class LogsController implements Initializable {

    public TableView<TradeLog> logTable;
    public TableColumn<TradeLog, LocalDate> logDate;
    public TableColumn<TradeLog, String> logStock;
    public TableColumn<TradeLog, String> logAction;
    public TableColumn<TradeLog, String> logPrice;
    public TableColumn<TradeLog, String> logShares;
    public TableColumn<TradeLog, String> logFees;
    public TableColumn<TradeLog, String> logNet;
    public TableColumn<TradeLog, String> logStrategy;

    public TableView<TradeSummary> summaryTable;
    public TableColumn<TradeSummary, String> summaryClosed;
    public TableColumn<TradeSummary, String> summaryStock;
    public TableColumn<TradeSummary, String> summaryPosition;
    public TableColumn<TradeSummary, String> summaryProfit;
    public TableColumn<TradeSummary, String> summaryStrategy;
    public TableColumn<TradeSummary, String> summaryPercent;
    public TableColumn<TradeSummary, String> summaryDays;

    private TradeLogService tradeLogService;
    private Stage tradeDetailsStage;
    private TradeDetailsController tradeDetailsController;
    private PlotService plotService;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        tradeLogService = ServiceProvider.getTradeLogService();
        plotService = ServiceProvider.getPlotService();

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/dialog/trade-details.fxml"));
            Parent dialog = loader.load();
            Scene scene = new Scene(dialog);
            tradeDetailsStage = new Stage();
            tradeDetailsStage.initModality(Modality.APPLICATION_MODAL);
            tradeDetailsStage.setScene(scene);
            tradeDetailsController = loader.getController();
        } catch (IOException e) {
            handleException(e);
        }

        reload();
    }

    public void reload() {
        initLogs();
        initSummary();
    }

    private void initLogs() {
        logTable.setItems(FXCollections.observableList(tradeLogService.getLogs()));
        logDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        logStock.setCellValueFactory(new PropertyValueFactory<>("stock"));
        logPrice.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getPrice())));
        logAction.setCellValueFactory(t -> new SimpleStringProperty(t.getValue().isBuy() ? "BUY" : "SELL"));
        logShares.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getShares())));
        logFees.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getFees())));
        logNet.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getNetAmount())));
        logStrategy.setCellValueFactory(new PropertyValueFactory<>("strategyId"));
    }

    private void initSummary() {
        summaryTable.setRowFactory(param -> {
            TableRow<TradeSummary> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                TradeSummary summary = row.getItem();
                tradeDetailsController.initialize(summary);
                tradeDetailsStage.setTitle(summary.getStock());
                tradeDetailsStage.show();

                CompletableFuture.supplyAsync(() -> {
                    try {
                        return plotService.plot(row.getItem());
                    } catch (IOException e) {
                        handleException(e);
                    }
                    return null;
                }).thenAccept(imageUrl -> {
                    if (imageUrl == null) return;
                    try {
                        tradeDetailsController.updateImage(imageUrl.toUri().toURL().toString());
                    } catch (MalformedURLException e) {
                        handleException(e);
                    }
                });
            });
            return row;
        });

        summaryTable.setItems(FXCollections.observableList(tradeLogService.getTradeSummaries()));
        summaryClosed.setCellValueFactory(new PropertyValueFactory<>("closeDate"));
        summaryStock.setCellValueFactory(new PropertyValueFactory<>("stock"));
        summaryPosition.setCellValueFactory(s -> new SimpleStringProperty(prettify(s.getValue().getPosition())));
        summaryProfit.setCellValueFactory(s ->
                new SimpleStringProperty("" + prettify(s.getValue().getProfit())));
        summaryPercent.setCellValueFactory(s ->
                new SimpleStringProperty(prettify(s.getValue().getProfitPercentage()) + "%"));
        summaryDays.setCellValueFactory(new PropertyValueFactory<>("tradeLength"));
    }

}
