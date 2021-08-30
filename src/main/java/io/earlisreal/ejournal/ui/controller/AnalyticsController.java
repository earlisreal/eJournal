package io.earlisreal.ejournal.ui.controller;

import io.earlisreal.ejournal.model.TradeSummary;
import io.earlisreal.ejournal.service.AnalyticsService;
import io.earlisreal.ejournal.service.ServiceProvider;
import io.earlisreal.ejournal.ui.service.UIServiceProvider;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import static io.earlisreal.ejournal.util.CommonUtil.prettify;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;

public class AnalyticsController implements Initializable {

    public LineChart<String, Double> equityChart;
    public VBox successProfit;
    public VBox failLoss;
    public VBox successPercent;
    public VBox failPercent;
    public BarChart<String, Double> monthlyBarChart;
    public GridPane dailyGridPane;
    public HBox dailyHBox;
    public ChoiceBox<Integer> dailyYearChoice;
    public Label currentMonthLabel;

    private final AnalyticsService service;

    public AnalyticsController() {
        service = ServiceProvider.getAnalyticsService();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        var years = service.getSummaries().stream()
                .map(summary -> summary.getCloseDate().getYear())
                .distinct()
                .collect(Collectors.toList());
        dailyYearChoice.setItems(FXCollections.observableList(years));
        if (!years.isEmpty()) dailyYearChoice.setValue(years.get(0));
    }

    public void reload() {
        initializeEquityChart();
        initializeRemarkableTrades();
        initializeMonthlyChart();
        initializeDailyChart(LocalDate.now());
    }

    public void changeMonth(ActionEvent actionEvent) {
        Button button = (Button) actionEvent.getSource();
        Month month = Month.of(dailyHBox.getChildren().indexOf(button) + 1);
        var date = LocalDate.of(dailyYearChoice.getValue(), month, 1);
        initializeDailyChart(date);
    }

    private void initializeRemarkableTrades() {
        double maxProfit = 0;
        double maxProfitPercent = 0;
        double minProfit = 0;
        double minProfitPercent = 0;

        TradeSummary bestTradeProfit = null;
        TradeSummary bestTradePercent = null;
        TradeSummary worstTradeProfit = null;
        TradeSummary worstTradePercentage = null;
        for (TradeSummary summary : service.getWins()) {
            double profit = summary.getProfit();
            double percent = summary.getProfitPercentage();
            if (profit > maxProfit) {
                maxProfit = profit;
                bestTradeProfit = summary;
            }

            if (percent > maxProfitPercent) {
                maxProfitPercent = percent;
                bestTradePercent = summary;
            }
        }

        for (TradeSummary summary : service.getLosses()) {
            double lossPercent = summary.getProfitPercentage();
            double loss = summary.getProfit();
            if (lossPercent < minProfitPercent) {
                worstTradePercentage = summary;
                minProfitPercent = lossPercent;
            }

            if (loss < minProfit) {
                worstTradeProfit = summary;
                minProfit = loss;
            }
        }

        List<TradeSummary> summaries = new ArrayList<>();
        if (bestTradeProfit != null) summaries.add(bestTradeProfit);
        if (worstTradeProfit != null) summaries.add(worstTradeProfit);
        if (bestTradePercent != null) summaries.add(bestTradePercent);
        if (worstTradePercentage != null) summaries.add(worstTradePercentage);
        setTrade(successProfit, bestTradeProfit, summaries);
        setTrade(failLoss, worstTradeProfit, summaries);
        setTrade(successPercent, bestTradePercent, summaries);
        setTrade(failPercent, worstTradePercentage, summaries);
    }

    private void setTrade(VBox vBox, TradeSummary summary, List<TradeSummary> summaries) {
        Label stock = (Label) vBox.getChildren().get(0);
        Label percent = (Label) vBox.getChildren().get(1);
        Label value = (Label) vBox.getChildren().get(2);
        if (summary == null) {
            stock.setText("N/A");
            percent.setText("");
            value.setText("");
            vBox.setOnMouseClicked(null);
            return;
        }

        stock.setText(summary.getStock());
        percent.setText(prettify(summary.getProfitPercentage()) + "%");
        value.setText(prettify(summary.getProfit()));

        vBox.setOnMouseClicked(unused -> UIServiceProvider.getTradeDetailsDialogService().show(summary, summaries, "Remarkable Trades"));
    }

    private void initializeEquityChart() {
        XYChart.Series<String, Double> series = new XYChart.Series<>();
        series.setData(FXCollections.observableList(service.getEquityData()));
        equityChart.setData(FXCollections.observableList(List.of(series)));
    }

    private void initializeMonthlyChart() {
        XYChart.Series<String, Double> series = new XYChart.Series<>();
        series.setData(FXCollections.observableList(service.getMonthlyProfit()));
        monthlyBarChart.setData(FXCollections.observableList(List.of(series)));
    }

    private void initializeDailyChart(LocalDate date) {
        Month month = date.getMonth();
        currentMonthLabel.setText(month.getDisplayName(TextStyle.FULL, Locale.getDefault()));
        var map = service.getSummaries().stream()
                .filter(summary -> summary.getCloseDate() != null)
                .filter(summary -> summary.getCloseDate().getYear() == date.getYear())
                .filter(summary -> summary.getCloseDate().getMonth() == month)
                .sorted(Comparator.comparing(TradeSummary::getCloseDate))
                .collect(Collectors.groupingBy(tradeSummary -> tradeSummary.getCloseDate().toLocalDate().getDayOfMonth()));

        int x = 0;
        LocalDate start = LocalDate.of(date.getYear(), month, 1);
        while (start.getDayOfWeek() == SUNDAY || start.getDayOfWeek() == SATURDAY) start = start.plusDays(1);
        int y = start.getDayOfWeek().getValue();

        Node[][] grid = new Node[5][7];
        for (var node : dailyGridPane.getChildren()) {
            if (!(node instanceof VBox)) continue;
            clearData((VBox) node);
            int row = 0;
            int column = 0;
            if (node.hasProperties()) {
                row = (Integer) node.getProperties().getOrDefault("gridpane-row", 0);
                column = (Integer) node.getProperties().getOrDefault("gridpane-column", 0);
            }
            grid[row][column] = node;
        }

        int day = start.getDayOfMonth();
        while (day <= month.length(date.isLeapYear()) && x < 5) {
            setData((VBox) grid[x][y], day, map.getOrDefault(day, Collections.emptyList()));
            ++y;
            if (y % 7 == 0) {
                y = 0;
                ++x;
            }
            ++day;
        }
    }

    private void clearData(VBox vBox) {
        for (Node node : vBox.getChildren()) {
            ((Label) node).setText("");
        }
        vBox.getStyleClass().clear();
        vBox.getStyleClass().add("default");
        vBox.setOnMouseClicked(null);
    }

    private void setData(VBox vBox, int day, List<TradeSummary> summaries) {
        Label dayLabel = (Label) vBox.getChildren().get(0);
        Label amount = (Label) vBox.getChildren().get(1);
        Label trades = (Label) vBox.getChildren().get(2);

        dayLabel.setText(String.valueOf(day));
        if (!summaries.isEmpty()) {
            vBox.getStyleClass().add("hover");
            String title = summaries.get(0).getCloseDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL));
            vBox.setOnMouseClicked(event -> UIServiceProvider.getTradeDetailsDialogService().show(summaries.get(0), summaries, title));

            trades.setText(summaries.size() + " Trade" + (summaries.size() > 1 ? "s" : ""));
            double sum = summaries.stream().mapToDouble(TradeSummary::getProfit).sum();
            amount.setText("$" + prettify(sum));
            if (sum > 0) {
                vBox.getStyleClass().add("gain");
            }
            else {
                vBox.getStyleClass().add("loss");
            }
        }
    }

}
