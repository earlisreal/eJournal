package io.earlisreal.ejournal.ui.service;

import com.google.api.client.util.Strings;
import io.earlisreal.ejournal.model.TradeSummary;
import io.earlisreal.ejournal.ui.controller.TradeDetailsController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.TableRow;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static io.earlisreal.ejournal.util.CommonUtil.handleException;

public class TradeDetailsDialogService {

    private final Stage stage;
    private final TradeDetailsController controller;

    TradeDetailsDialogService() {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/trade-details.fxml"));
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

    public TableRow<TradeSummary> getTableRow(List<TradeSummary> summaries) {
        return getTableRow(summaries, "All Trades");
    }

    public TableRow<TradeSummary> getTableRow(List<TradeSummary> summaries, String title) {
        TableRow<TradeSummary> row = new TableRow<>();
        row.setOnMouseClicked(event -> show(row.getItem(), summaries, title));
        return row;
    }

    public void show(TradeSummary summary, List<TradeSummary> summaries, String title) {
        if (summary == null) return;

        controller.setSummaries(summaries);
        controller.show(summary);

        stage.setTitle("Trade Summary" + (Strings.isNullOrEmpty(title) ? "" : " - " + title));
        stage.setResizable(false);
        stage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/eJournal-logo.png"))));
        stage.show();
    }

    public void showIfActive(List<TradeSummary> summaries) {
        controller.notifyNewSummaries(summaries);
    }

}
