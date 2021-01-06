package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dto.BankTransaction;
import io.earlisreal.ejournal.model.TradeSummary;
import javafx.scene.chart.XYChart;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static io.earlisreal.ejournal.util.CommonUtil.round;

public class SimpleAnalyticsService implements AnalyticsService {

    private final TradeLogService tradeLogService;
    private final BankTransactionService bankTransactionService;

    private List<TradeSummary> summaries;
    private List<TradeSummary> wins;
    private List<TradeSummary> losses;

    SimpleAnalyticsService(TradeLogService tradeLogService, BankTransactionService bankTransactionService) {
        this.tradeLogService = tradeLogService;
        this.bankTransactionService = bankTransactionService;
    }

    @Override
    public void initialize() {
        summaries = tradeLogService.getTradeSummaries();
        wins = tradeLogService.getTradeSummaries().stream().filter(t -> t.getProfit() >= 0).collect(Collectors.toList());
        losses = tradeLogService.getTradeSummaries().stream().filter(t -> t.getProfit() < 0).collect(Collectors.toList());
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
        return round((double) wins.size() / tradeLogService.getTradeSummaries().size() * 100);
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
        var summaries = tradeLogService.getTradeSummaries();
        var transactions = bankTransactionService.getAll();
        var dateMap = summaries.stream()
                .collect(Collectors.toMap(TradeSummary::getCloseDate, TradeSummary::getProfit, Double::sum, TreeMap::new));
        for (BankTransaction transaction : transactions) {
            dateMap.merge(transaction.getDate(), transaction.getAmount(), Double::sum);
        }

        List<XYChart.Data<String, Double>> data = new ArrayList<>();
        double runningSum = 0;
        for (var entry : dateMap.entrySet()) {
            runningSum += entry.getValue();
            data.add(new XYChart.Data<>(entry.getKey().toString(), runningSum));
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

}
