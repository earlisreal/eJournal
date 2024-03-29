package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.model.TradeSummary;

import java.time.LocalDate;
import java.util.List;

public interface TradeLogService {

    void initialize();

    void applyFilter(LocalDate startDate, LocalDate endDate);

    List<TradeLog> insertCsv(List<String> csv);

    boolean insert(TradeLog tradeLog);

    List<TradeLog> insert(List<TradeLog> tradeLogs);

    List<TradeSummary> getTradeSummaries();

    List<TradeLog> getLogs();

    List<TradeSummary> getOpenPositions();

    void delete(int id);

}
