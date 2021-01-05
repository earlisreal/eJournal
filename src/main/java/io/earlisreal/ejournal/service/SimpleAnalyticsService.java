package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dto.BankTransaction;
import io.earlisreal.ejournal.model.TradeSummary;
import javafx.scene.chart.XYChart;

import java.time.LocalDate;
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

    List<TradeSummary> summaries;
    List<BankTransaction> transactions;

    SimpleAnalyticsService(TradeLogService tradeLogService, BankTransactionService bankTransactionService) {
        this.tradeLogService = tradeLogService;
        this.bankTransactionService = bankTransactionService;
        win = t -> t.getProfit() >= 0;
        loss = t -> t.getProfit() < 0;
    }

    @Override
    public void initialize(LocalDate startDate, LocalDate endDate) {
        initialize();
        summaries.removeIf(tradeSummary -> tradeSummary.getOpenDate().isBefore(startDate)
                || tradeSummary.getCloseDate().isAfter(endDate));
        transactions.removeIf(bankTransaction -> bankTransaction.getDate().isBefore(startDate)
                || bankTransaction.getDate().isAfter(endDate));
    }

    @Override
    public void initialize() {
        summaries = tradeLogService.getTradeSummaries();
        transactions = bankTransactionService.getAll();
    }

    @Override
    public double getEdgeRatio() {
        return getAverageProfit() / getAverageLoss() * -1;
    }

    @Override
    public double getAverageProfit() {
        return summaries.stream()
                .filter(t -> t.getProfit() >= 0).collect(Collectors.averagingDouble(TradeSummary::getProfit));
    }

    @Override
    public double getAverageLoss() {
        return summaries.stream()
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
        return round((double) summaries.stream().filter(win).count() / summaries.size() * 100);
    }

    @Override
    public double getProfitFactor() {
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
        var dateMap = summaries.stream()
                .collect(Collectors.toMap(TradeSummary::getCloseDate, TradeSummary::getProfit, Double::sum, TreeMap::new));
        for (BankTransaction transaction : transactions) {
            dateMap.merge(transaction.getDate(), transaction.getAmount(), Double::sum);
        }

        List<XYChart.Data<String, Double>> data = new ArrayList<>();
        for (var entry : dateMap.entrySet()) {
            data.add(new XYChart.Data<>(entry.getKey().toString(), entry.getValue()));
        }
        return data;
    }

}
