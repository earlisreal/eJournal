package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.database.MapDatabase;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

public class MapDBStockDAO implements StockDAO {

    private final ConcurrentMap<String, String> stockMap;

    MapDBStockDAO() {
        stockMap = MapDatabase.getStockMap();
    }

    @Override
    public Map<String, String> getStockMap() {
        return new HashMap<>(stockMap);
    }

    @Override
    public void updateStockMap(Map<String, String> stockMap) {
        this.stockMap.putAll(stockMap);
    }

}
