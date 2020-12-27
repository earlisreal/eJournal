package io.earlisreal.ejournal.ui.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class BankTransactionController implements Initializable {

    @Override
    public void initialize(URL location, ResourceBundle resources) {
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
        System.out.println("loading");
    }

}
