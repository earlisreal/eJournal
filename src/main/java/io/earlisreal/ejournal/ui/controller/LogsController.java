package io.earlisreal.ejournal.ui.controller;


import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.service.ServiceProvider;
import io.earlisreal.ejournal.service.TradeLogService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.Initializable;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

import java.net.URL;
import java.time.LocalDate;
import java.util.ResourceBundle;

public class LogsController implements Initializable {

    public TableView<TradeLog> table;
    public TableColumn<TradeLog, LocalDate> date;
    public TableColumn<TradeLog, String> stock;
    public TableColumn<TradeLog, String> action;
    public TableColumn<TradeLog, Double> price;
    public TableColumn<TradeLog, Integer> shares;
    public TableColumn<TradeLog, String> fees;
    public TableColumn<TradeLog, String> net;
    public TableColumn<TradeLog, String> strategy;

    private TradeLogService tradeLogService;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        tradeLogService = ServiceProvider.getTradeLogService();

        table.setItems(FXCollections.observableList(tradeLogService.getAll()));
        date.setCellValueFactory(new PropertyValueFactory<>("date"));
        stock.setCellValueFactory(new PropertyValueFactory<>("stock"));
        price.setCellValueFactory(new PropertyValueFactory<>("price"));
        action.setCellValueFactory(t -> new SimpleStringProperty(t.getValue().isBuy() ? "BUY" : "SELL"));
        price.setCellValueFactory(new PropertyValueFactory<>("price"));
        shares.setCellValueFactory(new PropertyValueFactory<>("shares"));
        net.setCellValueFactory(p -> new SimpleStringProperty(String.valueOf(p.getValue().getPrice() * p.getValue().getShares())));
        strategy.setCellValueFactory(new PropertyValueFactory<>("strategyId"));
    }

}
