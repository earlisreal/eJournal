package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dao.StrategyDAO;
import io.earlisreal.ejournal.dao.TradeLogDAO;
import io.earlisreal.ejournal.dto.Strategy;
import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.model.TradeSummary;
import io.earlisreal.ejournal.util.ParseUtil;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class SimpleTradeLogService implements TradeLogService {

    private final TradeLogDAO tradeLogDAO;
    private final StrategyDAO strategyDAO;
    private final List<TradeLog> logs;
    private final List<TradeSummary> summaries;
    private final List<TradeSummary> openPositions;

    SimpleTradeLogService(TradeLogDAO tradeLogDAO, StrategyDAO strategyDAO) {
        this.tradeLogDAO = tradeLogDAO;
        this.strategyDAO = strategyDAO;

        logs = new ArrayList<>();
        summaries = new ArrayList<>();
        openPositions = new ArrayList<>();
    }

    @Override
    public int insertCsv(List<String> csv) {
        Map<String, Integer> strategies = strategyDAO.queryAll().stream().collect(Collectors.toMap(Strategy::getName, Strategy::getId));
        List<TradeLog> tradeLogs = ParseUtil.parseCsv(csv);
        for (TradeLog tradeLog : tradeLogs) {
            tradeLog.setStrategyId(strategies.getOrDefault(tradeLog.getStrategy(), null));
        }

        return insert(tradeLogs);
    }

    @Override
    public int insert(List<TradeLog> tradeLogs) {
        if (tradeLogs.isEmpty()) {
            return 0;
        }

        int inserted = tradeLogDAO.insertLog(tradeLogs);
        System.out.println(inserted + " Records Inserted");

        if (inserted > 0) {
            logs.clear();
            logs.addAll(tradeLogDAO.queryAll());
            getSummaries(logs);
        }

        return inserted;
    }

    @Override
    public List<TradeLog> getLogs() {
        if (logs.isEmpty()) {
            logs.addAll(tradeLogDAO.queryAll());
            getSummaries(logs);
        }
        return logs;
    }

    @Override
    public List<TradeSummary> getOpenPositions() {
        return openPositions;
    }

    @Override
    public void applyFilter(LocalDate startDate, LocalDate endDate) {
        logs.clear();
        logs.addAll(tradeLogDAO.queryAll());
        logs.removeIf(tradeLog -> tradeLog.getDate().isAfter(endDate) || tradeLog.getDate().isBefore(startDate));

        getSummaries(logs);
    }

    @Override
    public List<TradeSummary> getTradeSummaries() {
        return summaries;
    }

    private void getSummaries(List<TradeLog> logs) {
        summaries.clear();
        logs.sort(Comparator.comparing(TradeLog::getDate).thenComparing(tradeLog -> !tradeLog.isBuy()));
        Map<String, TradeSummary> trades = new HashMap<>();
        for (TradeLog log : logs) {
            String stock = log.getStock();
            if (trades.containsKey(stock)) {
                var trade = trades.get(stock);
                if (log.isBuy()) {
                    trade.buy(log);
                }
                else {
                    trade.sell(log);
                    if (trade.isClosed()) {
                        trades.remove(stock);
                        trade.setCloseDate(log.getDate());
                        summaries.add(trade);
                    }
                }
            }
            else {
                var trade = new TradeSummary(log);
                trades.put(stock, trade);
            }
        }

        openPositions.addAll(trades.values());
    }

}
