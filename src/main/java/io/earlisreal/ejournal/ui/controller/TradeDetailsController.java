package io.earlisreal.ejournal.ui.controller;

import com.jsoniter.output.JsonStream;
import de.jensd.fx.glyphs.GlyphsDude;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.model.CandleStickSeriesData;
import io.earlisreal.ejournal.model.LineData;
import io.earlisreal.ejournal.model.MarkerData;
import io.earlisreal.ejournal.model.TradeSummary;
import io.earlisreal.ejournal.model.VolumeData;
import io.earlisreal.ejournal.service.ServiceProvider;
import io.earlisreal.ejournal.service.StockService;
import io.earlisreal.ejournal.service.SummaryDetailService;
import io.earlisreal.ejournal.util.Interval;
import io.earlisreal.ejournal.util.Pair;
import javafx.application.Platform;
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

import javax.sound.sampled.Line;
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
import java.util.concurrent.TimeUnit;

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
    public Button oneMinuteButton;
    public Button fiveMinuteButton;
    public Button dailyButton;

    private WebEngine webEngine;
    private List<TradeSummary> summaries;
    private int index;
    private String seriesJson;
    private String fiveMinuteSeriesJson;
    private String dailySeriesJson;
    private String volumeJson;
    private String fiveMinuteVolumeJson;
    private String dailyVolumeJson;
    private String markerJson;
    private String fiveMinuteMarkerJson;
    private String dailyMarkerJson;
    private int scrollPosition;
    private int fiveMinuteScrollPosition;
    private int dailyScrollPosition;
    private Interval interval;
    private boolean isIntradayNotAvailable;
    private boolean isDailyNotAvailable;
    private String vwapJson;

    public TradeDetailsController() {
        stockService = ServiceProvider.getStockService();
        detailService = ServiceProvider.getSummaryDetailService();

        summaries = new ArrayList<>();
        interval = Interval.ONE_MINUTE;
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
        if (summary.isDayTrade()) {
            updateIntradayData(summary);
        }
        else {
            interval = Interval.DAILY;
        }
        updateDailyData(summary);

        resetChart();
        hideLoading();
    }

    private void updateIntradayData(TradeSummary summary) {
        isIntradayNotAvailable = false;
        String symbol = summary.getStock();
        var dataPath = STOCKS_DIRECTORY.resolve(summary.getCountry().name()).resolve(symbol + ".csv");
        if (!Files.exists(dataPath) || stockService.getLastPriceDate(symbol).isBefore(summary.getCloseDate().toLocalDate())) {
            isIntradayNotAvailable = true;
            showLoading();
            return;
        }

        List<CandleStickSeriesData> seriesDataList = new ArrayList<>();
        List<CandleStickSeriesData> fiveMinuteSeries = new ArrayList<>();
        List<VolumeData> volumeDataList = new ArrayList<>();
        List<VolumeData> fiveMinuteVolumes = new ArrayList<>();
        CandleStickSeriesData fiveMinuteData = null;
        VolumeData fiveMinuteVolumeData = null;
        List<LineData> vwapList = new ArrayList<>();
        double runningVolume = 0;
        double runningTpv = 0;
        LocalDate lastDate = null;
        try {
            for (var line : Files.readAllLines(dataPath)) {
                String[] tokens = line.split(",");
                LocalDateTime localDateTime = LocalDateTime.parse(tokens[0], AV_FORMATTER);
                localDateTime = localDateTime.minusMinutes(1);
                LocalDate localDate = localDateTime.toLocalDate();
                if (summary.getCloseDate().toLocalDate().minusDays(6).isAfter(localDate)) {
                    continue;
                }

                if (!localDate.equals(lastDate)) {
                    lastDate = localDate;
                    runningTpv = 0;
                    runningVolume = 0;
                }

                long epochSecond = localDateTime.toEpochSecond(ZoneOffset.UTC);
                CandleStickSeriesData data = toSeriesData(tokens, epochSecond);
                VolumeData volumeData = toVolumeData(tokens, data, epochSecond);
                double tpv = (data.getHigh() + data.getLow() + data.getClose()) / 3 * volumeData.getValue();
                runningTpv += tpv;
                runningVolume += volumeData.getValue();
                vwapList.add(new LineData(epochSecond, runningTpv / runningVolume));

                if (fiveMinuteData == null) {
                    fiveMinuteData = new CandleStickSeriesData();
                    fiveMinuteVolumeData = new VolumeData();
                    fiveMinuteVolumeData.setTime(epochSecond);
                    fiveMinuteData.setTime(epochSecond);
                    fiveMinuteData.setOpen(data.getOpen());
                    fiveMinuteData.setLow(data.getLow());
                }

                fiveMinuteData.setClose(data.getClose());
                fiveMinuteData.setHigh(Math.max(fiveMinuteData.getHigh(), data.getHigh()));
                fiveMinuteData.setLow(Math.min(fiveMinuteData.getLow(), data.getLow()));

                fiveMinuteVolumeData.setValue(fiveMinuteVolumeData.getValue() + volumeData.getValue());

                seriesDataList.add(data);
                volumeDataList.add(volumeData);

                if ((localDateTime.getMinute() + 1) % 5 == 0) {
                    fiveMinuteVolumeData.setColor(getVolumeColor(fiveMinuteData));
                    fiveMinuteVolumes.add(fiveMinuteVolumeData);
                    fiveMinuteSeries.add(fiveMinuteData);
                    fiveMinuteData = null;
                    fiveMinuteVolumeData = null;
                }
            }
        } catch (IOException e) {
            handleException(e);
        }

        List<MarkerData> markerDataList = generateMarkers(summary, 1);
        List<MarkerData> fiveMinuteMarkers = generateMarkers(summary, 5);
        scrollPosition = calculateScrollPosition(summary, seriesDataList);
        fiveMinuteScrollPosition = calculateScrollPosition(summary, fiveMinuteSeries);

        seriesJson = JsonStream.serialize(seriesDataList);
        fiveMinuteSeriesJson = JsonStream.serialize(fiveMinuteSeries);
        volumeJson = JsonStream.serialize(volumeDataList);
        fiveMinuteVolumeJson = JsonStream.serialize(fiveMinuteVolumes);
        markerJson = JsonStream.serialize(markerDataList);
        fiveMinuteMarkerJson = JsonStream.serialize(fiveMinuteMarkers);
        vwapJson = JsonStream.serialize(vwapList);
    }

    private void updateDailyData(TradeSummary summary) {
        String symbol = summary.getStock();
        var dataPath = STOCKS_DIRECTORY.resolve(summary.getCountry().name()).resolve("daily").resolve(symbol + ".csv");
        isDailyNotAvailable = false;
        if (!Files.exists(dataPath) || stockService.getLastPriceDate(symbol).isBefore(summary.getCloseDate().toLocalDate())) {
            isDailyNotAvailable = true;
            showLoading();
            return;
        }

        List<CandleStickSeriesData> seriesDataList = new ArrayList<>();
        List<VolumeData> volumeDataList = new ArrayList<>();
        try {
            for (var line : Files.readAllLines(dataPath)) {
                String[] tokens = line.split(",");
                LocalDate localDate = LocalDate.parse(tokens[0]);

                long epochSecond = localDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
                CandleStickSeriesData data = toSeriesData(tokens, epochSecond);
                VolumeData volumeData = toVolumeData(tokens, data, epochSecond);

                seriesDataList.add(data);
                volumeDataList.add(volumeData);
            }
        } catch (IOException e) {
            handleException(e);
        }

        List<MarkerData> markerDataList = generateMarkers(summary, TimeUnit.DAYS.toMinutes(1));
        dailyScrollPosition = calculateScrollPosition(summary, seriesDataList);

        dailySeriesJson = JsonStream.serialize(seriesDataList);
        dailyVolumeJson = JsonStream.serialize(volumeDataList);
        dailyMarkerJson = JsonStream.serialize(markerDataList);
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

    private List<MarkerData> generateMarkers(TradeSummary summary, long minusMinutes) {
        List<MarkerData> markerDataList = new ArrayList<>();
        for (TradeLog log : summary.getLogs()) {
            MarkerData data = new MarkerData();
            data.setTime(log.getDate().minusMinutes(minusMinutes).toEpochSecond(ZoneOffset.UTC));
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
            if (seriesDataList.get(i).getTime() >= first) {
                position = i - seriesDataList.size();
                break;
            }
        }
        return position + 40;
    }

    public void set1MinuteChart() {
        webEngine.executeScript(String.format("setData(%s, %s, %s)", seriesJson, volumeJson, vwapJson));
        webEngine.executeScript(String.format("series.setMarkers(%s)", markerJson));
        webEngine.executeScript(String.format("chart.timeScale().scrollToPosition(%d, false)", scrollPosition));
        webEngine.executeScript(String.format("updateTitle('%s', '1 Minute')", getCurrentSummary().getStock()));
        interval = Interval.ONE_MINUTE;
        updateButtons();
    }

    public void set5MinuteChart() {
        webEngine.executeScript(String.format("setData(%s, %s)", fiveMinuteSeriesJson, fiveMinuteVolumeJson));
        webEngine.executeScript(String.format("series.setMarkers(%s)", fiveMinuteMarkerJson));
        webEngine.executeScript(String.format("chart.timeScale().scrollToPosition(%d, false)", fiveMinuteScrollPosition));
        webEngine.executeScript(String.format("updateTitle('%s', '5 Minute')", getCurrentSummary().getStock()));
        interval = Interval.FIVE_MINUTE;
        updateButtons();
    }

    public void setDailyChart() {
        webEngine.executeScript(String.format("setData(%s, %s)", dailySeriesJson, dailyVolumeJson));
        webEngine.executeScript(String.format("series.setMarkers(%s)", dailyMarkerJson));
        webEngine.executeScript(String.format("chart.timeScale().scrollToPosition(%d, false)", dailyScrollPosition));
        webEngine.executeScript(String.format("updateTitle('%s', 'Daily')", getCurrentSummary().getStock()));
        interval = Interval.DAILY;
        updateButtons();
    }

    private void updateButtons() {
        dailyButton.setDisable(isDailyNotAvailable);
        oneMinuteButton.setDisable(isIntradayNotAvailable);
        fiveMinuteButton.setDisable(isIntradayNotAvailable);
        if (interval == Interval.DAILY) {
            dailyButton.setDisable(true);
        }
        else {
            if (interval == Interval.ONE_MINUTE) {
                oneMinuteButton.setDisable(true);
            }
            else {
                fiveMinuteButton.setDisable(true);
            }
        }
    }

    public void resetChart() {
        webEngine.executeScript("chart.timeScale().resetTimeScale()");
        if (interval == Interval.ONE_MINUTE) {
            set1MinuteChart();
        }
        if (interval == Interval.FIVE_MINUTE) {
            set5MinuteChart();
        }
        if (interval == Interval.DAILY) {
            setDailyChart();
        }
    }

    public void notifyNewSummaries(List<TradeSummary> summaries) {
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
