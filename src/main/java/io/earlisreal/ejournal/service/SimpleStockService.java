package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dao.StockDAO;
import io.earlisreal.ejournal.dto.Stock;
import io.earlisreal.ejournal.util.CommonUtil;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SimpleStockService implements StockService {

    private final StockDAO stockDAO;

    private Map<String, Stock> stockMap;
    private Map<String, String> stockCodeMap;

    SimpleStockService(StockDAO stockDAO) {
        this.stockDAO = stockDAO;
        stockMap = stockDAO.getStockMap();
        updateStockCodeMap();
    }

    @Override
    public String getName(String stock) {
        return stockMap.get(stock).getName();
    }

    @Override
    public String getCode(String stock) {
        return stockCodeMap.get(stock);
    }

    @Override
    public String getSecurityId(String stock) {
        return stockMap.get(stock).getSecurityId();
    }

    @Override
    public String getCompanyId(String stock) {
        return stockMap.get(stock).getCompanyId();
    }

    @Override
    public LocalDate getLastPriceDate(String stock) {
        LocalDate last = stockMap.get(stock).getLastDate();
        if (last == null) return LocalDate.of(2000, 1, 1);
        return last;
    }

    @Override
    public Double getPrice(String stock) {
        if (stockMap.containsKey(stock)) return stockMap.get(stock).getPrice();
        return null;
    }

    @Override
    public void updateLastDate(String stock, LocalDate localDate) {
        if (stockDAO.updateLastDate(stock, localDate)) {
            stockMap.get(stock).setLastDate(localDate);
        }
    }

    @Override
    public int getStockCount() {
        return stockMap.size();
    }

    @Override
    public void updateStocks(List<Stock> stocks) {
        stockDAO.updateStocks(stocks);
        stockMap = stockDAO.getStockMap();
        updateStockCodeMap();
    }

    @Override
    public void updateStockId(List<Stock> stocks) {
        if (stocks.isEmpty()) return;
        stockDAO.updateStockId(stocks);
    }

    @Override
    public List<Stock> getEmptyIds() {
        return stockMap.values().stream().filter(stock -> stock.getCompanyId() == null).collect(Collectors.toList());
    }

    @Override
    public Collection<String> getStockList() {
        return stockMap.keySet();
    }

    private void updateStockCodeMap() {
        stockCodeMap = new HashMap<>();
        for (var stock : stockMap.values()) {
            stockCodeMap.put(CommonUtil.trimStockName(stock.getName()), stock.getCode());
        }
    }

}
