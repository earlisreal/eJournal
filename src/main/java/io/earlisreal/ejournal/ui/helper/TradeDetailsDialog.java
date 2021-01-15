package io.earlisreal.ejournal.ui.helper;

import io.earlisreal.ejournal.model.TradeSummary;
import io.earlisreal.ejournal.service.PlotService;
import io.earlisreal.ejournal.service.ServiceProvider;
import io.earlisreal.ejournal.ui.controller.TradeDetailsController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.concurrent.CompletableFuture;

import static io.earlisreal.ejournal.util.CommonUtil.handleException;

public class TradeDetailsDialog {

    private static TradeDetailsDialog instance;

    private final PlotService plotService;
    private final Stage stage;
    private final TradeDetailsController controller;

    public static TradeDetailsDialog getInstance() throws IOException {
        if (instance == null) {
            synchronized (TradeDetailsDialog.class) {
                if (instance == null) {
                    instance = new TradeDetailsDialog();
                }
            }
        }

        return instance;
    }

    private TradeDetailsDialog() throws IOException {
        plotService = ServiceProvider.getPlotService();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/dialog/trade-details.fxml"));
        Scene scene = new Scene(loader.load());

        stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setScene(scene);
        controller = loader.getController();
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
