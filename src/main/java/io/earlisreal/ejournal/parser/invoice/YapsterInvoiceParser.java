package io.earlisreal.ejournal.parser.invoice;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class YapsterInvoiceParser extends InvoiceParser {

    YapsterInvoiceParser() {}

    @Override
    void parse(String invoice) {
        for (String line : invoice.split(System.lineSeparator())) {
            if (line.startsWith("Date ")) {
                parseDate(line);
            }
            if (line.startsWith("'")) {
                parseStock(line);
            }
            if (line.contains(" . VAT")) {
                parsePrice(line);
            }
            if (line.contains("BUY CONFIRMATION")) setBuy(true);
        }
    }

    private void parseDate(String line) {
        setDate(LocalDate.parse(line.substring(5), DateTimeFormatter.ofPattern("dd -MMM-uu")));
    }

    private void parseStock(String line) {
        setStock(line.substring(1, line.lastIndexOf("'")));
    }

    private void parsePrice(String line) {
        String[] tokens = line.split(" . ");
        setShares(Integer.parseInt(tokens[0]));
        setPrice(Double.parseDouble(tokens[1].substring(0, tokens[1].indexOf(' '))));
    }

}
