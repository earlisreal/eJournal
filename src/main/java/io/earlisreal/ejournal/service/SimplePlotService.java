package io.earlisreal.ejournal.service;

import com.jsoniter.output.JsonStream;
import io.earlisreal.ejournal.dto.Stock;
import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.model.IntradayPlotArgument;
import io.earlisreal.ejournal.model.TradeSummary;
import io.earlisreal.ejournal.scraper.StockPriceScraper;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.earlisreal.ejournal.util.CommonUtil.handleException;
import static io.earlisreal.ejournal.util.Configs.INTRADAY_PLOT_DIRECTORY;
import static io.earlisreal.ejournal.util.Configs.PLOT_DIRECTORY;
import static io.earlisreal.ejournal.util.Configs.STOCKS_DIRECTORY;

public class SimplePlotService implements PlotService {

    private final StockService stockService;
    private final StockPriceScraper stockPriceScraper;
    private final IntradayService intradayService;

    private final Set<Path> imageCache;

    SimplePlotService(StockService stockService, StockPriceScraper stockPriceScraper, IntradayService intradayService) {
        this.stockService = stockService;
        this.stockPriceScraper = stockPriceScraper;
        this.intradayService = intradayService;
        imageCache = new HashSet<>();
    }

    @Override
    public void reloadCache() {
        try {
            Files.list(INTRADAY_PLOT_DIRECTORY).forEach(imageCache::add);
            Files.list(PLOT_DIRECTORY).forEach(path -> {
                if (!Files.isDirectory(path)) imageCache.add(path);
            });
        } catch (IOException e) {
            handleException(e);
        }
    }

    @Override
    public Path plot(TradeSummary tradeSummary) throws IOException {
        if (tradeSummary.getOpenDate().toLocalDate().equals(tradeSummary.getCloseDate().toLocalDate())) {
            return plotIntraday(tradeSummary);
        }

        Path imagePath = PLOT_DIRECTORY.resolve(generateImageName(tradeSummary));

        String stock = tradeSummary.getStock();
        LocalDate lastDate = stockService.getLastPriceDate(stock);

        if (LocalDate.now().equals(lastDate) || tradeSummary.isClosed() && imageCache.contains(imagePath)) {
            return imagePath;
        }

        if (!tradeSummary.isClosed() || lastDate.isBefore(tradeSummary.getCloseDate().toLocalDate().plusDays(7))) {
            var csv = stockPriceScraper.scrapeStockPrice(tradeSummary.getStock());
            if (!csv.isEmpty()) {
                Files.write(STOCKS_DIRECTORY.resolve(tradeSummary.getStock() + ".csv"), csv,
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

        run("python", "python/scripts/plot.py", STOCKS_DIRECTORY.resolve(stock + ".csv").toString(),
                imagePath.toString(), tradeSummary.isClosed() ? "1" : "0", buys.toString(), sells.toString());
        cacheImage(imagePath);
        return imagePath;
    }

    private Path plotIntraday(TradeSummary tradeSummary) throws IOException {
        Path imagePath = INTRADAY_PLOT_DIRECTORY.resolve(generateImageName(tradeSummary));
        if (imageCache.contains(imagePath)) return imagePath;

        String symbol = tradeSummary.getStock();
        var dataPath = STOCKS_DIRECTORY.resolve(tradeSummary.getCountry().name()).resolve(symbol + ".csv");
        if (!Files.exists(dataPath) || stockService.getLastPriceDate(symbol).isBefore(tradeSummary.getCloseDate().toLocalDate())) {
            intradayService.download(List.of(tradeSummary));
            System.out.println("Trying to Download missing " + symbol + " Data");
            return null;
        }

        Map<String, List<Double>> buys = new HashMap<>();
        Map<String, List<Double>> shorts = new HashMap<>();
        Map<String, List<Double>> sells = new HashMap<>();
        int buysLength = 0;
        int sellsLength = 0;
        int shortsLength = 0;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:'00'");
        for (TradeLog log : tradeSummary.getLogs()) {
            var date = log.getDate();
            if (date.getSecond() > 0) {
                date = date.plusMinutes(1);
            }
            String dateStr = date.format(formatter);
            if (log.isBuy()) {
                if (!buys.containsKey(dateStr)) buys.put(dateStr, new ArrayList<>());
                buys.get(dateStr).add(log.getPrice());
                buysLength = Math.max(buysLength, buys.get(dateStr).size());
            }
            else {
                if (log.isShort()) {
                    if (!shorts.containsKey(dateStr)) shorts.put(dateStr, new ArrayList<>());
                    shorts.get(dateStr).add(log.getPrice());
                    shortsLength = Math.max(shortsLength, shorts.get(dateStr).size());
                }
                else {
                    if (!sells.containsKey(dateStr)) sells.put(dateStr, new ArrayList<>());
                    sells.get(dateStr).add(log.getPrice());
                    sellsLength = Math.max(sellsLength, sells.get(dateStr).size());
                }
            }
        }

        IntradayPlotArgument argument = new IntradayPlotArgument();
        argument.setOutputPath(imagePath.toString());
        argument.setDataPath(dataPath.toString());
        argument.setBuys(buys);
        argument.setSells(sells);
        argument.setShorts(shorts);
        argument.setStart(tradeSummary.getOpenDate().format(formatter));
        argument.setEnd(tradeSummary.getCloseDate().format(formatter));
        argument.setBuysLength(buysLength);
        argument.setSellsLength(sellsLength);
        argument.setShortsLength(shortsLength);
        String json = JsonStream.serialize(argument);

        StringBuilder args = new StringBuilder();
        for (Character c : json.toCharArray()) {
            if (c == '"') {
                args.append("\\");
            }
            args.append(c);
        }

        run("python", "python/scripts/intraday-plot.py", args.toString());
        cacheImage(imagePath);
        return imagePath;
    }

    private void cacheImage(Path path) {
        imageCache.add(path);
    }

    private String generateImageName(TradeSummary tradeSummary) {
        String imageId = tradeSummary.getLogs().get(0).getBroker().name() + tradeSummary.getStock();
        long hash = 5381;
        for (TradeLog tradeLog : tradeSummary.getLogs()) {
            hash = (hash * 33 + tradeLog.getInvoiceNo().hashCode()) % Integer.MAX_VALUE;
        }

        return imageId + hash + ".png";
    }

    private void run(String... command) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        try {
            int res = process.waitFor();
            String output = IOUtils.toString(process.getInputStream());
            if (res != 0) {
                System.out.println("Process returned " + res);
                System.out.println(output);
            }
        } catch (InterruptedException e) {
            handleException(e);
        }

    }

}
