package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dto.BankTransaction;
import io.earlisreal.ejournal.model.TradeSummary;
import javafx.scene.chart.XYChart;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static io.earlisreal.ejournal.util.CommonUtil.round;

public class SimpleAnalyticsService implements AnalyticsService {

    private final TradeLogService tradeLogService;
    private final BankTransactionService bankTransactionService;
    private final Predicate<TradeSummary> win;
    private final Predicate<TradeSummary> loss;

    SimpleAnalyticsService(TradeLogService tradeLogService, BankTransactionService bankTransactionService) {
        this.tradeLogService = tradeLogService;
        this.bankTransactionService = bankTransactionService;
        win = t -> t.getProfit() >= 0;
        loss = t -> t.getProfit() < 0;
    }

    @Override
    public double getEdgeRatio() {
        return getAverageProfit() / getAverageLoss() * -1;
    }

    @Override
    public double getAverageProfit() {
        return tradeLogService.getTradeSummaries().stream()
                .filter(t -> t.getProfit() >= 0).collect(Collectors.averagingDouble(TradeSummary::getProfit));
    }

    @Override
    public double getAverageLoss() {
        return tradeLogService.getTradeSummaries().stream()
                .filter(t -> t.getProfit() < 0).collect(Collectors.averagingDouble(TradeSummary::getProfit));
    }

    @Override
    public double getAverageProfitPercentage() {
        return round(tradeLogService.getTradeSummaries().stream().filter(t -> t.getProfitPercentage() >= 0)
                .collect(Collectors.averagingDouble(TradeSummary::getProfitPercentage)));
    }

    @Override
    public double getAverageLossPercentage() {
        return round(tradeLogService.getTradeSummaries().stream().filter(t -> t.getProfitPercentage() < 0)
                .collect(Collectors.averagingDouble(TradeSummary::getProfitPercentage)));
    }

    @Override
    public double getAccuracy() {
        var summaries = tradeLogService.getTradeSummaries();
        return round((double) summaries.stream().filter(win).count() / summaries.size() * 100);
    }

    @Override
    public double getProfitFactor() {
        var summaries = tradeLogService.getTradeSummaries();
        double profits = summaries.stream().filter(win).mapToDouble(TradeSummary::getProfit).sum();
        double losses = summaries.stream().filter(loss).mapToDouble(TradeSummary::getProfit).sum();
        return round(profits / losses * -1);
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

}
