package io.earlisreal.ejournal.ui.controller;

import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MainController implements Initializable {

    public GridPane grid;
    public StackPane stackPane;

    private Parent log;
    private Parent analytics;
    private Parent strategy;
    private Parent bankTransaction;
    private List<Parent> parents;
    private ObservableList<Node> children;
    private Map<String, Parent> parentMap;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            log = FXMLLoader.load(getClass().getResource("/fxml/log.fxml"));
            analytics = FXMLLoader.load(getClass().getResource("/fxml/analytics.fxml"));
            strategy = FXMLLoader.load(getClass().getResource("/fxml/strategy.fxml"));
            bankTransaction = FXMLLoader.load(getClass().getResource("/fxml/bank-transaction.fxml"));
        } catch (IOException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }

        children = stackPane.getChildren();
        children.add(log);
    }

    public void showAnalytics(ActionEvent event) {
        children.clear();
        children.add(analytics);
    }

    public void showLog(ActionEvent event) {
        children.clear();
        children.add(log);
    }

    public void showBankTransaction(ActionEvent event) {
        children.clear();
        children.add(bankTransaction);
    }

    public void showStrategy(ActionEvent event) {
        children.clear();
        children.add(strategy);
    }

}
