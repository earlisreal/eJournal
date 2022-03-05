package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.scraper.CompanyScraper;
import io.earlisreal.ejournal.scraper.ExchangeRateScraper;
import io.earlisreal.ejournal.scraper.ScraperProvider;
import io.earlisreal.ejournal.scraper.StockScraper;
import io.earlisreal.ejournal.util.Country;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static io.earlisreal.ejournal.util.CommonUtil.handleException;
import static io.earlisreal.ejournal.util.CommonUtil.runAsync;
import static io.earlisreal.ejournal.util.Configs.INTRADAY_PLOT_DIRECTORY;
import static io.earlisreal.ejournal.util.Configs.PLOT_DIRECTORY;
import static io.earlisreal.ejournal.util.Configs.STOCKS_DIRECTORY;

public class SimpleStartupService implements StartupService {

    private final StockScraper stockListScraper;
    private final CompanyScraper companyScraper;
    private final ExchangeRateScraper exchangeRateScraper;
    private final StockService stockService;
    private final AnalyticsService analyticsService;
    private final TradeLogService tradeLogService;
    private final CacheService cacheService;
    private final CompanyScraper usCompanyScraper;

    private final List<StartupListener> listenerList;

    SimpleStartupService(StockScraper stockListScraper, CompanyScraper companyScraper,
                         ExchangeRateScraper exchangeRateScraper, StockService stockService,
                         TradeLogService tradeLogService, AnalyticsService analyticsService, CacheService cacheService,
                         CompanyScraper usCompanyScraper) {

        this.stockListScraper = stockListScraper;
        this.companyScraper = companyScraper;
        this.exchangeRateScraper = exchangeRateScraper;
        this.stockService = stockService;
        this.tradeLogService = tradeLogService;
        this.analyticsService = analyticsService;
        this.cacheService = cacheService;
        this.usCompanyScraper = usCompanyScraper;

        listenerList = new ArrayList<>();
    }

    @Override
    public void run() {
        createDirectories();
        // TODO : Add handling if Broker is PH or US
//        manageStockList();
        manageUsStockList();

        tradeLogService.applyFilter(cacheService.getStartFilter(), cacheService.getEndFilter());

        tradeLogService.initialize();
        analyticsService.initialize();
    }

    private void manageUsStockList() {
        runAsync(() -> {
            var list = usCompanyScraper.scrapeCompanies();
            stockService.updateStocks(list);
            System.out.println("Managing US Stock List Done");
        });
    }

    @Override
    public void manageStockList() {
        // TODO Try to replace this with java fx service
        var scrapeList = CompletableFuture.supplyAsync(() -> {
            var stocks = stockListScraper.scrape();
            boolean hasNew = stockService.getStockCount() != stocks.size();
            stockService.updateStocks(stocks);
            System.out.println("Downloading PH companies done");

            return hasNew;
        });

        scrapeList.thenAcceptAsync(unused -> listenerList.forEach(StartupListener::onFinish));

        var scrapeCompanies = scrapeList.thenAcceptAsync(hasNew -> {
            if (hasNew) {
                stockService.updateStockId(companyScraper.scrapeCompanies());
                System.out.println("Updating Stock ID done");
            }
        });

        scrapeCompanies.thenAcceptAsync(unused -> {
            var stocks = ScraperProvider.getEmptyIdCompanyScraper().scrapeCompanies();
            if (!stocks.isEmpty()) {
                stockService.updateStockId(stocks);
                System.out.println("Updating empty stock IDs Done");
            }
        });
    }

    @Override
    public void createDirectories() {
        runAsync(() -> {
            try {
                for (Country country : Country.values()) {
                    Files.createDirectories(STOCKS_DIRECTORY.resolve(country.name()));
                }
                Files.createDirectories(PLOT_DIRECTORY);
                Files.createDirectories(INTRADAY_PLOT_DIRECTORY);
            } catch (IOException e) {
                handleException(e);
            }
            System.out.println("Creating data directories Done");
        });
    }

    @Override
    public void addStockPriceListener(StartupListener listener) {
        listenerList.add(listener);
    }

}
