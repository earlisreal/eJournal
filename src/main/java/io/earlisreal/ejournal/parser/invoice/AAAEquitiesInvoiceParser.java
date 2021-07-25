package io.earlisreal.ejournal.parser.invoice;

import io.earlisreal.ejournal.service.StockService;
import io.earlisreal.ejournal.util.Broker;
import io.earlisreal.ejournal.util.CommonUtil;

import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static io.earlisreal.ejournal.util.CommonUtil.*;

public class AAAEquitiesInvoiceParser extends InvoiceParser {

    private final StockService stockService;

    AAAEquitiesInvoiceParser(StockService stockService) {
        super(Broker.AAA);
        this.stockService = stockService;
    }

    void parse(String invoice) {
        String[] lines = invoice.split(System.lineSeparator());
        parseIsBuy(lines[0]);

        for (int i = 0; i < lines.length; ++i) {
            String line = lines[i];
            if (line.toUpperCase().contains("DUE DATE:")) {
                parseDate(line);
            }
            if (line.contains("COMM ")) {
                try {
                    parseStock(line, lines[i + 1]);
                } catch (ParseException e) {
                    CommonUtil.handleException(e);
                }
            }
        }
    }

    private void parseDate(String line) {
        String left;
        int l, r;
        if (line.contains("Date: ")) {
            left = "Date: ";
            String right = " Due Date: ";
            l = line.indexOf(left);
            r = line.indexOf(right);
        }
        else {
            left = "DATE ";
            String right = " DUE DATE: ";
            l = line.toUpperCase().indexOf(left);
            r = line.toUpperCase().indexOf(right);
        }

        String dateStr = line.substring(l + left.length(), r);
        setDate(LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("MMMM dd, uuuu")).atStartOfDay());
    }

    private void parseStock(String line, String nextLine) throws ParseException {
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

        setShares(parseInt(line.substring(shareIndex, shareEnd)));
        String name = line.substring(0, shareIndex).trim();
        String stock = stockService.getCode(trimStockName(name));
        if (stock == null) {
            int i = 0;
            while (Character.isLetter(nextLine.charAt(i))) {
                ++i;
            }
            String extension = nextLine.substring(0, i);
            if (!extension.isBlank()) {
                name += " " + extension;
            }
            stock = stockService.getCode(trimStockName(name));
        }
        setStock(stock);
        String end = line.substring(shareEnd).trim();
        setPrice(parseDouble(end.substring(0, end.indexOf(' '))));
    }

    private void parseIsBuy(String line) {
        setBuy(line.startsWith("BUY"));
        setInvoiceNo(line.substring(line.indexOf("No. ") + 4));
    }

}
