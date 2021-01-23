package io.earlisreal.ejournal.ui.controller;

import io.earlisreal.ejournal.dto.BankTransaction;
import io.earlisreal.ejournal.service.BankTransactionService;
import io.earlisreal.ejournal.service.ServiceProvider;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.Callback;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class BankTransactionController implements Initializable {

    public TableColumn<BankTransaction, String> dateColumn;
    public TableColumn<BankTransaction, String> actionColumn;
    public TableColumn<BankTransaction, String> amountColumn;
    public TableView<BankTransaction> bankTable;
    public TableColumn<BankTransaction, Void> deleteColumn;
    public DatePicker depositDate;
    public TextField depositAmount;
    public TextField withdrawAmount;
    public DatePicker withdrawDate;

    private BankTransactionService service;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        service = ServiceProvider.getBankTransactionService();
        loadBankTransactions();
    }

    public void deposit(ActionEvent event) {
        BankTransaction bankTransaction = new BankTransaction();
        bankTransaction.setAmount(Double.parseDouble(depositAmount.getText()));
        bankTransaction.setDate(depositDate.getValue());
        service.insert(List.of(bankTransaction));

        reload();
    }

    public void withdraw(ActionEvent event) {
        BankTransaction bankTransaction = new BankTransaction();
        bankTransaction.setAmount(Double.parseDouble(withdrawAmount.getText()) * -1);
        bankTransaction.setDate(withdrawDate.getValue());
        service.insert(List.of(bankTransaction));

        reload();
    }

    public void reload() {
        loadBankTransactions();
    }

    public void loadBankTransactions() {
        bankTable.setItems(FXCollections.observableList(service.getAll()));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("date"));
        amountColumn.setCellValueFactory(new PropertyValueFactory<>("amount"));
        actionColumn.setCellValueFactory(new PropertyValueFactory<>("action"));
        deleteColumn.setCellFactory(new Callback<>() {
            @Override
            public TableCell<BankTransaction, Void> call(TableColumn<BankTransaction, Void> param) {
                return new TableCell<>() {
                    @Override
                    protected void updateItem(Void item, boolean empty) {
                        final Button button = new Button("Delete");
                        button.setOnAction(event -> {
                            boolean res = service.delete(getTableView().getItems().get(getIndex()).getId());
                            if (res) {
                                loadBankTransactions();
                            }
                        });
                        super.updateItem(item, empty);
                        if (!empty) setGraphic(button);
                    }
                };
            }
        });
    }

}
