package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dto.BankTransaction;
import io.earlisreal.ejournal.model.LineData;
import io.earlisreal.ejournal.model.TradeSummary;
import io.earlisreal.ejournal.model.HistogramData;
import javafx.scene.chart.XYChart;

import java.time.LocalDate;
import java.time.Month;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

import static io.earlisreal.ejournal.util.CommonUtil.normalize;
import static io.earlisreal.ejournal.util.CommonUtil.round;
import static java.time.LocalDate.now;

public class SimpleAnalyticsService implements AnalyticsService {

    private final TradeLogService tradeLogService;
    private final BankTransactionService bankTransactionService;

    private List<TradeSummary> summaries;
    private List<TradeSummary> wins;
    private List<TradeSummary> losses;
    private TreeMap<LocalDate, Double> dateMap;
    private double totalEquity;

    SimpleAnalyticsService(TradeLogService tradeLogService, BankTransactionService bankTransactionService) {
        this.tradeLogService = tradeLogService;
        this.bankTransactionService = bankTransactionService;
    }

    @Override
    public void initialize() {
        summaries = tradeLogService.getTradeSummaries();
        wins = tradeLogService.getTradeSummaries().stream().filter(t -> t.getProfit() >= 0).collect(Collectors.toList());
        losses = tradeLogService.getTradeSummaries().stream().filter(t -> t.getProfit() < 0).collect(Collectors.toList());

        var transactions = bankTransactionService.getAll();
        dateMap = summaries.stream()
                .collect(Collectors.toMap(tradeSummary -> tradeSummary.getCloseDate().toLocalDate(), TradeSummary::getProfit, Double::sum, TreeMap::new));
        for (BankTransaction transaction : transactions) {
            dateMap.merge(transaction.getDate(), transaction.getAmount(), Double::sum);
        }

        totalEquity = 0;
        for (double value : dateMap.values()) {
            totalEquity += value;
        }
    }

    @Override
    public double getTotalEquity() {
        return totalEquity;
    }

    @Override
    public double getTotalProfit() {
        double total = 0;
        for (TradeSummary tradeSummary : tradeLogService.getTradeSummaries()) {
            total += tradeSummary.getProfit();
        }
        return total;
    }

    @Override
    public double getTotalProfitPercentage() {
        return round(getTotalProfit() / getTotalEquity() * 100);
    }

    @Override
    public double getEdgeRatio() {
        return getAverageProfit() / getAverageLoss() * -1;
    }

    @Override
    public double getAverageProfit() {
        return summaries.stream().filter(t -> t.getProfit() >= 0).collect(Collectors.averagingDouble(TradeSummary::getProfit));
    }

    @Override
    public double getAverageLoss() {
        return losses.stream().collect(Collectors.averagingDouble(TradeSummary::getProfit));
    }

    @Override
    public double getAverageProfitPercentage() {
        return round(wins.stream().collect(Collectors.averagingDouble(TradeSummary::getProfitPercentage)));
    }

    @Override
    public double getAverageLossPercentage() {
        return round(losses.stream().collect(Collectors.averagingDouble(TradeSummary::getProfitPercentage)));
    }

    @Override
    public double getWinAccuracy() {
        return round((double) wins.size() / summaries.size() * 100);
    }

    @Override
    public double getLossAccuracy() {
        return round((double) losses.size() / summaries.size() * 100);
    }

    @Override
    public double getProfitFactor() {
        double averageLoss = getAverageLoss();
        double averageWin = getAverageProfit();
        return round((getWinAccuracy() * averageWin) / (getLossAccuracy() * averageLoss * -1));
    }

    @Override
    public String getAverageHoldingDays() {
        long seconds = tradeLogService.getTradeSummaries().stream().collect(Collectors.averagingLong(TradeSummary::getTradeLength)).longValue();
        return normalize(seconds);
    }

    @Override
    public List<LineData> getEquityData() {
        List<LineData> data = new ArrayList<>();
        double runningSum = 0;
        for (var entry : dateMap.entrySet()) {
            runningSum += entry.getValue();
            long epochSecond = entry.getKey().atStartOfDay().toEpochSecond(ZoneOffset.UTC);
            data.add(new LineData(epochSecond, runningSum));
        }
        return data;
    }

    @Override
    public List<XYChart.Data<Double, String>> getTopWinners() {
        var map = tradeLogService.getTradeSummaries().stream()
                .filter(summary -> summary.getProfit() > 0)
                .sorted(Comparator.comparing(TradeSummary::getProfit).reversed())
                .collect(Collectors.toMap(TradeSummary::getStock, TradeSummary::getProfit, Double::sum));
        var list = mapToList(map);
        list.sort(Comparator.comparing(XYChart.Data::getXValue));
        return list;
    }

    @Override
    public List<XYChart.Data<Double, String>> getTopLosers() {
        var map = tradeLogService.getTradeSummaries().stream()
                .filter(summary -> summary.getProfit() < 0)
                .sorted(Comparator.comparing(TradeSummary::getProfit))
                .collect(Collectors.toMap(TradeSummary::getStock, TradeSummary::getProfit, Double::sum));
        var list = mapToList(map);
        list.sort((o1, o2) -> o2.getXValue().compareTo(o1.getXValue()));
        return list;
    }

    private List<XYChart.Data<Double, String>> mapToList(Map<String, Double> map) {
        List<XYChart.Data<Double, String>> data = new ArrayList<>();
        for (var entry : map.entrySet()) {
            if (data.size() == 5) break;
            data.add(new XYChart.Data<>(entry.getValue(), entry.getKey()));
        }

        return data;
    }

    @Override
    public List<TradeSummary> getSummaries() {
        return summaries;
    }

    @Override
    public List<TradeSummary> getLosses() {
        return losses;
    }

    @Override
    public List<TradeSummary> getWins() {
        return wins;
    }

    @Override
    public String getTradingAge() {
        if (tradeLogService.getLogs().isEmpty()) {
            return "N/A";
        }
        var logs = tradeLogService.getLogs();
        Period period = logs.get(logs.size() - 1).getDate().toLocalDate().until(now()).normalized();
        String age = "";
        if (period.getYears() > 0) age += period.getYears() + "yr ";
        if (period.getMonths() > 0) age += period.getMonths() + "m ";
        if (period.getDays() > 0) age += period.getDays() + "day" + (period.getDays() > 1 ? "s" : "");
        return age;
    }

    @Override
    public double getAveragePosition() {
        return tradeLogService.getTradeSummaries()
                .stream()
                .collect(Collectors.averagingDouble(TradeSummary::getPosition));
    }

    @Override
    public List<XYChart.Data<String, Double>> getMonthlyProfit(int year) {
        List<XYChart.Data<String, Double>> chartData = new ArrayList<>();
        var map = tradeLogService.getTradeSummaries().stream()
                .filter(summary -> summary.getCloseDate().getYear() == year)
                .collect(Collectors.toMap(summary -> summary.getCloseDate().getMonth(), TradeSummary::getProfit, Double::sum));
        for (Month month : Month.values()) {
            String monthStr = month.getDisplayName(TextStyle.SHORT, Locale.getDefault());
            double value = round(map.getOrDefault(month, 0.0));
            var data = new XYChart.Data<>(monthStr, value);
            if (value > 0) {
                data.nodeProperty().addListener((observable, oldValue, newValue) -> newValue.setStyle("-fx-bar-fill: green"));
            }
            else {
                data.nodeProperty().addListener((observable, oldValue, newValue) -> newValue.setStyle("-fx-bar-fill: red"));
            }
            chartData.add(data);
        }

        return chartData;
    }

}
