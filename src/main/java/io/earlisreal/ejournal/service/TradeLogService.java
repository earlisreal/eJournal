package io.earlisreal.ejournal.service;

import java.util.List;

public interface TradeLogService {

    void insertCsv(List<String> csv);

    void insertLedger(List<String> lines);

}
