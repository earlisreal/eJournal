package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.database.FileDatabase;
import io.earlisreal.ejournal.dto.Stock;
import io.earlisreal.ejournal.util.Country;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.earlisreal.ejournal.util.Configs.DEBUG_MODE;
import static java.time.temporal.ChronoUnit.MILLIS;

public class CsvStockDAO implements StockDAO {

    @Override
    public void saveStocks(List<Stock> stocks, Country country) {
        var start = Instant.now();
        try {
            Path path = FileDatabase.getStockPath(country);
            Files.write(path, stocks.stream().map(Stock::toCsv).toList());
            if (DEBUG_MODE)
                System.out.println("Writing Stocks: " + MILLIS.between(start, Instant.now()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void updateStockId(List<Stock> stocks) {

    }

    @Override
    public Map<String, Stock> getStockMap() {
        var start = Instant.now();
        try {
            try (var ph = Files.lines(FileDatabase.getStockPath(Country.PH));
                 var us = Files.lines(FileDatabase.getStockPath(Country.US))) {
                var res = Stream.concat(ph, us)
                        .map(line -> {
                            Stock stock = new Stock();
                            var columns = line.split(",");
                            stock.setCode(columns[0]);
                            stock.setName(columns[1]);
                            stock.setCompanyId(columns[2].isEmpty() ? null : columns[2]);
                            stock.setSecurityId(columns[3].isEmpty() ? null : columns[3]);
                            stock.setPrice(Double.parseDouble(columns[4]));
                            stock.setCountry(Country.valueOf(columns[5]));
                            return stock;
                        })
                        .collect(Collectors.toMap(Stock::getCode, stock -> stock));
                if (DEBUG_MODE)
                    System.out.println("Reading Stocks: " + MILLIS.between(start, Instant.now()));
                return res;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean insertStock(Stock stock) {
        var start = Instant.now();
        Path path = FileDatabase.getStockPath(stock.getCountry());
        try {
            Files.writeString(path, stock.toCsv(), StandardOpenOption.APPEND);
            if (DEBUG_MODE)
                System.out.println("Inserting Stock: " + MILLIS.between(start, Instant.now()));
            return true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
