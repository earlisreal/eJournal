package io.earlisreal.ejournal.util;

import io.earlisreal.ejournal.dto.BankTransaction;
import io.earlisreal.ejournal.dto.TradeLog;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public interface ParseUtil {

    static List<TradeLog> parseTradeLogs(List<String> csv) {
        List<TradeLog> tradeLogs = new ArrayList<>();
        for (String s : csv) {
            parseRecord(s).ifPresent(tradeLogs::add);
        }

        return tradeLogs;
    }

    static Optional<TradeLog> parseRecord(String line) {
        String[] columns = line.split(",");
        if (columns.length != 8) {
            System.out.println("Missing columns in row: " + line);
            System.out.println("Near: " + line);
            return Optional.empty();
        }

        if (columns[1].isBlank()) {
            System.out.println("Stock Cannot be blank on row: " + line);
        }

        try {
            LocalDateTime date = LocalDateTime.parse(columns[0]);
            boolean isBuy = "buy".equalsIgnoreCase(columns[2]);
            double price = Double.parseDouble(columns[3]);
            double shares = Double.parseDouble(columns[4]);
            boolean isShort = "short".equalsIgnoreCase(columns[5]);
            Broker broker = Broker.valueOf(columns[6]);
            String reference = columns[7];

            TradeLog tradeLog = new TradeLog(date, columns[1], isBuy, price, shares, reference, broker);
            tradeLog.setShort(isShort);
            return Optional.of(tradeLog);
        } catch (NumberFormatException numberFormatException) {
            System.out.println("Invalid format at row: " + line);
            System.out.println(numberFormatException.getMessage());
        }

        return Optional.empty();
    }

    static List<BankTransaction> parseBankTransactions(List<String> csv) {
        List<BankTransaction> bankTransactions = new ArrayList<>();
        for (int i = 0; i < csv.size(); ++i) {
            String[] columns = csv.get(i).split(",");
            if (columns.length != 8) {
                System.out.println("Missing columns in row: " + i);
                System.out.println("Near: " + columns[i]);
                break;
            }

            if (!columns[1].isBlank()) continue;

            try {
                LocalDate date = LocalDate.parse(columns[0]);
                boolean isDividend = "Dividend".equalsIgnoreCase(columns[2]);
                double amount = Double.parseDouble(columns[3]);
                Broker broker = Broker.valueOf(columns[6]);

                BankTransaction bankTransaction = new BankTransaction(date, isDividend, amount, broker, columns[7]);
                bankTransactions.add(bankTransaction);
            } catch (NumberFormatException numberFormatException) {
                System.out.println("Invalid format at row: " + i);
                System.out.println(numberFormatException.getMessage());
            }
        }

        return bankTransactions;
    }

}
