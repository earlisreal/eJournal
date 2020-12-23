package io.earlisreal.ejournal.ui.controller;


import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.model.TradeSummary;
import io.earlisreal.ejournal.service.ServiceProvider;
import io.earlisreal.ejournal.service.TradeLogService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.Initializable;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

import java.net.URL;
import java.time.LocalDate;
import java.util.ResourceBundle;

public class LogsController implements Initializable {

    public TableView<TradeLog> logTable;
    public TableColumn<TradeLog, LocalDate> logDate;
    public TableColumn<TradeLog, String> logStock;
    public TableColumn<TradeLog, String> logAction;
    public TableColumn<TradeLog, Double> logPrice;
    public TableColumn<TradeLog, Integer> logShares;
    public TableColumn<TradeLog, String> logFees;
    public TableColumn<TradeLog, String> logNet;
    public TableColumn<TradeLog, String> logStrategy;

    public TableView<TradeSummary> summaryTable;
    public TableColumn<TradeSummary, String> summaryClosed;
    public TableColumn<TradeSummary, String> summaryStock;
    public TableColumn<TradeSummary, String> summaryPosition;
    public TableColumn<TradeSummary, String> summaryProfit;
    public TableColumn<TradeSummary, String> summaryStrategy;

    private TradeLogService tradeLogService;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        tradeLogService = ServiceProvider.getTradeLogService();
        initLogs();
        initSummary();
    }

    private void initLogs() {
        logTable.setItems(FXCollections.observableList(tradeLogService.getAll()));
        logDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        logStock.setCellValueFactory(new PropertyValueFactory<>("stock"));
        logPrice.setCellValueFactory(new PropertyValueFactory<>("price"));
        logAction.setCellValueFactory(t -> new SimpleStringProperty(t.getValue().isBuy() ? "BUY" : "SELL"));
        logPrice.setCellValueFactory(new PropertyValueFactory<>("price"));
        logShares.setCellValueFactory(new PropertyValueFactory<>("shares"));
        logNet.setCellValueFactory(p -> new SimpleStringProperty(String.valueOf(p.getValue().getPrice() * p.getValue().getShares())));
        logStrategy.setCellValueFactory(new PropertyValueFactory<>("strategyId"));
    }

    private void initSummary() {
        for (TradeSummary summary : tradeLogService.getTradeSummaries()) {
            System.out.println(summary);
        }
        summaryTable.setItems(FXCollections.observableList(tradeLogService.getTradeSummaries()));
        summaryClosed.setCellValueFactory(new PropertyValueFactory<>("closeDate"));
        summaryStock.setCellValueFactory(new PropertyValueFactory<>("stock"));
        summaryPosition.setCellValueFactory(new PropertyValueFactory<>("position"));
        summaryProfit.setCellValueFactory(s ->
                new SimpleStringProperty(s.getValue().getSimpleProfit() + " (" + s.getValue().getProfitPercentage() + ")"));
    }

}
