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

    private List<TradeLog> allLogs;

    SimpleTradeLogService(TradeLogDAO tradeLogDAO, StrategyDAO strategyDAO) {
        this.tradeLogDAO = tradeLogDAO;
        this.strategyDAO = strategyDAO;

        logs = new ArrayList<>();
    }

    @Override
    public int insertCsv(List<String> csv) {
        Map<String, Integer> strategies = strategyDAO.queryAll().stream().collect(Collectors.toMap(Strategy::getName, Strategy::getId));
        List<TradeLog> tradeLogs = ParseUtil.parseCsv(csv);
        for (TradeLog tradeLog : tradeLogs) {
            tradeLog.setStrategyId(strategies.getOrDefault(tradeLog.getStrategy(), null));
        }

        int res = insert(tradeLogs);
        if (res > 0) {
            logs.addAll(tradeLogs);
        }
        return res;
    }

    @Override
    public int insert(List<TradeLog> tradeLogs) {
        int inserted = 0;
        if (!tradeLogs.isEmpty()) {
            inserted = tradeLogDAO.insertLog(tradeLogs);
        }
        System.out.println(inserted + " Records Inserted");

        logs.clear();
        logs.addAll(tradeLogDAO.queryAll());

        return inserted;
    }

    @Override
    public List<TradeSummary> getAllTradeSummaries() {
        return getSummaries(getAllLogs());
    }

    @Override
    public List<TradeSummary> getTradeSummaries() {
        return getSummaries(getLogs());
    }

    private List<TradeSummary> getSummaries(List<TradeLog> logs) {
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

    @Override
    public List<TradeLog> getLogs() {
        if (allLogs == null) {
            synchronized (this) {
                if (allLogs == null) {
                    allLogs = tradeLogDAO.queryAll();
                    logs.addAll(allLogs);
                }
            }
        }
        return logs;
    }

    @Override
    public List<TradeLog> getAllLogs() {
        return allLogs;
    }

    @Override
    public void applyFilter(LocalDate startDate, LocalDate endDate) {
        logs.clear();
        logs.addAll(allLogs);
        logs.removeIf(tradeLog -> tradeLog.getDate().isAfter(endDate) || tradeLog.getDate().isBefore(startDate));
    }

}
