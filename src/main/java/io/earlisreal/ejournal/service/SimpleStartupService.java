package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.scraper.CompanyScraper;
import io.earlisreal.ejournal.scraper.StockListScraper;

import java.util.concurrent.CompletableFuture;

public class SimpleStartupService implements StartupService {

    private final StockListScraper stockListScraper;
    private final CompanyScraper companyScraper;
    private final StockService stockService;

    SimpleStartupService(StockListScraper stockListScraper, CompanyScraper companyScraper, StockService stockService) {
        this.stockListScraper = stockListScraper;
        this.companyScraper = companyScraper;
        this.stockService = stockService;
    }

    @Override
    public void manageStockList() {
        CompletableFuture.runAsync(() -> {
            stockListScraper.parse();
            var stockMap = stockListScraper.getStockMap();
            if (stockService.getStockCount() != stockMap.size()) {
                stockService.updateStockNameMap(stockMap);
                stockService.updateStockSecurityMap(companyScraper.scrapeCompanies());
            }
        });
    }

}
