package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.model.TradeSummary;

import java.util.function.Predicate;
import java.util.stream.Collectors;

import static io.earlisreal.ejournal.util.CommonUtil.round;

public class SimpleAnalyticsService implements AnalyticsService {

    private final TradeLogService tradeLogService;
    private final Predicate<TradeSummary> win;
    private final Predicate<TradeSummary> loss;

    SimpleAnalyticsService(TradeLogService tradeLogService) {
        this.tradeLogService = tradeLogService;
        win = t -> t.getProfit() >= 0;
        loss = t -> t.getProfit() < 0;
    }

    @Override
    public double getEdgeRatio() {
        return getAverageProfit() / getAverageLoss() * -1;
    }

    @Override
    public double getAverageProfit() {
        var summaries = tradeLogService.getTradeSummaries();
        return summaries.stream()
                .filter(t -> t.getProfit() >= 0).collect(Collectors.averagingDouble(TradeSummary::getProfit));
    }

    @Override
    public double getAverageLoss() {
        var summaries = tradeLogService.getTradeSummaries();
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
        var summaries = tradeLogService.getTradeSummaries();
        return round((double) summaries.stream().filter(win).count() / summaries.size() * 100);
    }

}
