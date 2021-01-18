package io.earlisreal.ejournal.dao;

import java.util.Map;

public interface StockDAO {

    Map<String, String> getStockNameMap();

    Map<String, Double> getStockPriceMap();

    Map<String, String> getStockSecurityMap();

    void updateStockMap(Map<String, String> stockMap);

    void updateStockSecurityMap(Map<String, String> stockSecurityMap);

    Map<String, Long> getStockDateMap();

    void updateStockPrice(Map<String, Double> priceMap);

}
