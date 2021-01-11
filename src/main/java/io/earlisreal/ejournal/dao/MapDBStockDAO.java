package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.database.MapDatabase;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

public class MapDBStockDAO implements StockDAO {

    private final ConcurrentMap<String, String> stockNameMap;
    private final ConcurrentMap<String, String> stockSecurityMap;

    MapDBStockDAO() {
        stockNameMap = MapDatabase.getStockNameMap();
        stockSecurityMap = MapDatabase.getStockSecurityMap();
    }

    @Override
    public Map<String, String> getStockNameMap() {
        return new HashMap<>(stockNameMap);
    }

    @Override
    public Map<String, String> getStockSecurityMap() {
        return new HashMap<>(stockSecurityMap);
    }

    @Override
    public void updateStockMap(Map<String, String> stockMap) {
        this.stockNameMap.putAll(stockMap);
    }

    @Override
    public void updateStockSecurityMap(Map<String, String> stockSecurityMap) {
        this.stockSecurityMap.putAll(stockSecurityMap);
    }

}
