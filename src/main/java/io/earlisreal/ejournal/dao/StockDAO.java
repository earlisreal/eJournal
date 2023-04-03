package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.dto.Stock;
import io.earlisreal.ejournal.util.Country;

import java.util.List;
import java.util.Map;

public interface StockDAO {

    void saveStocks(List<Stock> stocks, Country country);

    void updateStockId(List<Stock> stocks);

    Map<String, Stock> getStockMap();

    boolean insertStock(Stock stock);

}
