package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dto.BankTransaction;
import io.earlisreal.ejournal.model.TradeSummary;
import javafx.scene.chart.XYChart;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static io.earlisreal.ejournal.util.CommonUtil.round;

public class SimpleAnalyticsService implements AnalyticsService {

    private final TradeLogService tradeLogService;
    private final BankTransactionService bankTransactionService;

    private List<TradeSummary> allWins;
    private List<TradeSummary> allLosses;
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
        List<TradeSummary> allSummaries = tradeLogService.getAllTradeSummaries();
        allWins = allSummaries.stream().filter(t -> t.getProfit() >= 0).collect(Collectors.toList());
        allLosses = allSummaries.stream().filter(t -> t.getProfit() < 0).collect(Collectors.toList());

        summaries = tradeLogService.getTradeSummaries();
        wins = tradeLogService.getTradeSummaries().stream().filter(t -> t.getProfit() >= 0).collect(Collectors.toList());
        losses = tradeLogService.getTradeSummaries().stream().filter(t -> t.getProfit() < 0).collect(Collectors.toList());

        var transactions = bankTransactionService.getAll();
        dateMap = summaries.stream()
                .collect(Collectors.toMap(TradeSummary::getCloseDate, TradeSummary::getProfit, Double::sum, TreeMap::new));
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
    public double getAccuracy() {
        return round((double) wins.size() / summaries.size() * 100);
    }

    @Override
    public double getProfitFactor() {
        double profits = wins.stream().mapToDouble(TradeSummary::getProfit).sum();
        double mistakes = losses.stream().mapToDouble(TradeSummary::getProfit).sum();
        return round(profits / mistakes * -1);
    }

    @Override
    public double getAverageHoldingDays() {
        return tradeLogService.getTradeSummaries().stream().collect(Collectors.averagingDouble(TradeSummary::getTradeLength));
    }

    @Override
    public List<XYChart.Data<String, Double>> getEquityData() {
        List<XYChart.Data<String, Double>> data = new ArrayList<>();
        double runningSum = 0;
        for (var entry : dateMap.entrySet()) {
            runningSum += entry.getValue();
            data.add(new XYChart.Data<>(entry.getKey().toString(), runningSum));
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
    public List<TradeSummary> getAllWins() {
        return allWins;
    }

    @Override
    public List<TradeSummary> getAllLosses() {
        return allLosses;
    }

}
