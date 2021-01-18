package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.database.MapDatabase;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

public class MapDBStockDAO implements StockDAO {

    private final ConcurrentMap<String, String> stockNameMap;
    private final ConcurrentMap<String, Double> stockPriceMap;
    private final ConcurrentMap<String, String> stockSecurityMap;
    private final ConcurrentMap<String, Long> stockDateMap;

    MapDBStockDAO() {
        stockNameMap = MapDatabase.getStockNameMap();
        stockPriceMap = MapDatabase.getStockPriceMap();
        stockSecurityMap = MapDatabase.getStockSecurityMap();
        stockDateMap = MapDatabase.getStockDateMap();
    }

    @Override
    public Map<String, String> getStockNameMap() {
        return new HashMap<>(stockNameMap);
    }

    @Override
    public Map<String, Double> getStockPriceMap() {
        return new HashMap<>(stockPriceMap);
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

    @Override
    public Map<String, Long> getStockDateMap() {
        return stockDateMap;
    }

    @Override
    public void updateStockPrice(Map<String, Double> priceMap) {
        stockPriceMap.putAll(priceMap);
    }

}
