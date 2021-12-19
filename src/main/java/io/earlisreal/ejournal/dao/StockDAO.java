package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.dto.Stock;

import java.util.List;
import java.util.Map;

public interface StockDAO {

    void updateStocks(List<Stock> stocks);

    void updateStockId(List<Stock> stocks);

    Map<String, Stock> getStockMap();

    boolean insertStock(Stock stock);

}
