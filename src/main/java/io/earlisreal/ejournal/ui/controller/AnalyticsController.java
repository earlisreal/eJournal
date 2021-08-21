package io.earlisreal.ejournal.ui.controller;

import io.earlisreal.ejournal.model.TradeSummary;
import io.earlisreal.ejournal.service.AnalyticsService;
import io.earlisreal.ejournal.service.ServiceProvider;
import io.earlisreal.ejournal.ui.service.UIServiceProvider;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static io.earlisreal.ejournal.util.CommonUtil.prettify;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;

public class AnalyticsController {

    public LineChart<String, Double> equityChart;
    public BarChart<Double, String> topWinners;
    public BarChart<Double, String> topLosers;
    public VBox successProfit;
    public VBox failLoss;
    public VBox successPercent;
    public VBox failPercent;
    public BarChart<String, Double> monthlyBarChart;
    public GridPane dailyGridPane;

    private final AnalyticsService service;

    public AnalyticsController() {
        service = ServiceProvider.getAnalyticsService();
    }

    public void reload() {
        initializeEquityChart();
        initializeTopLoser();
        initializeTopWinner();
        initializeRemarkableTrades();
        initializeMonthlyChart();
        initializeDailyChart(LocalDate.now());
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

        vBox.setOnMouseClicked(unused -> UIServiceProvider.getTradeDetailsDialogService().show(summary, summaries));
    }

    private void initializeEquityChart() {
        XYChart.Series<String, Double> series = new XYChart.Series<>();
        series.setData(FXCollections.observableList(service.getEquityData()));
        equityChart.setData(FXCollections.observableList(List.of(series)));
    }

    private void initializeTopWinner() {
        XYChart.Series<Double, String> series = new XYChart.Series<>();
        series.setData(FXCollections.observableList(service.getTopWinners()));
        topWinners.setData(FXCollections.observableList(List.of(series)));
    }

    private void initializeTopLoser() {
        XYChart.Series<Double, String> series = new XYChart.Series<>();
        series.setData(FXCollections.observableList(service.getTopLosers()));
        topLosers.setData(FXCollections.observableList(List.of(series)));
    }

    private void initializeMonthlyChart() {
        XYChart.Series<String, Double> series = new XYChart.Series<>();
        series.setData(FXCollections.observableList(service.getMonthlyProfit()));
        monthlyBarChart.setData(FXCollections.observableList(List.of(series)));
    }

    private void initializeDailyChart(LocalDate date) {
        var children = dailyGridPane.getChildren();
        System.out.println("Size: " + children.size());
        Month month = date.getMonth();
        var map = service.getSummaries().stream()
                .filter(summary -> summary.getCloseDate() != null && summary.getCloseDate().getMonth() == month)
                .collect(Collectors.groupingBy(tradeSummary -> tradeSummary.getCloseDate().toLocalDate().getDayOfMonth()));

        int x = 0;
        LocalDate start = LocalDate.of(date.getYear(), month, 1);
        while (start.getDayOfWeek() == SUNDAY || start.getDayOfWeek() == SATURDAY) start = start.plusDays(1);
        int y = start.getDayOfWeek().getValue();

        Node[][] grid = new Node[5][7];
        for (var node : children) {
            if (!(node instanceof VBox)) continue;
            int row = 0;
            int column = 0;
            if (node.hasProperties()) {
                row = (Integer) node.getProperties().getOrDefault("gridpane-row", 0);
                column = (Integer) node.getProperties().getOrDefault("gridpane-column", 0);
            }
            grid[row][column] = node;
        }

        int day = start.getDayOfMonth();
        while (day <= month.length(date.isLeapYear())) {
            setData((VBox) grid[x][y], day, map.getOrDefault(day, Collections.emptyList()));
            ++y;
            if (y % 7 == 0) {
                y = 0;
                ++x;
            }
            ++day;
        }
    }

    private void setData(VBox vBox, int day, List<TradeSummary> summaries) {
        Label dayLabel = (Label) vBox.getChildren().get(0);
        Label amount = (Label) vBox.getChildren().get(1);
        Label trades = (Label) vBox.getChildren().get(2);

        dayLabel.setText(String.valueOf(day));
        if (!summaries.isEmpty()) {
            trades.setText(summaries.size() + " Trade" + (summaries.size() > 1 ? "s" : ""));
            double sum = summaries.stream().mapToDouble(TradeSummary::getProfit).sum();
            amount.setText("$" + prettify(sum));
            if (sum > 0) {
                vBox.setStyle("-fx-background-color: #90EE90;");
            }
            else {
                vBox.setStyle("-fx-background-color: #ffcccb;");
            }
        }
    }

}
