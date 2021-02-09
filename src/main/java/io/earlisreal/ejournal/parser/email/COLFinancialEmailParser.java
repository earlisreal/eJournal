package io.earlisreal.ejournal.parser.email;

import io.earlisreal.ejournal.dto.BankTransaction;
import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.util.Broker;
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

    private final DateTimeFormatter formatter;
    private final DateTimeFormatter referenceFormatter;

    COLFinancialEmailParser() {
       formatter = DateTimeFormatter.ofPattern("MMMM dd, uuuu");
       referenceFormatter = DateTimeFormatter.ofPattern("MMdduu");
    }

    public List<TradeLog> parseTradeLogs(String subject, String body) {
        if (!subject.startsWith("COL Trading Confirmation")) return Collections.emptyList();

        Document document = Jsoup.parse(body);
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
        tradeLog.setBroker(Broker.COL);
        String[] lines = body.split(System.lineSeparator());

        for (int i = 0; i < lines.length; ++i) {
            if (lines[i].equals("BOUGHT")) {
                tradeLog.setBuy(true);
            }
            if (lines[i].equals("Trade Date:")) {
                tradeLog.setDate(LocalDate.parse(lines[i + 1], formatter));
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

        tradeLog.setInvoiceNo(tradeLog.getDate().format(referenceFormatter)
                + (tradeLog.isBuy() ? "1" : "0") + tradeLog.getStock());

        return tradeLog;
    }

    public List<BankTransaction> parseBankTransactions(String subject, String body) {
        if (subject.startsWith("COL Trading Confirmation")) return Collections.emptyList();

        Document document = Jsoup.parse(body);
        try {
            if (subject.equals("Notice of Debit Adjustment")) {
                return parseWithdrawal(document);
            }

            if (subject.equals("Acknowledgement of Deposit")) {
                return parseDeposit(document);
            }

            if (subject.startsWith("Notice of Cash Dividend")) {
                return parseDividend(document);
            }
        } catch (ParseException exception) {
            handleException(exception);
        }

        return Collections.emptyList();
    }

    private List<BankTransaction> parseDividend(Document document) throws ParseException {
        BankTransaction bankTransaction = new BankTransaction();
        bankTransaction.setBroker(Broker.COL);
        bankTransaction.setDividend(true);

        var texts = document.getElementsByTag("p").eachText();
        bankTransaction.setDate(LocalDate.parse(texts.get(2), formatter));
        for (String text : texts) {
            if (text.startsWith("Stock Code")) {
                bankTransaction.setReferenceNo(text.split("Stock Code : ")[1]
                        + DateTimeFormatter.ofPattern("MMuu").format(bankTransaction.getDate()));
            }
            String net = "Net Amount (Php) : ";
            if (text.startsWith(net)) {
                bankTransaction.setAmount(parseDouble(text.substring(net.length())));
            }
        }

        return List.of(bankTransaction);
    }

    private List<BankTransaction> parseDeposit(Document document) throws ParseException {
        BankTransaction bankTransaction = new BankTransaction();
        bankTransaction.setBroker(Broker.COL);

        var texts = document.getElementsByTag("font").eachText();
        bankTransaction.setDate(LocalDate.parse(texts.get(2), formatter));
        for (String text : texts) {
            String net = "NET AMOUNT CREDITED TO ACCOUNT : Php ";
            if (text.startsWith(net)) {
                bankTransaction.setAmount(parseDouble(text.substring(net.length())));
            }
            String receipt = "NV Official Acknowledgement Receipt No : ";
            if (text.startsWith(receipt)) {
                bankTransaction.setReferenceNo(text.substring(receipt.length()));
            }
        }

        return List.of(bankTransaction);
    }

    private List<BankTransaction> parseWithdrawal(Document document) throws ParseException {
        BankTransaction bankTransaction = new BankTransaction();
        bankTransaction.setBroker(Broker.COL);

        var texts = document.getElementsByTag("font").eachText();
        bankTransaction.setDate(LocalDate.parse(texts.get(0), formatter));
        bankTransaction.setReferenceNo(referenceFormatter.format(bankTransaction.getDate()));
        for (String text : texts) {
            String amount = "Amount : Php ";
            if (text.startsWith(amount)) {
                bankTransaction.setAmount(-1 * parseDouble(text.substring(amount.length())));
            }
        }

        return List.of(bankTransaction);
    }

}
