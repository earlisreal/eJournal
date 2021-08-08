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
                    year = 2;
                }

                if (year == 2 && month == 12) break;
            }

            while (rightDate.minusDays(30).isAfter(maxDate)) {
                rightDate = rightDate.minusDays(30);
            }

            int dateIndex = 0;
            while (dateIndex < dates.size() && !leftDate.isAfter(rightDate)) {
                LocalDate date = dates.get(dateIndex);
                while (date.isBefore(leftDate.plusDays(31))) {
                    ++dateIndex;
                }

                if (date.isAfter(leftDate.minusDays(1))) {
                    String slice =  "year" + year + "month" + month;
                    var csv = alphaVantageClients.get(clientIndex).get1minuteHistory(stock.getCode(), slice);
                    saveCsv(stock, csv);
                }

                leftDate = leftDate.plusDays(30);
            }

            clientIndex = (clientIndex + 1 ) % alphaVantageClients.size();
        });
    }

    private void saveCsv(Stock stock, List<String> csv) {
        String lastDate = stock.getLastDate().format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss"));
        List<String> records = new ArrayList<>();
        for (int i = csv.size() - 1; i >= 0; --i) {
            if (csv.get(i).startsWith(lastDate)) continue;
            records.add(csv.get(i));
        }

        if (!records.isEmpty()) {
            try {
                Files.write(stocksDirectory.resolve("us").resolve(stock.getCode() + ".csv"), records,
                        StandardOpenOption.APPEND, StandardOpenOption.CREATE);
            } catch (IOException e) {
                CommonUtil.handleException(e);
            }
        }
    }

}
