package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dao.StrategyDAO;
import io.earlisreal.ejournal.dao.TradeLogDAO;
import io.earlisreal.ejournal.dto.Strategy;
import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.model.TradeSummary;
import io.earlisreal.ejournal.util.ParseUtil;

import java.util.*;
import java.util.stream.Collectors;

public class SimpleTradeLogService implements TradeLogService {

    private final TradeLogDAO tradeLogDAO;
    private final StrategyDAO strategyDAO;

    SimpleTradeLogService(TradeLogDAO tradeLogDAO, StrategyDAO strategyDAO) {
        this.tradeLogDAO = tradeLogDAO;
        this.strategyDAO = strategyDAO;
    }

    public List<TradeLog> getAll() {
        // TODO : Create cache and retrieve from dao only when there is newly inserted record
        return tradeLogDAO.queryAll();
    }

    public void insertCsv(List<String> csv) {
        Map<String, Integer> strategies = strategyDAO.queryAll().stream().collect(Collectors.toMap(Strategy::getName, Strategy::getId));
        List<TradeLog> tradeLogs = ParseUtil.parseCsv(csv);
        for (TradeLog tradeLog : tradeLogs) {
            tradeLog.setStrategyId(strategies.getOrDefault(tradeLog.getStrategy(), null));
        }

        insert(tradeLogs);
    }

    public void insert(List<TradeLog> tradeLogs) {
        int inserted = 0;
        if (!tradeLogs.isEmpty()) {
            inserted = tradeLogDAO.insertLog(tradeLogs);
        }
        // TODO: Track a flag if logs is updated, then use it to recompute the summaries
        System.out.println(inserted + " Records Inserted");
    }

    @Override
    public List<TradeSummary> getTradeSummaries() {
        var logs = getAll();
        logs.sort(Comparator.comparing(TradeLog::getDate).thenComparing(tradeLog -> !tradeLog.isBuy()));
        List<TradeSummary> summaries = new ArrayList<>();
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
                    if (trade.getShares() == 0) {
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
        return summaries;
    }

}
