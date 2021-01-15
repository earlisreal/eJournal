package io.earlisreal.ejournal.ui.controller;


import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.model.TradeSummary;
import io.earlisreal.ejournal.service.PlotService;
import io.earlisreal.ejournal.service.ServiceProvider;
import io.earlisreal.ejournal.service.TradeLogService;
import io.earlisreal.ejournal.ui.helper.TradeDetailsDialog;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.Initializable;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDate;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

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
                try {
                    TradeDetailsDialog.getInstance().show(row.getItem());
                } catch (IOException e) {
                    handleException(e);
                }
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
