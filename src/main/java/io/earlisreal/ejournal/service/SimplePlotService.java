package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.model.TradeSummary;
import io.earlisreal.ejournal.scraper.PSECompanyScraper;
import io.earlisreal.ejournal.scraper.ScraperProvider;
import io.earlisreal.ejournal.util.Configs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class SimplePlotService implements PlotService {

    private final StockService stockService;

    SimplePlotService(StockService stockService) {
        this.stockService = stockService;
    }

    public String plot(TradeSummary tradeSummary) throws IOException {
        long start = System.currentTimeMillis();

        // TODO : Cache the image then skip this process when there is already an image

        String stock = tradeSummary.getStock();
        if (stockService.getLastPriceDate(stock).isBefore(tradeSummary.getCloseDate())) {
            // TODO : Skip plotting if closed now because there is no data yet
            var csv = ScraperProvider.getStockPriceScraper().scrapeStockPrice(tradeSummary.getStock());
            if (!csv.isEmpty()) {
                Files.createDirectories(Path.of(Configs.DATA_DIR, "stocks"));
                Files.createDirectories(Path.of(Configs.DATA_DIR, "stocks"));
                Files.write(Path.of(Configs.DATA_DIR, "stocks", tradeSummary.getStock() + ".csv"), csv,
                        StandardOpenOption.APPEND, StandardOpenOption.CREATE);
            }
        }

        // TODO : Create id or hash for each trade summary for the filename
        ProcessBuilder processBuilder = new ProcessBuilder("python",
                "python/plot.py", ".eJournal/stocks/BDO.csv", ".eJournal/plot/BDO.png", "2021-01-05", "2021-01-08");
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        try {
            System.out.println(process.waitFor());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println(System.currentTimeMillis() - start);

        return "";
    }

}
