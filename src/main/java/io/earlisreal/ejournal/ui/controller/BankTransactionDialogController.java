package io.earlisreal.ejournal.ui.controller;

import io.earlisreal.ejournal.dto.BankTransaction;
import io.earlisreal.ejournal.service.BankTransactionService;
import io.earlisreal.ejournal.service.ServiceProvider;
import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class BankTransactionDialogController implements Initializable {

    public DatePicker datePicker;
    public TextField amount;

    private BankTransactionService service;
    private BankTransactionController bankTransactionController;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        service = ServiceProvider.getBankTransactionService();
    }

    public void okay(ActionEvent event) {
        BankTransaction bankTransaction = new BankTransaction();
        bankTransaction.setAmount(Double.parseDouble(amount.getText()));
        bankTransaction.setDate(datePicker.getValue());
        service.insert(List.of(bankTransaction));


        closeWindow(event);
    }

    public void cancel(ActionEvent event) {
        closeWindow(event);
    }

    public void setBankTransactionController(BankTransactionController bankTransactionController) {
        this.bankTransactionController = bankTransactionController;
    }

    private void closeWindow(ActionEvent event) {
        Node source = (Node) event.getSource();
        Stage stage = (Stage) source.getScene().getWindow();
        stage.close();
    }

}
