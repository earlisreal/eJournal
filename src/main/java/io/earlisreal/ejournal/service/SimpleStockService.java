package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dao.StockDAO;

import java.time.LocalDate;
import java.util.Map;

public class SimpleStockService implements StockService {

    private final StockDAO stockDAO;

    private Map<String, String> stockNameMap;
    private Map<String, String> stockSecurityMap;
    private final Map<String, Long> stockDateMap;

    SimpleStockService(StockDAO stockDAO) {
        this.stockDAO = stockDAO;
        stockNameMap = stockDAO.getStockNameMap();
        stockSecurityMap = stockDAO.getStockSecurityMap();
        stockDateMap = stockDAO.getStockDateMap();
    }

    @Override
    public String getCode(String stock) {
        return stockNameMap.get(stock);
    }

    @Override
    public String getSecurityId(String stock) {
        String security = stockSecurityMap.get(stock);
        return security.split(",")[1];
    }

    @Override
    public String getCompanyId(String stock) {
        String security = stockSecurityMap.get(stock);
        return security.split(",")[0];
    }

    @Override
    public LocalDate getLastPriceDate(String stock) {
        Long last = stockDateMap.get(stock);
        if (last == null) return LocalDate.of(2000, 1, 1);
        return LocalDate.ofEpochDay(last);
    }

    @Override
    public void updateLastDate(String stock, LocalDate localDate) {
        stockDateMap.put(stock, localDate.toEpochDay());
    }

    @Override
    public void updateStockNameMap(Map<String, String> stockMap) {
        stockDAO.updateStockMap(stockMap);
        this.stockNameMap = stockMap;
    }

    @Override
    public void updateStockSecurityMap(Map<String, String> stockSecurityMap) {
        stockDAO.updateStockSecurityMap(stockSecurityMap);
        this.stockSecurityMap = stockSecurityMap;
    }

}
