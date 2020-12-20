package io.earlisreal.ejournal.parser.invoice;

import io.earlisreal.ejournal.util.Broker;

import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static io.earlisreal.ejournal.util.CommonUtil.*;

public class YapsterInvoiceParser extends InvoiceParser {

    YapsterInvoiceParser() {
        super(Broker.YAPSTER);
    }

    @Override
    void parse(String invoice) {
        String[] lines = invoice.split(System.lineSeparator());
        parseInvoiceNo(lines[0]);
        for (String line : lines) {
            if (line.startsWith("Date ")) {
                parseDate(line);
            }
            if (line.startsWith("'")) {
                parseStock(line);
            }
            if (line.contains(" . VAT")) {
                try {
                    parsePrice(line);
                } catch (ParseException e) {
                    handleException(e);
                }
            }
            if (line.contains("BUY CONFIRMATION")) {
                setBuy(true);
            }
            if (line.contains("(INVOICE No.)")) {
                parseInvoiceNo(line);
            }
        }
    }

    private void parseInvoiceNo(String line) {
        setInvoiceNo(line.substring(line.indexOf("(INVOICE No.)") + 13).trim());
    }

    private void parseDate(String line) {
        setDate(LocalDate.parse(line.substring(5), DateTimeFormatter.ofPattern("dd -MMM-uu")));
    }

    private void parseStock(String line) {
        setStock(line.substring(1, line.lastIndexOf("'")));
    }

    private void parsePrice(String line) throws ParseException {
        String[] tokens = line.split(" . ");
        setShares(parseInt(tokens[0]));
        setPrice(parseDouble(tokens[1].substring(0, tokens[1].indexOf(' '))));
    }

}
