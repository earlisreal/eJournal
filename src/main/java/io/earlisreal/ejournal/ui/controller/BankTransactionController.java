package io.earlisreal.ejournal.ui.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;

public class BankTransactionController {

    public void deposit(ActionEvent event) {
        try {
            Parent dialog = FXMLLoader.load(getClass().getResource("/fxml/dialog/bank-transaction.fxml"));
            Scene scene = new Scene(dialog);
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(scene);

            stage.setTitle("Dialog");
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
