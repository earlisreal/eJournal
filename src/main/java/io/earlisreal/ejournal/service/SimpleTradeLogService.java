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

    private LocalDate startDate;
    private LocalDate endDate;

    SimpleTradeLogService(TradeLogDAO tradeLogDAO, StrategyDAO strategyDAO) {
        this.tradeLogDAO = tradeLogDAO;
        this.strategyDAO = strategyDAO;

        logs = new ArrayList<>();
        summaries = new ArrayList<>();
        openPositions = new ArrayList<>();

        endDate = LocalDate.now();
        startDate = LocalDate.ofEpochDay(0);
    }

    @Override
    public int insertCsv(List<String> csv) {
        Map<String, Integer> strategies = strategyDAO.queryAll().stream().collect(Collectors.toMap(Strategy::getName, Strategy::getId));
        List<TradeLog> tradeLogs = ParseUtil.parseTradeLogs(csv);
        for (TradeLog tradeLog : tradeLogs) {
            tradeLog.setStrategyId(strategies.getOrDefault(tradeLog.getStrategy(), null));
        }

        return insert(tradeLogs);
    }

    @Override
    public boolean insert(TradeLog tradeLog) {
        boolean inserted = tradeLogDAO.insertLog(tradeLog);
        System.out.println("Trade Log Inserted");
        return inserted;
    }

    @Override
    public int insert(List<TradeLog> tradeLogs) {
        if (tradeLogs.isEmpty()) {
            return 0;
        }

        int inserted = tradeLogDAO.insertLog(tradeLogs);
        System.out.println(inserted + " Records Inserted");
        return inserted;
    }

    @Override
    public int upsert(List<TradeLog> tradeLogs) {
        if (tradeLogs.isEmpty()) {
            return 0;
        }

        int upserted = tradeLogDAO.upsert(tradeLogs);
        System.out.println(upserted + " Records Upserted");
        return upserted;
    }

    @Override
    public List<TradeLog> getLogs() {
        return logs;
    }

    @Override
    public List<TradeSummary> getOpenPositions() {
        return openPositions;
    }

    @Override
    public void delete(int id) {
        tradeLogDAO.delete(id);
    }

    @Override
    public void initialize() {
        logs.clear();
        logs.addAll(tradeLogDAO.queryAll());
        logs.removeIf(tradeLog -> tradeLog.getDate().isAfter(endDate));
        logs.sort(Comparator.comparing(TradeLog::getDate).reversed());

        calculateSummaries(logs);
    }

    @Override
    public void applyFilter(LocalDate startDate, LocalDate endDate) {
        logs.clear();
        logs.addAll(tradeLogDAO.queryAll());

        this.startDate = Objects.requireNonNullElseGet(startDate, () -> LocalDate.ofEpochDay(0));
        this.endDate = Objects.requireNonNullElseGet(endDate, LocalDate::now);
    }

    @Override
    public List<TradeSummary> getTradeSummaries() {
        return summaries;
    }

    private void calculateSummaries(List<TradeLog> logs) {
        summaries.clear();
        openPositions.clear();

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

        summaries.removeIf(summary -> summary.getCloseDate().isAfter(endDate) || summary.getCloseDate().isBefore(startDate));
        summaries.sort(Comparator.comparing(TradeSummary::getCloseDate).reversed());
        logs.sort(Comparator.comparing(TradeLog::getDate).reversed());

        openPositions.addAll(trades.values());
    }

}
