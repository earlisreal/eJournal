package io.earlisreal.ejournal.util;

import io.earlisreal.ejournal.dto.TradeLog;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static io.earlisreal.ejournal.dto.TradeLog.COLUMN_COUNT;

public interface ParseUtils {

    static List<TradeLog> parseCsv(List<String> csv) {
        List<TradeLog> tradeLogs = new ArrayList<>();
        for (int i = 0; i < csv.size(); ++i) {
            String[] columns = csv.get(i).split(",");
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
                boolean isShort = "short".equalsIgnoreCase(columns[6]);

                TradeLog tradeLog = new TradeLog(date, columns[1], isBuy, price, shares, columns[5], isShort);
                tradeLogs.add(tradeLog);
            } catch (NumberFormatException numberFormatException) {
                System.out.println("Invalid format at row: " + i);
                System.out.println(numberFormatException.getMessage());
            }
        }

        return tradeLogs;
    }

}
