package io.earlisreal.ejournal.ui.controller;

import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.model.TradeSummary;
import io.earlisreal.ejournal.service.DataService;
import io.earlisreal.ejournal.service.IntradayService;
import io.earlisreal.ejournal.service.ServiceProvider;
import io.earlisreal.ejournal.service.StockService;
import io.earlisreal.ejournal.service.SummaryDetailService;
import io.earlisreal.ejournal.ui.service.ChartService;
import io.earlisreal.ejournal.ui.service.UIServiceProvider;
import io.earlisreal.ejournal.util.Interval;
import io.earlisreal.ejournal.util.Pair;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Worker;
import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import static io.earlisreal.ejournal.util.CommonUtil.prettify;
import static io.earlisreal.ejournal.util.CommonUtil.round;
import static io.earlisreal.ejournal.util.CommonUtil.runAsync;

public class TradeDetailsController implements Initializable {

    public static final DateTimeFormatter AV_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");
    private static final String SELECTED_RATING = "selected";

    private final StockService stockService;
    private final SummaryDetailService detailService;
    private final IntradayService intradayService;
    private final DataService dataService;
    private ChartService chartService;

    public TableView<TradeLog> logTable;
    public TableColumn<TradeLog, String> logDate;
    public TableColumn<TradeLog, String> logAction;
    public TableColumn<TradeLog, String> logPrice;
    public TableColumn<TradeLog, String> logShares;
    public TableColumn<TradeLog, String> logFees;
    public TableColumn<TradeLog, String> logNet;
    public TableColumn<TradeLog, String> logProfit;
    public AnchorPane anchorPane;
    public ProgressIndicator loadingProgress;
    public Label loadingLabel;

    public TableView<List<Pair<String, String>>> statisticTable;
    public TableColumn<List<Pair<String, String>>, String> statisticColumn;
    public TableColumn<List<Pair<String, String>>, String> valueColumn;
    public TableColumn<List<Pair<String, String>>, String> statisticColumn1;
    public TableColumn<List<Pair<String, String>>, String> valueColumn1;
    public TableColumn<List<Pair<String, String>>, String> statisticColumn2;
    public TableColumn<List<Pair<String, String>>, String> valueColumn2;
    public Button nextButton;
    public Button previousButton;
    public Label ofLabel;
    public TextArea remarksTextArea;
    public HBox ratingHBox;
    public WebView webView;
    public Button resetButton;
    public Button oneMinuteButton;
    public Button fiveMinuteButton;
    public Button dailyButton;

    private WebEngine webEngine;
    private List<TradeSummary> summaries;
    private int index;
    private Interval interval;
    private boolean chartReady;

    public TradeDetailsController() {
        stockService = ServiceProvider.getStockService();
        detailService = ServiceProvider.getSummaryDetailService();
        intradayService = ServiceProvider.getIntradayService();
        dataService = ServiceProvider.getDataService();

        summaries = new ArrayList<>();
        interval = Interval.ONE_MINUTE;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        disableButtons();
        showLoading();
        webEngine = webView.getEngine();
        chartService = UIServiceProvider.getChartService(webEngine);

        var html = getClass().getResource("/chart.html");
        try {
            assert html != null;
            webEngine.loadContent(new String(html.openStream().readAllBytes()));
        } catch (IOException e) {
            e.printStackTrace();
        }

        webEngine.getLoadWorker().stateProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == Worker.State.SUCCEEDED) {
                chartReady = true;
                if (!summaries.isEmpty()) {
                    updateChartData(getCurrentSummary());
                }
            }
        });
        remarksTextArea.focusedProperty().addListener((observableValue, oldValue, newValue) -> {
            if (!newValue) {
                detailService.saveRemarks(getCurrentSummary().getId(), remarksTextArea.getText());
            }
        });
        Text undo = GlyphsDude.createIcon(FontAwesomeIcon.UNDO, "20px");
        resetButton.setText("");
        resetButton.setGraphic(undo);
    }

    public void setSummaries(List<TradeSummary> summaries) {
        this.summaries = summaries;
        boolean disabled = summaries.size() == 1;
        nextButton.setDisable(disabled);
        previousButton.setDisable(disabled);
    }

    public void navigate(KeyEvent keyEvent) {
        if (keyEvent.getCode().equals(KeyCode.LEFT)) previousButton.fire();
        if (keyEvent.getCode().equals(KeyCode.RIGHT)) nextButton.fire();
    }

    public void nextTrade() {
        index = (index + 1) % summaries.size();
        show();
    }

    public void previousTrade() {
        if (--index < 0) index = summaries.size() - 1;
        show();
    }

    public void show(TradeSummary summary) {
        index = summaries.indexOf(summary);
        show();
    }

    public void show() {
        var summary = getCurrentSummary();
        updateChartData(summary);
        initializeStatistics(summary);
        initializeLogs(summary);
        initializeDetails(summary);

        ofLabel.setText(index + 1 + " of " + summaries.size() + " Trade" + (summaries.size() > 1 ? "s" : ""));
    }

    private void initializeDetails(TradeSummary summary) {
        var detail = detailService.getSummaryDetail(summary.getId());
        String remarks = "";
        int rating = 0;
        if (detail.isPresent()) {
            remarks = detail.get().getRemarks();
            rating = detail.get().getRating();
        }
        updateRatingLayout(rating);
        remarksTextArea.setText(remarks);
    }

    private void initializeStatistics(TradeSummary summary) {
        List<Pair<String, String>> list = new ArrayList<>();
        list.add(new Pair<>("Stock", summary.getStock()));
        list.add(new Pair<>("Name", stockService.getName(summary.getStock())));
        list.add(new Pair<>("Type", summary.getTradeType()));

        list.add(new Pair<>("Open", prettify(summary.getOpenDate())));
        if (summary.isClosed()) {
            list.add(new Pair<>("Closed", prettify(summary.getCloseDate())));
            list.add(new Pair<>("Holding Period", String.valueOf(summary.getHoldingPeriod())));

            list.add(new Pair<>("Total Shares", prettify(summary.getShares())));
            list.add(new Pair<>("Position", prettify(summary.getPosition())));
            list.add(new Pair<>("Profit %", prettify(summary.getProfitPercentage()) + "%"));

            list.add(new Pair<>("Average Buy", prettify(summary.getAverageBuy())));
            list.add(new Pair<>("Average Sell", prettify(summary.getAverageSell())));
            list.add(new Pair<>("Profit", prettify(summary.getProfit())));
        }
        else {
            String hold = "";
            Period period = summary.getOpenDate().toLocalDate().until(LocalDate.now());
            if (period.getYears() > 0) {
                hold += period.getYears() + " Years ";
            }
            if (period.getMonths() > 0) {
                hold += period.getMonths() + " Months ";
            }
            hold += period.getDays() + " Days";

            double cost = summary.getAverageBuy() * summary.getRemainingShares();
            double unrealizedProfit = stockService.getPrice(summary.getStock()) * summary.getRemainingShares() - cost;

            double soldShares = summary.getShares() - summary.getRemainingShares();
            double profit = ((summary.getTotalSell() / soldShares) - summary.getAverageBuy()) * soldShares;

            list.add(new Pair<>("Holding Days", hold));
            list.add(new Pair<>("Unrealized Profit", prettify(unrealizedProfit)));

            list.add(new Pair<>("Total Shares", prettify(summary.getShares())));
            list.add(new Pair<>("Position", prettify(summary.getPosition())));
            list.add(new Pair<>("Unrealized Profit %", round(unrealizedProfit / cost * 100) + "%"));

            list.add(new Pair<>("Average Buy", prettify(summary.getAverageBuy())));
            list.add(new Pair<>("Realized Profit", prettify(profit)));
            list.add(new Pair<>("Realized Profit %", round(profit / (soldShares * summary.getAverageBuy()) * 100) + "%"));
        }

        List<List<Pair<String, String>>> pairs = new ArrayList<>();
        List<Pair<String, String>> pair = new ArrayList<>();
        for (int i = 0; i < list.size(); ++i) {
            if (i % 3 == 0) {
                pair = new ArrayList<>();
                pairs.add(pair);
            }
            pair.add(list.get(i));
        }

        statisticTable.setItems(FXCollections.observableList(pairs));
        statisticColumn.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().get(0).getT()));
        valueColumn.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().get(0).getU()));
        statisticColumn1.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().get(1).getT()));
        valueColumn1.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().get(1).getU()));
        statisticColumn2.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().get(2).getT()));
        valueColumn2.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().get(2).getU()));
    }

    private void initializeLogs(TradeSummary tradeSummary) {
        logTable.setItems(FXCollections.observableList(tradeSummary.getLogs()));
        logDate.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getDate())));
        logAction.setCellValueFactory(t -> new SimpleStringProperty(t.getValue().getAction()));
        logPrice.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getPrice())));
        logShares.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getShares())));
        logNet.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getNetAmount())));
        logFees.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getFees())));
        logProfit.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getProfit())));
    }

    public void showLoading() {
        loadingLabel.setVisible(true);
        loadingProgress.setVisible(true);
        webView.setVisible(false);
    }

    public void hideLoading() {
        webView.setVisible(true);
        loadingLabel.setVisible(false);
        loadingProgress.setVisible(false);
    }

    private TradeSummary getCurrentSummary() {
        return summaries.get(index);
    }

    public void updateRating(ActionEvent actionEvent) {
        int rating = ratingHBox.getChildren().indexOf((Button) actionEvent.getSource()) + 1;
        updateRatingLayout(rating);

        detailService.saveRating(getCurrentSummary().getId(), rating);
    }

    private void updateRatingLayout(int rating) {
        var children = ratingHBox.getChildren();
        for (int i = 0; i < children.size(); ++i) {
            var styleClass = children.get(i).getStyleClass();
            boolean isSelected = styleClass.contains(SELECTED_RATING);
            if (i < rating) {
                if (!isSelected) styleClass.add(SELECTED_RATING);
            }
            else {
                if (isSelected) styleClass.remove(SELECTED_RATING);
            }
        }
    }

    private void updateChartData(TradeSummary summary) {
        if (!chartReady) {
            return;
        }

        chartService.setSummary(summary);
        if (summary.isDayTrade()) {
            if (!chartService.isIntradayAvailable()) {
                intradayService.download(List.of(summary), this::notifyNewSummaries);
            }
        }
        else {
            if (interval.isIntraDay()) {
                interval = Interval.DAILY;
            }
            if (!chartService.isDailyAvailable()) {
                runAsync(() -> dataService.downloadDailyData(List.of(summary)));
            }
        }
        chartService.setInterval(interval);
        updateButtons();
        hideLoading();
    }

    public void setInterval(ActionEvent actionEvent) {
        Node node = (Node) actionEvent.getSource();
        interval = Interval.valueOf((String) node.getUserData());
        chartService.setInterval(interval);
    }

    private void updateButtons() {
        dailyButton.setDisable(!chartService.isDailyAvailable());
        oneMinuteButton.setDisable(!chartService.isIntradayAvailable());
        fiveMinuteButton.setDisable(!chartService.isIntradayAvailable());
    }

    private void disableButtons() {
        dailyButton.setDisable(true);
        oneMinuteButton.setDisable(true);
        fiveMinuteButton.setDisable(true);
    }

    public void resetChart() {
        webEngine.executeScript("chart.timeScale().resetTimeScale()");
        chartService.setInterval(interval);
        updateButtons();
    }

    public void notifyNewSummaries(List<TradeSummary> summaries) {
        if (this.summaries.isEmpty()) {
            return;
        }

        for (TradeSummary summary : summaries) {
            if (summary.equals(getCurrentSummary())) {
                Platform.runLater(() -> updateChartData(summary));
                return;
            }
        }
    }

    public void notifyNewDailyData(TradeSummary summary) {
        dailyButton.setDisable(false);
        notifyNewSummaries(List.of(summary));
    }

}
