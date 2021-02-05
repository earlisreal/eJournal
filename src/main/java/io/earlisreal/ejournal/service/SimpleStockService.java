package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dao.StockDAO;
import io.earlisreal.ejournal.dto.Stock;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class SimpleStockService implements StockService {

    private final StockDAO stockDAO;

    private Map<String, Stock> stockMap;

    SimpleStockService(StockDAO stockDAO) {
        this.stockDAO = stockDAO;
        stockMap = stockDAO.getStockMap();
    }

    @Override
    public String getName(String stock) {
        return stockMap.get(stock).getName();
    }

    @Override
    public String getCode(String stock) {
        return stockMap.get(stock).getCode();
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
    public double getPrice(String stock) {
        return stockMap.get(stock).getPrice();
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
    }

    @Override
    public void updateStockId(List<Stock> stocks) {
        stockDAO.updateStockId(stocks);
    }

}
