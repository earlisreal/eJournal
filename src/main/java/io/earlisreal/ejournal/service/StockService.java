package io.earlisreal.ejournal.service;

import java.util.Map;

public interface StockService {

    String getCode(String stock);

    void updateStockMap(Map<String, String> stockMap);

}
