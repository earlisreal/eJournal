package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.model.TradeSummary;

import java.time.LocalDate;
import java.util.List;

public interface TradeLogService {

    void applyFilter(LocalDate startDate, LocalDate endDate);

    int insertCsv(List<String> csv);

    int insert(List<TradeLog> tradeLogs);

    List<TradeSummary> getAllTradeSummaries();

    List<TradeSummary> getTradeSummaries();

    List<TradeLog> getLogs();

    List<TradeLog> getAllLogs();

}
