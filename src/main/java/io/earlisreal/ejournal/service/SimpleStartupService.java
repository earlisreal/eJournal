package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.scraper.CompanyScraper;
import io.earlisreal.ejournal.scraper.StockListScraper;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static io.earlisreal.ejournal.util.CommonUtil.handleException;
import static io.earlisreal.ejournal.util.Configs.plotDirectory;
import static io.earlisreal.ejournal.util.Configs.stocksDirectory;

public class SimpleStartupService implements StartupService {

    private final StockListScraper stockListScraper;
    private final CompanyScraper companyScraper;
    private final StockService stockService;

    private final List<StartupListener> listenerList;

    SimpleStartupService(StockListScraper stockListScraper, CompanyScraper companyScraper, StockService stockService) {
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
            stockListScraper.parse();
            var stockMap = stockListScraper.getStockMap();
            stockService.updateStockPrice(stockListScraper.getPriceMap());
            return stockMap;
        });

        res.thenAcceptAsync(stockMap -> listenerList.forEach(StartupListener::onFinish));

        res.thenAcceptAsync(stockMap -> {
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
