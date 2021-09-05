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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static io.earlisreal.ejournal.util.CommonUtil.handleException;
import static io.earlisreal.ejournal.util.Configs.STOCKS_DIRECTORY;

public class AsyncIntradayService implements IntradayService {

    private final Queue<AlphaVantageClient> alphaVantageClients;
    private final StockService stockService;
    private final ExecutorService executorService;
    private final Set<String> lock;

    AsyncIntradayService(List<AlphaVantageClient> alphaVantageClients, StockService stockService) {
        this.alphaVantageClients = new ConcurrentLinkedQueue<>(alphaVantageClients);
        this.stockService = stockService;
        this.executorService = Executors.newFixedThreadPool(alphaVantageClients.size());
        this.lock = new HashSet<>();
        System.out.println(alphaVantageClients.size() + " API keys provided.");
    }

    @Override
    public void download(List<TradeSummary> summaries, Consumer<List<TradeSummary>> onDownloadFinish) {
        List<TradeLog> tradeLogs = new ArrayList<>();
        Map<String, List<TradeSummary>> summaryMap = new HashMap<>();
        for (TradeSummary summary : summaries) {
            if (summary.isDayTrade()) {
                tradeLogs.addAll(summary.getLogs());
            }

            if (summaryMap.containsKey(summary.getStock())) {
                summaryMap.get(summary.getStock()).add(summary);
            }
            else {
                List<TradeSummary> summaryList = new ArrayList<>();
                summaryList.add(summary);
                summaryMap.put(summary.getStock(), summaryList);
            }
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
            if (map.containsKey(stock)) {
                map.get(stock).add(log.getDate().toLocalDate());
            }
            else {
                Set<LocalDate> list = new LinkedHashSet<>();
                list.add(log.getDate().toLocalDate());
                map.put(stock, list);
            }
        }

        for (var entry : map.entrySet()) {
            download(entry.getKey(), new ArrayList<>(entry.getValue()), () -> onDownloadFinish.accept(summaryMap.get(entry.getKey().getCode())));
        }
    }

    public void download(Stock stock, List<LocalDate> dates, Runnable onDownloadFinish) {
        if (lock.contains(stock.getCode())) return;
        lock.add(stock.getCode());
        executorService.execute(() -> {
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

            AlphaVantageClient client = alphaVantageClients.poll();
            if (client == null) {
                System.out.println("Imbalance Thread pool size and AlphaVantage pool size. This should never happen.");
                return;
            }
            int dateIndex = 0;
            while (dateIndex < dates.size() && !leftDate.isAfter(rightDate)) {
                LocalDate date = dates.get(dateIndex);
                while (dateIndex < dates.size() - 1 && date.isAfter(leftDate.plusDays(30))) {
                    ++dateIndex;
                    date = dates.get(dateIndex);
                }

                if (date.isAfter(leftDate.minusDays(1))) {
                    String slice =  "year" + year + "month" + month;
                    try {
                        var csv = client.get1minuteHistory(stock.getCode(), slice);
                        saveCsv(stock, csv);
                    }
                    catch (AlphaVantageLimitException e) {
                        CommonUtil.handleException(e);
                        return;
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

            alphaVantageClients.add(client);
            lock.remove(stock.getCode());
            onDownloadFinish.run();
        });
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
