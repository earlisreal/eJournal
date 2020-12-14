package io.earlisreal.ejournal.parser.invoice;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class AAAEquitiesInvoiceParser extends InvoiceParser {

    AAAEquitiesInvoiceParser() {}

    void parse(String invoice) {
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
        setDate(LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("MMMM dd, uuuu")));
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

        setShares(Integer.parseInt(line.substring(shareIndex, shareEnd)));
        setStock(line.substring(0, shareIndex).trim());
        String end = line.substring(shareEnd).trim();
        setPrice(Double.parseDouble(end.substring(0, end.indexOf(' '))));
    }

    private void parseIsBuy(String line) {
        setBuy(line.substring(0, line.indexOf(' ')).equalsIgnoreCase("BUY"));
    }

}
