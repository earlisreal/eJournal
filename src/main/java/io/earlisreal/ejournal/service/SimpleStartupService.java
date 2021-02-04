package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.scraper.CompanyScraper;
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

    private final List<StartupListener> listenerList;

    SimpleStartupService(StockScraper stockListScraper, CompanyScraper companyScraper, StockService stockService) {
        this.stockListScraper = stockListScraper;
        this.companyScraper = companyScraper;
        this.stockService = stockService;

        listenerList = new ArrayList<>();
    }

    @Override
    public void run() {
        createDirectories();
        manageStockList();
    }

    @Override
    public void manageStockList() {
        var res = CompletableFuture.supplyAsync(() -> {
            var stocks = stockListScraper.scrape();
            boolean hasNew = stockService.getStockCount() > stocks.size();
            stockService.updateStocks(stocks);

            return hasNew;
        });

        res.thenAcceptAsync(unused -> listenerList.forEach(StartupListener::onFinish));

        res.thenAcceptAsync(hasNew -> {
            if (hasNew) {
                stockService.updateStockId(companyScraper.scrapeCompanies());
            }
        });
    }

    @Override
    public void createDirectories() {
        CompletableFuture.runAsync(() -> {
            try {
                // TODO : Move this to the checker if first run
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
