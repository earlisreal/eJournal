package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.client.AlphaVantageClient;
import io.earlisreal.ejournal.dto.Stock;
import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.exception.AlphaVantageLimitException;
import io.earlisreal.ejournal.model.TradeSummary;
import io.earlisreal.ejournal.util.CommonUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static io.earlisreal.ejournal.util.CommonUtil.handleException;
import static io.earlisreal.ejournal.util.Configs.STOCKS_DIRECTORY;

public class AsyncIntradayService implements IntradayService {

    private final AlphaVantageClient alphaVantageClient;
    private final StockService stockService;
    private final ScheduledExecutorService executorService;
    private final Set<String> lock;
    private final Map<String, List<TradeSummary>> stockDateMap;

    private int minuteOffset;
    private int callCount;

    AsyncIntradayService(AlphaVantageClient alphaVantageClient, StockService stockService) {
        this.alphaVantageClient = alphaVantageClient;
        this.stockService = stockService;
        this.executorService = Executors.newScheduledThreadPool(5);
        this.lock = new HashSet<>();
        this.stockDateMap = new HashMap<>();
    }

    @Override
    public void download(List<TradeSummary> summaries, Consumer<List<TradeSummary>> onDownloadFinish) {
        stockDateMap.clear();
        List<TradeLog> tradeLogs = new ArrayList<>();
        Map<String, List<TradeSummary>> summaryMap = new HashMap<>();
        for (TradeSummary summary : summaries) {
            if (summary.isDayTrade()) {
                tradeLogs.addAll(summary.getLogs());
            }

            if (!summaryMap.containsKey(summary.getStock())) summaryMap.put(summary.getStock(), new ArrayList<>());
            summaryMap.get(summary.getStock()).add(summary);

            String stockDate = summary.getStock() + summary.getOpenDate().toLocalDate();
            if (!stockDateMap.containsKey(stockDate)) stockDateMap.put(stockDate, new ArrayList<>());
            stockDateMap.get(stockDate).add(summary);
        }

        Map<Stock, Set<LocalDate>> map = new HashMap<>();
        tradeLogs.sort(Comparator.comparing(TradeLog::getDate));
        for (TradeLog log : tradeLogs) {
            Stock stock = stockService.getStock(log.getStock());
            if (stock == null) {
                stock = new Stock();
                stock.setCode(log.getStock());
                stock.setCountry(log.getBroker().getCountry());
                if (stockService.insertStock(stock)) {
                    System.out.println("Stock " + stock.getCode() + " not found and was inserted");
                }
            }

            if (!map.containsKey(stock)) map.put(stock, new LinkedHashSet<>());
            map.get(stock).add(log.getDate().toLocalDate());
        }

        for (var entry : map.entrySet()) {
            download(entry.getKey(), new ArrayList<>(entry.getValue()), onDownloadFinish);
        }
    }

    public void download(Stock stock, List<LocalDate> dates, Consumer<List<TradeSummary>> onDownloadFinish) {
        if (lock.contains(stock.getCode())) return;
        lock.add(stock.getCode());

        System.out.println("Downloading Intraday data for " + stock.getCode());
        int year = 1;
        int month = 1;
        LocalDate now = LocalDate.now();
        LocalDate maxDate = dates.get(dates.size() - 1);
        LocalDate leftDate = now.minusDays(30);
        LocalDate rightDate = now;
        while (leftDate.isAfter(dates.get(0))) {
            leftDate = leftDate.minusDays(30);
            ++month;
            if (leftDate.getYear() != now.getYear()) {
                month = 1;
                ++year;
            }

            if (year > 2) break;
        }

        while (rightDate.minusDays(30).isAfter(maxDate)) {
            rightDate = rightDate.minusDays(30);
        }

        int secondsLeft = 60 - LocalTime.now().getMinute();
        int dateIndex = 0;
        while (dateIndex < dates.size() && !leftDate.isAfter(rightDate)) {
            LocalDate date = dates.get(dateIndex);
            while (dateIndex < dates.size() - 1 && date.isAfter(leftDate.plusDays(30))) {
                ++dateIndex;
                date = dates.get(dateIndex);
            }

            if (date.isAfter(leftDate.minusDays(1))) {
                String slice =  "year" + year + "month" + month;
                String key = stock.getCode() + date;

                Runnable task = () -> {
                    try {
                        var csv = alphaVantageClient.get1minuteHistory(stock.getCode(), slice);
                        saveCsv(stock, csv);
                        onDownloadFinish.accept(stockDateMap.get(key));
                    } catch (AlphaVantageLimitException e) {
                        CommonUtil.handleException(e);
                        executorService.shutdownNow();
                    }
                };

                if (minuteOffset == 0) {
                    executorService.execute(task);
                }
                else {
                    executorService.schedule(task, minuteOffset * 60L + secondsLeft, TimeUnit.SECONDS);
                }

                ++callCount;
                if (callCount == 5) {
                    callCount = 0;
                    ++minuteOffset;
                }
            }

            leftDate = leftDate.plusDays(30);
            --month;
            if (month < 1) {
                month = 12;
                --year;
            }

            if (year < 1) break;
        }

        lock.remove(stock.getCode());
    }

    private void saveCsv(Stock stock, List<String> csv) {
        LocalDate lastDate = stock.getLastDate();
        List<String> records = new ArrayList<>();
        for (int i = csv.size() - 1; i >= 0; --i) {
            String record = csv.get(i);
            if (record.trim().isEmpty()) continue;
            LocalDate date = parseDate(record);
            if (lastDate != null && date.isBefore(lastDate)) continue;
            records.add(record);
        }

        if (!records.isEmpty()) {
            try {
                Files.write(STOCKS_DIRECTORY.resolve(stock.getCountry().name()).resolve(stock.getCode() + ".csv"), records,
                        StandardOpenOption.APPEND, StandardOpenOption.CREATE);
                System.out.println(records.size() + " records added to " + stock.getCode());
                stockService.updateLastDate(stock.getCode(), parseDate(csv.get(0)).plusDays(1));
            } catch (IOException e) {
                handleException(e);
            }
        }
    }

    private LocalDate parseDate(String record) {
        return LocalDate.parse(record.substring(0, record.indexOf(' ')), DateTimeFormatter.ISO_LOCAL_DATE);
    }

}
