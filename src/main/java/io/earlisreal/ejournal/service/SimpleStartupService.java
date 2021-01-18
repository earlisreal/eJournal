package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.scraper.CompanyScraper;
import io.earlisreal.ejournal.scraper.StockListScraper;

import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

import static io.earlisreal.ejournal.util.CommonUtil.handleException;
import static io.earlisreal.ejournal.util.Configs.plotDirectory;
import static io.earlisreal.ejournal.util.Configs.stocksDirectory;

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
    public void run() {
        createDirectories();
        manageStockList();
    }

    @Override
    public void manageStockList() {
        CompletableFuture.runAsync(() -> {
            stockListScraper.parse();
            var stockMap = stockListScraper.getStockMap();
            stockService.updateStockPrice(stockListScraper.getPriceMap());

            if (stockService.getStockCount() != stockMap.size()) {
                stockService.updateStockNameMap(stockMap);
                stockService.updateStockSecurityMap(companyScraper.scrapeCompanies());
            }
        });
    }

    @Override
    public void createDirectories() {
        CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(stocksDirectory);
                Files.createDirectories(plotDirectory);
            } catch (IOException e) {
                handleException(e);
            }
        });
    }

}
