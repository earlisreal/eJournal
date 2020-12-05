package io.earlisreal.ejournal.parser;

import io.earlisreal.ejournal.dto.TradeLog;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class AAAEquitiesInvoiceParser implements InvoiceParser {

    private LocalDate date = null;
    private String stock = null;
    private int shares;
    private String buy;
    private double price;

    public String parseAsCsv(String invoice) {
        parse(invoice);
        return date + "," + stock + "," + buy + "," + price + "," + shares + "," + "long";
    }

    public TradeLog parseAsObject(String invoice) {
        parse(invoice);
        return new TradeLog(date, stock, buy.equalsIgnoreCase("BUY"), price, shares, null, false);
    }

    private void parse(String invoice) {
        for (String line : invoice.split(System.lineSeparator())) {
            if (line.contains("Date")) {
                parseDate(line);
            }
            if (line.contains("COMM ")) {
                parseStock(line);
            }
            if (line.contains("CONFIRMATION")) {
                parseIsBuy(line);
            }
        }
    }

    private void parseDate(String line) {
        String left = "Date: ";
        String right = " Due Date: ";
        int l = line.indexOf(left);
        int r = line.indexOf(right);

        String dateStr = line.substring(l + left.length(), r);
        date = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("MMMM dd, uuuu"));
    }

    private void parseStock(String line) {
        int shareIndex = -1;
        int shareEnd = -1;
        for (int i = 0; i < line.length(); ++i) {
            if (shareIndex != -1 && line.charAt(i) == ' ') {
                shareEnd = i;
                break;
            }
            if (shareIndex == -1 && Character.isDigit(line.charAt(i))) {
                shareIndex = i;
            }
        }

        shares = Integer.parseInt(line.substring(shareIndex, shareEnd));
        stock = line.substring(0, shareIndex).trim();
        String end = line.substring(shareEnd).trim();
        price = Double.parseDouble(end.substring(0, end.indexOf(' ')));
    }

    private void parseIsBuy(String line) {
        buy = line.substring(0, line.indexOf(' '));
    }

}
