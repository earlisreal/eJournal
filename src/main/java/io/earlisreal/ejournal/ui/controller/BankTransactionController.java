package io.earlisreal.ejournal.ui.controller;

import io.earlisreal.ejournal.dto.BankTransaction;
import io.earlisreal.ejournal.service.BankTransactionService;
import io.earlisreal.ejournal.service.ServiceProvider;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class BankTransactionController implements Initializable {

    public TableColumn<BankTransaction, String> dateColumn;
    public TableColumn<BankTransaction, String> actionColumn;
    public TableColumn<BankTransaction, String> amountColumn;
    public TableView<BankTransaction> bankTable;

    private BankTransactionService service;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        service = ServiceProvider.getBankTransactionService();
        loadBankTransactions();
    }

    public void deposit(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/dialog/bank-transaction.fxml"));
            Parent dialog = loader.load();
            Scene scene = new Scene(dialog);
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(scene);
            BankTransactionDialogController controller = loader.getController();
            controller.setBankTransactionController(this);

            stage.setTitle("Dialog");
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadBankTransactions() {
        bankTable.setItems(FXCollections.observableList(service.getAll()));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("date"));
        amountColumn.setCellValueFactory(new PropertyValueFactory<>("amount"));
        actionColumn.setCellValueFactory(new PropertyValueFactory<>("action"));
    }

}
