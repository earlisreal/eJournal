package io.earlisreal.ejournal.parser.invoice;

import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static io.earlisreal.ejournal.util.CommonUtil.*;

public class COLFinancialInvoiceParser extends InvoiceParser {

    int shares;
    double amount;

    @Override
    void parse(String invoice) {
        shares = 0;
        amount = 0;
        String[] lines = invoice.split(System.lineSeparator());

        for (int i = 0; i < lines.length; ++i) {
            if (lines[i].equals("Trade Date:")) {
                parseDate(lines[i + 2]);
            }
            if (lines[i].equals("BOUGHT")) {
                setBuy(true);
            }
            if (lines[i].equals("Symbol:")) {
                setStock(lines[i + 2]);
            }
            if (lines[i].equals("Quantity")) {
                for (int j = i + 2; !lines[j].isEmpty(); ++j) {
                    try {
                        parseQuantity(lines[j]);
                    } catch (ParseException e) {
                        handleException(e);
                    }
                }
                setShares(shares);
                setPrice(amount / shares);
            }
        }
    }

    private void parseQuantity(String line) throws ParseException {
        String[] tokens = line.split(" ");
        shares += parseInt(tokens[0]);
        amount += parseDouble(tokens[2]);
    }

    private void parseDate(String line) {
        setDate(LocalDate.parse(line, DateTimeFormatter.ofPattern("MMMM dd, uuuu")));
    }

}
