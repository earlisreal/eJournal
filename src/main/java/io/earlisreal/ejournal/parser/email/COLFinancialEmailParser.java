package io.earlisreal.ejournal.parser.email;

import io.earlisreal.ejournal.dto.BankTransaction;
import io.earlisreal.ejournal.dto.TradeLog;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.earlisreal.ejournal.util.CommonUtil.*;

public class COLFinancialEmailParser implements EmailParser {

    COLFinancialEmailParser() {}

    public List<TradeLog> parseTradeLogs(String emailBody) {
        Document document = Jsoup.parse(emailBody);
        var texts = document.getElementsByTag("font").eachText();
        StringBuilder invoice = new StringBuilder();

        List<TradeLog> tradeLogs = new ArrayList<>();
        for (String text : texts) {
            if (text.startsWith("Important: Please")) {
                TradeLog log = parseLog(invoice.toString());
                tradeLogs.add(log);
                invoice = new StringBuilder();
            }
            else {
                invoice.append(text).append(System.lineSeparator());
            }
        }

        return tradeLogs;
    }

    private TradeLog parseLog(String body) {
        TradeLog tradeLog = new TradeLog();
        String[] lines = body.split(System.lineSeparator());

        for (int i = 0; i < lines.length; ++i) {
            if (lines[i].equals("BOUGHT")) {
                tradeLog.setBuy(true);
            }
            if (lines[i].equals("Trade Date:")) {
                tradeLog.setDate(LocalDate.parse(lines[i + 1], DateTimeFormatter.ofPattern("MMMM dd, uuuu")));
            }
            if (lines[i].equals("Symbol:")) {
                tradeLog.setStock(lines[i + 1]);
            }
            if (lines[i].equals("Totals")) {
                try {
                    tradeLog.setShares(parseInt(lines[i + 1]));
                    tradeLog.setPrice(parseDouble(lines[i + 2]) / tradeLog.getShares());
                } catch (ParseException e) {
                    handleException(e);
                }
            }
        }

        tradeLog.setInvoiceNo(tradeLog.getDate().format(DateTimeFormatter.ofPattern("MMdduu"))
                + (tradeLog.isBuy() ? "1" : "0") + tradeLog.getStock());

        return tradeLog;
    }

    public List<BankTransaction> parseBankTransactions(String emailBody) {
        return Collections.emptyList();
    }

}
