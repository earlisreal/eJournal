package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.model.TradeSummary;

import java.util.List;

public interface TradeLogService {

    List<TradeLog> getAll();

    void insertCsv(List<String> csv);

    void insert(List<TradeLog> tradeLogs);

    List<TradeSummary> getTradeSummaries();

}
