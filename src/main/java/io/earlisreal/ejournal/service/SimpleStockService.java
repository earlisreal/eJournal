package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dao.StockDAO;
import io.earlisreal.ejournal.dto.Stock;
import io.earlisreal.ejournal.util.CommonUtil;
import io.earlisreal.ejournal.util.Country;

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
    public Stock getStock(String code) {
        return stockMap.get(code);
    }

    @Override
    public String getName(String stock) {
        if (stockMap.containsKey(stock)) {
            return stockMap.get(stock).getName();
        }
        else {
            return "N/A";
        }
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
    public LocalDate getLastPriceDate(String stockCode) {
        Stock stock = stockMap.get(stockCode);
        if (stock == null) return LocalDate.of(2000, 1, 1);
        return stock.getLastDate();
    }

    @Override
    public Double getPrice(String stock) {
        if (stockMap.containsKey(stock)) return stockMap.get(stock).getPrice();
        return 0.0;
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
        return stockMap.values().stream()
                .filter(stock -> stock.getCompanyId() == null && stock.getCountry() == Country.PH)
                .collect(Collectors.toList());
    }

    @Override
    public Collection<String> getStockList() {
        return stockMap.keySet();
    }

    @Override
    public boolean insertStock(Stock stock) {
        if (stockDAO.insertStock(stock)) {
            stockMap.put(stock.getCode(), stock);
            return true;
        }
        return false;
    }

    private void updateStockCodeMap() {
        stockCodeMap = new HashMap<>();
        for (var stock : stockMap.values()) {
            if (stock.getName() == null) continue;
            stockCodeMap.put(CommonUtil.trimStockName(stock.getName()), stock.getCode());
        }
    }

}
