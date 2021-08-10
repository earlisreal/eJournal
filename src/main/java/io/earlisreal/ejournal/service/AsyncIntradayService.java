package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.client.AlphaVantageClient;
import io.earlisreal.ejournal.dto.Stock;
import io.earlisreal.ejournal.util.CommonUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.earlisreal.ejournal.util.Configs.stocksDirectory;

public class AsyncIntradayService implements IntradayService {

    private final List<AlphaVantageClient> alphaVantageClients;
    private final ExecutorService executorService;

    private int clientIndex;

    AsyncIntradayService(List<AlphaVantageClient> alphaVantageClients) {
        this.alphaVantageClients = alphaVantageClients;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    @Override
    public void download(Stock stock, List<LocalDate> dates) {
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

            int dateIndex = 0;
            while (dateIndex < dates.size() && !leftDate.isAfter(rightDate)) {
                LocalDate date = dates.get(dateIndex);
                while (dateIndex < dates.size() - 1 && date.isAfter(leftDate.plusDays(30))) {
                    ++dateIndex;
                    date = dates.get(dateIndex);
                }

                if (date.isAfter(leftDate.minusDays(1))) {
                    String slice =  "year" + year + "month" + month;
                    var csv = alphaVantageClients.get(clientIndex).get1minuteHistory(stock.getCode(), slice);
                    saveCsv(stock, csv);
                }

                leftDate = leftDate.plusDays(30);
                --month;
                if (month < 1) {
                    month = 12;
                    --year;
                }

                if (year < 1) break;
            }

            clientIndex = (clientIndex + 1 ) % alphaVantageClients.size();
        });
    }

    private void saveCsv(Stock stock, List<String> csv) {
        LocalDate lastDate = stock.getLastDate();
        List<String> records = new ArrayList<>();
        var formatter = DateTimeFormatter.ofPattern("uuuu-MM-dd");
        for (int i = csv.size() - 1; i >= 0; --i) {
            String record = csv.get(i);
            if (record.trim().isEmpty()) continue;
            LocalDate date = LocalDate.parse(record.substring(0, record.indexOf(' ')), formatter);
            if (lastDate != null && date.isBefore(lastDate)) continue;
            records.add(record);
        }

        if (!records.isEmpty()) {
            try {
                Files.write(stocksDirectory.resolve(stock.getCountry().name()).resolve(stock.getCode() + ".csv"), records,
                        StandardOpenOption.APPEND, StandardOpenOption.CREATE);
                System.out.println(records.size() + " records added to " + stock.getCode());
            } catch (IOException e) {
                CommonUtil.handleException(e);
            }
        }
    }

}
