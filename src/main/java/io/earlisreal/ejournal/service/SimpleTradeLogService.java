package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dao.StrategyDAO;
import io.earlisreal.ejournal.dao.TradeLogDAO;
import io.earlisreal.ejournal.dto.Strategy;
import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.util.ParseUtil;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SimpleTradeLogService implements TradeLogService {

    private final TradeLogDAO tradeLogDAO;
    private final StrategyDAO strategyDAO;

    SimpleTradeLogService(TradeLogDAO tradeLogDAO, StrategyDAO strategyDAO) {
        this.tradeLogDAO = tradeLogDAO;
        this.strategyDAO = strategyDAO;
    }

    public void insertCsv(List<String> csv) {
        Map<String, Integer> strategies = strategyDAO.queryAll().stream().collect(Collectors.toMap(Strategy::getName, Strategy::getId));
        List<TradeLog> tradeLogs = ParseUtil.parseCsv(csv);
        for (TradeLog tradeLog : tradeLogs) {
            tradeLog.setStrategyId(strategies.getOrDefault(tradeLog.getStrategy(), null));
        }

        int inserted = tradeLogDAO.insertLog(tradeLogs);
        System.out.println(inserted + " Records Inserted");
    }

    public void insertLedger(List<String> lines) {

    }

}
