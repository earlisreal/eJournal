package io.earlisreal.ejournal.ui.controller;

import io.earlisreal.ejournal.model.TradeSummary;
import io.earlisreal.ejournal.service.AnalyticsService;
import io.earlisreal.ejournal.service.ServiceProvider;
import io.earlisreal.ejournal.service.StockService;
import io.earlisreal.ejournal.service.TradeLogService;
import io.earlisreal.ejournal.ui.service.UIServiceProvider;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.Initializable;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.paint.Paint;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Comparator;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import static io.earlisreal.ejournal.util.CommonUtil.prettify;

public class DayTradeDashboardController implements Initializable {

    public TableView<TradeSummary> tradesTable;
    public TableColumn<TradeSummary, String> openColumn;
    public TableColumn<TradeSummary, String> stockColumn;
    public TableColumn<TradeSummary, String> typeColumn;
    public TableColumn<TradeSummary, String> averageColumn;
    public TableColumn<TradeSummary, String> sharesColumn;
    public TableColumn<TradeSummary, String> totalCostColumn;
    public TableColumn<TradeSummary, String> profitColumn;
    public TableColumn<TradeSummary, String> durationColumn;

    public PieChart accuracyPie;
    public Label accuracyLabel;

    public Label dateLabel;
    public Label timeLabel;
    public Label gainLossLabel;
    public Label profitLabel;
    public Label lossLabel;

    private AnalyticsService analyticsService;
    private TradeLogService tradeLogService;
    private StockService stockService;

    private List<TradeSummary> latestSummaries;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        tradeLogService = ServiceProvider.getTradeLogService();
        stockService = ServiceProvider.getStockService();
        analyticsService = ServiceProvider.getAnalyticsService();
    }

    public void reload() {
        var summaries = tradeLogService.getTradeSummaries();
        if (summaries.isEmpty()) {
            dateLabel.setText("N/A");
            timeLabel.setText("");
            profitLabel.setText("");
            lossLabel.setText("");
            return;
        }

        latestSummaries = summaries.stream()
                .filter(tradeSummary -> tradeSummary.getOpenDate().toLocalDate().equals(summaries.get(0).getOpenDate().toLocalDate()))
                .sorted(Comparator.comparing(TradeSummary::getOpenDate))
                .collect(Collectors.toList());

        initializeStatistics();
        initializeChart();
        initializeTrades();
    }

    private void initializeStatistics() {
        TradeSummary lastSummary = latestSummaries.get(latestSummaries.size() - 1);

        dateLabel.setText(lastSummary.getOpenDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)));
        var shortFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT);
        String startTime = latestSummaries.get(0).getOpenDate().format(shortFormatter);
        String endTime = lastSummary.getCloseDate().format(shortFormatter);
        timeLabel.setText(startTime + " - " + endTime);

        var partition = latestSummaries.stream()
                .collect(Collectors.partitioningBy(tradeSummary -> tradeSummary.getProfit() >= 0, Collectors.summingDouble(TradeSummary::getProfit)));
        profitLabel.setText("$" + prettify(partition.get(true)) + " profit");
        lossLabel.setText("$" + prettify(partition.get(false)) + " loss");

        double total = partition.get(true) + partition.get(false);
        gainLossLabel.setText("$" + prettify(total));
        if (total > 0) gainLossLabel.setTextFill(Paint.valueOf("GREEN"));
        else gainLossLabel.setTextFill(Paint.valueOf("RED"));
    }

    private void initializeChart() {

    }

    private void initializeTrades() {
        tradesTable.setItems(FXCollections.observableList(latestSummaries));
        String title = latestSummaries.get(0).getCloseDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL));
        tradesTable.setRowFactory(param ->
                UIServiceProvider.getTradeDetailsDialogService().getTableRow(latestSummaries, title));

        openColumn.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getOpenDate().toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))));
        stockColumn.setCellValueFactory(new PropertyValueFactory<>("stock"));
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("tradeType"));
        averageColumn.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getAverageBuy())));
        sharesColumn.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getShares())));
        totalCostColumn.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getPosition())));
        profitColumn.setCellValueFactory(p -> new SimpleStringProperty(prettify(p.getValue().getProfit())));
        durationColumn.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getHoldingPeriod()));
    }

}
