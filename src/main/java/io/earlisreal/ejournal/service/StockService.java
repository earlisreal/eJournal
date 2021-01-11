package io.earlisreal.ejournal.service;

import java.util.Map;

public interface StockService {

    String getCode(String stock);

    String getSecurityId(String stock);

    String getCompanyId(String stock);

    void updateStockNameMap(Map<String, String> stockMap);

    void updateStockSecurityMap(Map<String, String> stockSecurityMap);

}
