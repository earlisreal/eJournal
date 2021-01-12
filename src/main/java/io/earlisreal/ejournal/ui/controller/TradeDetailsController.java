package io.earlisreal.ejournal.ui.controller;

import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.model.TradeSummary;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;

import java.time.LocalDate;

import static io.earlisreal.ejournal.util.CommonUtil.prettify;

public class TradeDetailsController {

    public ImageView plotImageView;
    public TableView<TradeLog> logTable;
    public TableColumn<TradeLog, LocalDate> logDate;
    public TableColumn<TradeLog, String> logAction;
    public TableColumn<TradeLog, String> logPrice;
    public TableColumn<TradeLog, String> logShares;
    public TableColumn<TradeLog, String> logFees;
    public TableColumn<TradeLog, String> logNet;
    public TableColumn<TradeLog, String> logStrategy;
    public AnchorPane anchorPane;

    public void initialize(TradeSummary tradeSummary) {
        logTable.setItems(FXCollections.observableList(tradeSummary.getLogs()));
        logDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        logAction.setCellValueFactory(t -> new SimpleStringProperty(t.getValue().isBuy() ? "BUY" : "SELL"));
        logPrice.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getPrice())));
        logShares.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getShares())));
        logNet.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getNetAmount())));
        logFees.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getFees())));
        logStrategy.setCellValueFactory(new PropertyValueFactory<>("strategyId"));
    }

}
