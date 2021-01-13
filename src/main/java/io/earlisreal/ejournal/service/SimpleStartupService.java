package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.scraper.CompanyScraper;
import io.earlisreal.ejournal.scraper.StockListScraper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SimpleStartupService implements StartupService {

    private final StockListScraper stockListScraper;
    private final CompanyScraper companyScraper;
    private final StockService stockService;

    private final ExecutorService executorService;

    SimpleStartupService(StockListScraper stockListScraper, CompanyScraper companyScraper, StockService stockService) {
        this.stockListScraper = stockListScraper;
        this.companyScraper = companyScraper;
        this.stockService = stockService;

        executorService = Executors.newCachedThreadPool();
    }

    @Override
    public void manageStockList() {
        executorService.submit(() -> {
            stockListScraper.parse();
            var stockMap = stockListScraper.getStockMap();
            if (stockService.getStockCount() != stockMap.size()) {
                stockService.updateStockNameMap(stockMap);
                stockService.updateStockSecurityMap(companyScraper.scrapeCompanies());
            }
        });
    }

}
