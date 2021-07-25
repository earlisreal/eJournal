package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.model.TradeSummary;
import io.earlisreal.ejournal.scraper.StockPriceScraper;
import io.earlisreal.ejournal.util.CommonUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;

import static io.earlisreal.ejournal.util.Configs.plotDirectory;
import static io.earlisreal.ejournal.util.Configs.stocksDirectory;

public class SimplePlotService implements PlotService {

    private final StockService stockService;
    private final StockPriceScraper stockPriceScraper;

    SimplePlotService(StockService stockService, StockPriceScraper stockPriceScraper) {
        this.stockService = stockService;
        this.stockPriceScraper = stockPriceScraper;
    }

    @Override
    public Path plot(TradeSummary tradeSummary) throws IOException {
        Path imagePath = plotDirectory.resolve(generateImageName(tradeSummary));
        String stock = tradeSummary.getStock();
        LocalDate lastDate = stockService.getLastPriceDate(stock);

        if (LocalDate.now().equals(lastDate) || tradeSummary.isClosed() && Files.exists(imagePath)) {
            return imagePath;
        }

        if (!tradeSummary.isClosed() || lastDate.isBefore(tradeSummary.getCloseDate().toLocalDate().plusDays(7))) {
            var csv = stockPriceScraper.scrapeStockPrice(tradeSummary.getStock());
            if (!csv.isEmpty()) {
                Files.write(stocksDirectory.resolve(tradeSummary.getStock() + ".csv"), csv,
                        StandardOpenOption.APPEND, StandardOpenOption.CREATE);
            }
        }

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

        ProcessBuilder processBuilder = new ProcessBuilder("python", "python/plot.py",
                stocksDirectory.resolve(stock + ".csv").toString(),
                imagePath.toString(), tradeSummary.isClosed() ? "1" : "0", buys.toString(), sells.toString());
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            CommonUtil.handleException(e);
        }

        return imagePath;
    }

    private String generateImageName(TradeSummary tradeSummary) {
        String imageId = tradeSummary.getLogs().get(0).getBroker().name() + tradeSummary.getStock();
        long hash = 5381;
        for (TradeLog tradeLog : tradeSummary.getLogs()) {
            hash = (hash * 33 + tradeLog.getInvoiceNo().hashCode()) % Integer.MAX_VALUE;
        }

        return imageId + hash + ".png";
    }

}
