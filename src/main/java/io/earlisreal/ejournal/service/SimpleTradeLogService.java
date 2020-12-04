package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dao.TradeLogDAO;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class SimpleTradeLogService implements TradeLogService {

    private final TradeLogDAO tradeLogDAO;

    public SimpleTradeLogService(TradeLogDAO tradeLogDAO) {
        this.tradeLogDAO = tradeLogDAO;
    }

    public void insertCsvFromConsole() {
        System.out.println("Follow this csv format - date (yyyy-mm-dd), stock, buy/sell, price, shares, strategy, short");
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

        for (int i = 0; i < records.size(); ++i) {
            String[] columns = records.get(i).split(",");
            if (columns.length != 7) {
                System.out.println("Missing columns in row: " + i);
                System.out.println("Near: " + columns[i]);
                break;
            }

            try {
                Instant date = LocalDate.parse(columns[0]).atStartOfDay(ZoneId.systemDefault()).toInstant();
                if (columns[1].isBlank()) {
                    System.out.println("Stock Cannot be blank on row: " + i);
                }
                boolean isBuy = Boolean.parseBoolean(columns[2]);
                double price = Double.parseDouble(columns[3]);
                int shares = Integer.parseInt(columns[4]);
                String strategy = columns[5].isBlank() ? null : columns[5];
                boolean isShort = Boolean.parseBoolean(columns[6]);

                boolean success = tradeLogDAO.insertLog(date, columns[1], isBuy, price, shares, strategy, isShort);
                if (!success) {
                    System.out.println("Error on Saving Data");
                    return;
                }
            } catch (NumberFormatException numberFormatException) {
                System.out.println("Invalid format at row: " + i);
                System.out.println(numberFormatException.getMessage());
            }
        }

        System.out.println("Records Inserted");
    }

}
