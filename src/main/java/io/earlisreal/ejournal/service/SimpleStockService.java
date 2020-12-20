package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dao.StockDAO;

import java.util.Map;

public class SimpleStockService implements StockService {

    private final StockDAO stockDAO;

    private Map<String, String> stockMap;

    SimpleStockService(StockDAO stockDAO) {
        this.stockDAO = stockDAO;
        stockMap = stockDAO.getStockMap();
    }

    @Override
    public String getCode(String stock) {
        return stockMap.get(stock);
    }

    @Override
    public void updateStockMap(Map<String, String> stockMap) {
        stockDAO.updateStockMap(stockMap);
        this.stockMap = stockMap;
    }

}
