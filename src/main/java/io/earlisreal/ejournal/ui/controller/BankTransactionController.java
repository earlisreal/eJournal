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
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class BankTransactionController implements Initializable {

    public TableColumn<BankTransaction, String> dateColumn;
    public TableColumn<BankTransaction, String> actionColumn;
    public TableColumn<BankTransaction, String> amountColumn;
    public TableView<BankTransaction> bankTable;
    public TableColumn<BankTransaction, Void> deleteColumn;

    private BankTransactionService service;
    private Stage dialogStage;
    private BankTransactionDialogController dialogController;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        service = ServiceProvider.getBankTransactionService();
        loadBankTransactions();

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/dialog/bank-transaction.fxml"));
            Parent dialog = loader.load();
            Scene scene = new Scene(dialog);
            dialogStage = new Stage();
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.setScene(scene);
            dialogController = loader.getController();
            dialogController.setBankTransactionController(this);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void deposit(ActionEvent event) {
        dialogStage.setTitle("Add Deposit Transaction");
        dialogController.setWithdraw(false);
        showDialog();
    }

    public void withdraw(ActionEvent event) {
        dialogStage.setTitle("Add Withdraw Transaction");
        dialogController.setWithdraw(true);
        showDialog();
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

    public void showDialog() {
        dialogStage.show();
    }

    public void closeDialog() {
        dialogStage.hide();
    }

}
