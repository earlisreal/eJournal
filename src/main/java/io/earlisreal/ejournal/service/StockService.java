package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dto.Stock;
import io.earlisreal.ejournal.util.Country;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface StockService {

    Stock getStock(String code);

    String getName(String stock);

    String getCode(String stock);

    String getSecurityId(String stock);

    String getCompanyId(String stock);

    Double getPrice(String stock);

    int getStockCount();

    void updateStocks(List<Stock> stocks, Country country);

    void updateStockId(List<Stock> scrapeCompanies);

    List<Stock> getEmptyIds();

    Collection<String> getStockList();

    boolean insertStock(Stock stock);

}
