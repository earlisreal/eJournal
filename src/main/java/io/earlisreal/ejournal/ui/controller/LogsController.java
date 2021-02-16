package io.earlisreal.ejournal.ui.controller;


import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.model.TradeSummary;
import io.earlisreal.ejournal.service.ServiceProvider;
import io.earlisreal.ejournal.service.TradeLogService;
import io.earlisreal.ejournal.ui.service.UIServiceProvider;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

import java.time.LocalDate;
import java.util.Comparator;

import static io.earlisreal.ejournal.util.CommonUtil.prettify;

public class LogsController {

    public TableView<TradeLog> logTable;
    public TableColumn<TradeLog, LocalDate> logDate;
    public TableColumn<TradeLog, String> logStock;
    public TableColumn<TradeLog, String> logAction;
    public TableColumn<TradeLog, String> logPrice;
    public TableColumn<TradeLog, String> logShares;
    public TableColumn<TradeLog, String> logFees;
    public TableColumn<TradeLog, String> logNet;

    public TableView<TradeSummary> summaryTable;
    public TableColumn<TradeSummary, String> summaryClosed;
    public TableColumn<TradeSummary, String> summaryStock;
    public TableColumn<TradeSummary, String> summaryPosition;
    public TableColumn<TradeSummary, String> summaryProfit;
    public TableColumn<TradeSummary, String> summaryPercent;
    public TableColumn<TradeSummary, String> summaryDays;

    private final TradeLogService tradeLogService;

    public LogsController() {
        tradeLogService = ServiceProvider.getTradeLogService();
    }

    public void reload() {
        initLogs();
        initSummary();
    }

    private void initLogs() {
        var logs = tradeLogService.getLogs();
        logTable.setItems(FXCollections.observableList(logs));
        logDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        logStock.setCellValueFactory(new PropertyValueFactory<>("stock"));
        logPrice.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getPrice())));
        logAction.setCellValueFactory(t -> new SimpleStringProperty(t.getValue().isBuy() ? "BUY" : "SELL"));
        logShares.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getShares())));
        logFees.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getFees())));
        logNet.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getNetAmount())));
    }

    private void initSummary() {
        var summaries = tradeLogService.getTradeSummaries();
        summaryTable.setRowFactory(param ->
                UIServiceProvider.getTradeDetailsDialogService().getTableRow(summaries));

        summaryTable.setItems(FXCollections.observableList(summaries));
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
