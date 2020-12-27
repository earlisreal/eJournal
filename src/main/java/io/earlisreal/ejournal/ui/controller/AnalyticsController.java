package io.earlisreal.ejournal.ui.controller;

import io.earlisreal.ejournal.service.AnalyticsService;
import io.earlisreal.ejournal.service.ServiceProvider;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;

import java.net.URL;
import java.util.ResourceBundle;

import static io.earlisreal.ejournal.util.CommonUtil.round;

public class AnalyticsController implements Initializable {

    public Label analyticsLabel;

    private AnalyticsService service;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        service = ServiceProvider.getAnalyticsService();
        String ratio = "Edge Ratio: " + round(service.getEdgeRatio()) + "\n";
        String profit = "Average Profit: " + round(service.getAverageProfit()) + " " + service.getAverageProfitPercentage() + "%\n";
        String loss = "Average Loss: " + round(service.getAverageLoss()) + " " + service.getAverageLossPercentage() + "%\n";
        String accuracy = "Accuracy: " + service.getAccuracy() + "%";
        analyticsLabel.setText(ratio + profit + loss + accuracy);
    }

}
