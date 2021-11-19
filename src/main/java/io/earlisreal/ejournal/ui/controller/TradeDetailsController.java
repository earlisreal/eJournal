package io.earlisreal.ejournal.ui.controller;

import com.jsoniter.output.JsonStream;
import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.model.CandleStickSeriesData;
import io.earlisreal.ejournal.model.MarkerData;
import io.earlisreal.ejournal.model.TradeSummary;
import io.earlisreal.ejournal.model.VolumeData;
import io.earlisreal.ejournal.service.ServiceProvider;
import io.earlisreal.ejournal.service.StockService;
import io.earlisreal.ejournal.service.SummaryDetailService;
import io.earlisreal.ejournal.util.Pair;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import static io.earlisreal.ejournal.util.CommonUtil.handleException;
import static io.earlisreal.ejournal.util.CommonUtil.prettify;
import static io.earlisreal.ejournal.util.CommonUtil.round;
import static io.earlisreal.ejournal.util.Configs.STOCKS_DIRECTORY;

public class TradeDetailsController implements Initializable {

    public static final DateTimeFormatter AV_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");
    private static final String SELECTED_RATING = "selected";

    private final StockService stockService;
    private final SummaryDetailService detailService;

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

    private WebEngine webEngine;
    private List<TradeSummary> summaries;
    private int index;
    private String seriesJson;
    private String fiveMinuteSeriesJson;
    private String volumeJson;
    private String fiveMinuteVolumeJson;
    private String markerJson;
    private int scrollPosition;

    public TradeDetailsController() {
        stockService = ServiceProvider.getStockService();
        detailService = ServiceProvider.getSummaryDetailService();

        summaries = new ArrayList<>();
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        webEngine = webView.getEngine();
        Path chartPath = Paths.get("chart/chart.html").toAbsolutePath();
        if (!Files.exists(chartPath)) {
            throw new RuntimeException(chartPath + " Not Found");
        }

        webEngine.load(chartPath.toUri().toString());
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
        updateChartData(getCurrentSummary());

        initializeStatistics(getCurrentSummary());
        initializeLogs(getCurrentSummary());
        initializeDetails(getCurrentSummary());

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
        String symbol = summary.getStock();
        var dataPath = STOCKS_DIRECTORY.resolve(summary.getCountry().name()).resolve(symbol + ".csv");
        if (!Files.exists(dataPath) || stockService.getLastPriceDate(symbol).isBefore(summary.getCloseDate().toLocalDate())) {
            System.out.println("Data not yet available");
            showLoading();
            return;
        }

        List<CandleStickSeriesData> seriesDataList = new ArrayList<>();
        List<CandleStickSeriesData> fiveMinuteSeries = new ArrayList<>();
        List<VolumeData> volumeDataList = new ArrayList<>();
        List<VolumeData> fiveMinuteVolumes = new ArrayList<>();
        CandleStickSeriesData fiveMinuteData = new CandleStickSeriesData();
        VolumeData fiveMinuteVolumeData = new VolumeData();
        try {
            for (var line : Files.readAllLines(dataPath)) {
                String[] tokens = line.split(",");
                LocalDateTime localDateTime = LocalDateTime.parse(tokens[0], AV_FORMATTER);
                if (!summary.getCloseDate().toLocalDate().equals(localDateTime.toLocalDate())) {
                    continue;
                }

                long epochSecond = localDateTime.toEpochSecond(ZoneOffset.UTC);
                CandleStickSeriesData data = toSeriesData(tokens, epochSecond);

                if (localDateTime.getMinute() % 5 == 0) {
                    fiveMinuteVolumeData.setTime(epochSecond);
                    fiveMinuteVolumeData.setColor(getVolumeColor(fiveMinuteData));
                    fiveMinuteVolumes.add(fiveMinuteVolumeData);
                    fiveMinuteVolumeData = new VolumeData();

                    fiveMinuteData.setTime(epochSecond);
                    fiveMinuteSeries.add(fiveMinuteData);
                    fiveMinuteData = new CandleStickSeriesData();
                }

                if ((localDateTime.getMinute() + 1) % 5 == 0) {
                    if (data.getClose() == 0) {
                        fiveMinuteData.setClose(fiveMinuteData.getOpen());
                    }
                    fiveMinuteData.setClose(data.getClose());
                }

                if (fiveMinuteData.getOpen() == 0) {
                    fiveMinuteData.setOpen(data.getOpen());
                    fiveMinuteData.setLow(data.getLow());
                }

                fiveMinuteData.setHigh(Math.max(fiveMinuteData.getHigh(), data.getHigh()));
                fiveMinuteData.setLow(Math.min(fiveMinuteData.getLow(), data.getLow()));

                VolumeData volumeData = toVolumeData(tokens, data, epochSecond);
                fiveMinuteVolumeData.setValue(fiveMinuteVolumeData.getValue() + volumeData.getValue());

                seriesDataList.add(data);
                volumeDataList.add(volumeData);
            }
        } catch (IOException e) {
            handleException(e);
        }

        List<MarkerData> markerDataList = generateMarkers(summary);
        scrollPosition = calculateScrollPosition(summary, seriesDataList);

        seriesJson = JsonStream.serialize(seriesDataList);
        fiveMinuteSeriesJson = JsonStream.serialize(fiveMinuteSeries);
        volumeJson = JsonStream.serialize(volumeDataList);
        fiveMinuteVolumeJson = JsonStream.serialize(fiveMinuteVolumes);
        markerJson = JsonStream.serialize(markerDataList);

        set5MinuteChart();
        resetChart();

        hideLoading();
    }

    private CandleStickSeriesData toSeriesData(String[] tokens, long epochSecond) {
        CandleStickSeriesData data = new CandleStickSeriesData();
        data.setTime(epochSecond);
        data.setOpen(Double.parseDouble(tokens[1]));
        data.setHigh(Double.parseDouble(tokens[2]));
        data.setLow(Double.parseDouble(tokens[3]));
        data.setClose(Double.parseDouble(tokens[4]));
        return data;
    }

    private VolumeData toVolumeData(String[] tokens, CandleStickSeriesData seriesData, long epochSecond) {
        VolumeData data = new VolumeData();
        data.setTime(epochSecond);
        data.setValue(Double.parseDouble(tokens[5]));
        data.setColor(getVolumeColor(seriesData));
        return data;
    }

    private String getVolumeColor(CandleStickSeriesData data) {
        if (data.getClose() >= data.getOpen()) {
            return VolumeData.GREEN;
        }
        return VolumeData.RED;
    }

    private List<MarkerData> generateMarkers(TradeSummary summary) {
        List<MarkerData> markerDataList = new ArrayList<>();
        for (TradeLog log : summary.getLogs()) {
            MarkerData data = new MarkerData();
            data.setTime(log.getDate().toEpochSecond(ZoneOffset.UTC));
            String color = MarkerData.BUY_COLOR;
            if (!log.isBuy()) {
                color = log.isShort() ? MarkerData.SHORT_COLOR : MarkerData.SELL_COLOR;
            }
            data.setPosition(log.getPrice());
            data.setColor(color);
            data.setShape("diamond");
            data.setBorderWidth(0.32);
            data.setSize(1.65);
            markerDataList.add(data);
        }
        return markerDataList;
    }

    private int calculateScrollPosition(TradeSummary summary, List<CandleStickSeriesData> seriesDataList) {
        long first = summary.getLogs().get(0).getDate().truncatedTo(ChronoUnit.MINUTES).toEpochSecond(ZoneOffset.UTC);
        int position = 0;
        for (int i = 0; i < seriesDataList.size(); ++i) {
            if (seriesDataList.get(i).getTime() == first) {
                position = i - seriesDataList.size();
                break;
            }
        }
        return position + 40;
    }

    public void set1MinuteChart() {
        webEngine.executeScript(String.format("setData(%s, %s)", seriesJson, volumeJson));
        webEngine.executeScript(String.format("series.setMarkers(%s)", markerJson));
    }

    public void set5MinuteChart() {
        webEngine.executeScript(String.format("setData(%s, %s)", fiveMinuteSeriesJson, fiveMinuteVolumeJson));
        webEngine.executeScript(String.format("series.setMarkers(%s)", markerJson));
    }

    public void resetChart() {
        webEngine.executeScript(String.format("chart.timeScale().scrollToPosition(%d, false)", scrollPosition));
    }

    public void notifyNewSummaries(List<TradeSummary> summaries) {
        for (TradeSummary summary : summaries) {
            if (summary.equals(getCurrentSummary())) {
                System.out.println("Updating current summary");
                updateChartData(summary);
                return;
            }
        }
    }

}
