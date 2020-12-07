package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dto.TradeLog;

import java.util.List;

public interface TradeLogService {

    void insertCsv(List<String> csv);

    void insert(List<TradeLog> tradeLogs);

}
