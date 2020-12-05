package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dao.StrategyDAO;
import io.earlisreal.ejournal.dao.TradeLogDAO;
import io.earlisreal.ejournal.dto.Strategy;
import io.earlisreal.ejournal.dto.TradeLog;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

import static io.earlisreal.ejournal.dto.TradeLog.COLUMN_COUNT;

public class SimpleTradeLogService implements TradeLogService {

    private final TradeLogDAO tradeLogDAO;
    private final StrategyDAO strategyDAO;

    public SimpleTradeLogService(TradeLogDAO tradeLogDAO, StrategyDAO strategyDAO) {
        this.tradeLogDAO = tradeLogDAO;
        this.strategyDAO = strategyDAO;
    }

    public void insertCsvFromConsole() {
        System.out.println("Follow this csv format - date (yyyy-mm-dd), stock, action (buy/sell), price, shares, strategy, type (long/short)");
        System.out.println("Enter csv records:");
        List<String> records = new ArrayList<>();
        Scanner scanner = new Scanner(System.in);
        while (true) {
            String line = scanner.nextLine();
            if (line.isBlank()) {
                break;
            }

            records.add(line);
        }

        Map<String, Integer> strategies = strategyDAO.queryAll().stream().collect(Collectors.toMap(Strategy::getName, Strategy::getId));
        List<TradeLog> tradeLogs = new ArrayList<>();

        for (int i = 0; i < records.size(); ++i) {
            String[] columns = records.get(i).split(",");
            if (columns.length != COLUMN_COUNT) {
                System.out.println("Missing columns in row: " + i);
                System.out.println("Near: " + columns[i]);
                break;
            }

            try {
                LocalDate date = LocalDate.parse(columns[0]);
                if (columns[1].isBlank()) {
                    System.out.println("Stock Cannot be blank on row: " + i);
                }

                boolean isBuy = "buy".equalsIgnoreCase(columns[2]);
                double price = Double.parseDouble(columns[3]);
                int shares = Integer.parseInt(columns[4]);
                Integer strategyId = strategies.getOrDefault(columns[5], null);
                boolean isShort = "short".equalsIgnoreCase(columns[6]);

                TradeLog tradeLog = new TradeLog(date, columns[1], isBuy, price, shares, strategyId, isShort);
                tradeLogs.add(tradeLog);
            } catch (NumberFormatException numberFormatException) {
                System.out.println("Invalid format at row: " + i);
                System.out.println(numberFormatException.getMessage());
            }
        }

        int inserted = tradeLogDAO.insertLog(tradeLogs);
        System.out.println(inserted + " Records Inserted");
    }

}
