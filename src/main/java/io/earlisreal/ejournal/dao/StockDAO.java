package io.earlisreal.ejournal.dao;

import java.util.Map;

public interface StockDAO {

    Map<String, String> getStockMap();

    void updateStockMap(Map<String, String> stockMap);

}
