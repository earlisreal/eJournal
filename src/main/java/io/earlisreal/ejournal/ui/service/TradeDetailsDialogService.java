package io.earlisreal.ejournal.ui.service;

import io.earlisreal.ejournal.model.TradeSummary;
import io.earlisreal.ejournal.service.PlotService;
import io.earlisreal.ejournal.ui.controller.TradeDetailsController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.TableRow;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.concurrent.CompletableFuture;

import static io.earlisreal.ejournal.util.CommonUtil.handleException;

public class TradeDetailsDialogService {

    private final PlotService plotService;
    private final Stage stage;
    private final TradeDetailsController controller;

    TradeDetailsDialogService(PlotService plotService) {
        this.plotService = plotService;
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/dialog/trade-details.fxml"));
        Scene scene = null;

        try {
            scene = new Scene(loader.load());
        } catch (IOException e) {
            // TODO : This should never happen
            handleException(e);
        }

        stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setScene(scene);
        controller = loader.getController();
    }

    public TableRow<TradeSummary> getTableRow() {
        TableRow<TradeSummary> row = new TableRow<>();
        row.setOnMouseClicked(event -> UIServiceProvider.getTradeDetailsDialogService().show(row.getItem()));
        return row;
    }

    public void show(TradeSummary summary) {
        controller.initialize(summary);
        stage.setTitle(summary.getStock());
        stage.show();

        CompletableFuture.supplyAsync(() -> {
            controller.showLoading();
            try {
                return plotService.plot(summary);
            } catch (IOException e) {
                handleException(e);
            }
            return null;
        }).thenAccept(imageUrl -> {
            if (imageUrl == null) return;
            try {
                controller.updateImage(imageUrl.toUri().toURL().toString());
            } catch (MalformedURLException e) {
                handleException(e);
            }
        });
    }

}
