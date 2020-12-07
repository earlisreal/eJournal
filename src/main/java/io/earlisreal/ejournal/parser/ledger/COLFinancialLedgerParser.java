package io.earlisreal.ejournal.parser.ledger;

import io.earlisreal.ejournal.dto.TradeLog;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class COLFinancialLedgerParser implements LedgerParser {

    COLFinancialLedgerParser() {}

    public List<TradeLog> parseAsObjects(List<String> lines) {
        List<TradeLog> tradeLogs = new ArrayList<>();
        for (int i = 0; i < lines.size(); ++i) {
            if (lines.get(i).contains("TRX DATE")) {
                for (int j = i + 4; !lines.get(j).startsWith("---"); ++j) {
                    if (lines.get(j).contains("OR")) {
                        // TODO : Parse as Bank Transaction
                    }
                    else {
                        tradeLogs.add(parseTradeLog(lines.get(j)));
                    }
                }
                break;
            }
        }
        return null;
    }

    private TradeLog parseTradeLog(String line) {
        String[] tokens = line.split(":");
        LocalDate date = LocalDate.parse(tokens[1], DateTimeFormatter.ofPattern("MMdduuuu"));
        boolean isBuy = tokens[2].trim().equals("BUY");
        String stock = tokens[4].trim();
        int shares = Integer.parseInt(tokens[5].trim());
        double price = Double.parseDouble(tokens[6].trim());
        return new TradeLog(date, stock, isBuy, price, shares, null, false);
    }

}
