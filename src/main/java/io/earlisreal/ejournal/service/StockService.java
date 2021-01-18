package io.earlisreal.ejournal.service;

import java.time.LocalDate;
import java.util.Map;

public interface StockService {

    String getCode(String stock);

    String getSecurityId(String stock);

    String getCompanyId(String stock);

    LocalDate getLastPriceDate(String stock);

    double getPrice(String stock);

    void updateLastDate(String stock, LocalDate localDate);

    void updateStockNameMap(Map<String, String> stockMap);

    void updateStockSecurityMap(Map<String, String> stockSecurityMap);

    int getStockCount();

    void updateStockPrice(Map<String, Double> priceMap);

}
