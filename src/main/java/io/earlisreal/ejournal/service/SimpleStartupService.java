package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dto.Stock;
import io.earlisreal.ejournal.scraper.CompanyScraper;
import io.earlisreal.ejournal.scraper.ScraperProvider;
import io.earlisreal.ejournal.scraper.StockScraper;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static io.earlisreal.ejournal.util.CommonUtil.handleException;
import static io.earlisreal.ejournal.util.Configs.plotDirectory;
import static io.earlisreal.ejournal.util.Configs.stocksDirectory;

public class SimpleStartupService implements StartupService {

    private final StockScraper stockListScraper;
    private final CompanyScraper companyScraper;
    private final StockService stockService;
    private final AnalyticsService analyticsService;
    private final TradeLogService tradeLogService;

    private final List<StartupListener> listenerList;

    SimpleStartupService(StockScraper stockListScraper, CompanyScraper companyScraper, StockService stockService,
                         TradeLogService tradeLogService, AnalyticsService analyticsService) {
        this.stockListScraper = stockListScraper;
        this.companyScraper = companyScraper;
        this.stockService = stockService;
        this.tradeLogService = tradeLogService;
        this.analyticsService = analyticsService;

        listenerList = new ArrayList<>();
    }

    @Override
    public void run() {
        createDirectories();
        manageStockList();

        tradeLogService.initialize();
        analyticsService.initialize();
    }

    @Override
    public void manageStockList() {
        // TODO Try to replace this with java fx service
        var scrapeList = CompletableFuture.supplyAsync(() -> {
            var stocks = stockListScraper.scrape();
            boolean hasNew = stockService.getStockCount() != stocks.size();
            stockService.updateStocks(stocks);

            return hasNew;
        });

        scrapeList.thenAcceptAsync(unused -> listenerList.forEach(StartupListener::onFinish));

        var scrapeCompanies = scrapeList.thenAcceptAsync(hasNew -> {
            if (hasNew) {
                stockService.updateStockId(companyScraper.scrapeCompanies());
            }
        });

        scrapeCompanies.thenAcceptAsync(unused -> {
            var stocks = ScraperProvider.getEmptyIdCompanyScraper().scrapeCompanies();
            stockService.updateStockId(stocks);
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

    @Override
    public void addStockPriceListener(StartupListener listener) {
        listenerList.add(listener);
    }

}
