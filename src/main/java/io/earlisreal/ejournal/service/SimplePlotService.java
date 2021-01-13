package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.model.TradeSummary;
import io.earlisreal.ejournal.scraper.StockPriceScraper;
import io.earlisreal.ejournal.util.Configs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class SimplePlotService implements PlotService {

    private final StockService stockService;
    private final StockPriceScraper stockPriceScraper;

    private final Path plotDirectory;
    private final Path stocksDirectory;

    SimplePlotService(StockService stockService, StockPriceScraper stockPriceScraper) {
        this.stockService = stockService;
        this.stockPriceScraper = stockPriceScraper;

        plotDirectory = Path.of(Configs.DATA_DIR, "plot");
        stocksDirectory = Path.of(Configs.DATA_DIR, "stocks");
    }

    @Override
    public Path plot(TradeSummary tradeSummary) throws IOException {
        long start = System.currentTimeMillis();

        // TODO : Cache the image then skip this process when there is already an image

        String stock = tradeSummary.getStock();
        if (stockService.getLastPriceDate(stock).isBefore(tradeSummary.getCloseDate())) {
            // TODO : Skip plotting if closed now because there is no data yet, or add button to generate plot again,
            // to provide plot for now
            var csv = stockPriceScraper.scrapeStockPrice(tradeSummary.getStock());
            if (!csv.isEmpty()) {
                Files.createDirectories(stocksDirectory);
                Files.createDirectories(stocksDirectory);

                Files.write(stocksDirectory.resolve(tradeSummary.getStock() + ".csv"), csv,
                        StandardOpenOption.APPEND, StandardOpenOption.CREATE);
            }
        }

        // TODO : Use invoice id for image id. Or generate if there is no invoice for all trade logs

        StringBuilder buys = new StringBuilder();
        StringBuilder sells = new StringBuilder();
        for (TradeLog tradeLog : tradeSummary.getLogs()) {
            if (tradeLog.isBuy()) {
                if (buys.length() > 0) buys.append(',');
                buys.append(tradeLog.getDate().toString());
            } else {
                if (sells.length() > 0) sells.append(',');
                sells.append(tradeLog.getDate().toString());
            }
        }

        Path outputPath = plotDirectory.resolve(stock + ".png");

        ProcessBuilder processBuilder = new ProcessBuilder("python", "python/plot.py",
                stocksDirectory.resolve(stock + ".csv").toString(), outputPath.toString(), buys.toString(), sells.toString());
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        try {
            System.out.println(process.waitFor());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println(System.currentTimeMillis() - start);

        return outputPath;
    }

}
