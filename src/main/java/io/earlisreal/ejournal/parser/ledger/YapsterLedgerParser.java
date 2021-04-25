package io.earlisreal.ejournal.parser.ledger;

import io.earlisreal.ejournal.dto.BankTransaction;
import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.util.Broker;
import io.earlisreal.ejournal.util.CommonUtil;

import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class YapsterLedgerParser implements LedgerParser {

    private final List<TradeLog> tradeLogs;
    private final List<BankTransaction> bankTransactions;
    private final Map<String, Double> ipoPrice;

    YapsterLedgerParser() {
        tradeLogs = new ArrayList<>();
        bankTransactions = new ArrayList<>();
        ipoPrice = new HashMap<>();
    }

    @Override
    public void parse(List<String> lines) {
        for (int i = 0; i < lines.size(); ++i) {
            if (lines.get(i).startsWith("BEGINNING BALANCE: ")) {
                parse(lines, i + 2);
                break;
            }
        }
    }

    private void parse(List<String> lines, int start) {
        for (int i = start; i < lines.size(); ++i) {
            String line = lines.get(i);
            if (line.equals(".")) {
                break;
            }

            try {
                if (line.contains("DM#")) {
                    // TODO : If there is a case where DM and SCM is in different Ledger, save ipo price to DB
                    var tokens = line.split(" ");
                    double total = CommonUtil.parseDouble(tokens[tokens.length - 2]);
                    double shares = CommonUtil.parseDouble(tokens[tokens.length - 3]);
                    double price = total / shares;

                    while (true) {
                        ++i;
                        if (lines.get(i).contains(" IPO")) {
                            tokens = lines.get(i).split(" ");
                            String stock = tokens[tokens.length - 2];
                            ipoPrice.put(stock, price);
                            break;
                        }
                    }

                    continue;
                }

                boolean isDividend = line.contains(" CM#");
                if (isDividend && !line.contains("Cash Dividend")) continue;

                boolean isIpo = line.contains("SCM#");
                boolean isBuy = line.contains("BI#");
                boolean isWithdrawal = line.contains("CV#");
                int dateIndex = line.indexOf(' ');
                if (dateIndex == -1) continue;

                String dateStr = line.substring(0, dateIndex);
                LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("MM/dd/uuuu"));
                int refIndex = dateIndex + 1;
                while (!Character.isDigit(line.charAt(refIndex))) {
                    ++refIndex;
                }
                line = line.substring(refIndex);
                int refEndIndex = line.indexOf(' ');
                String refNo = line.substring(0, refEndIndex);

                line = line.substring(refEndIndex).trim();
                if (Character.isDigit(line.charAt(0))) {
                    TradeLog tradeLog = parseTrade(line, isIpo);
                    tradeLog.setBuy(isIpo || isBuy);

                    tradeLog.setBroker(Broker.YAPSTER);
                    tradeLog.setDate(date);
                    tradeLog.setInvoiceNo(refNo);
                    tradeLogs.add(tradeLog);
                }
                else {
                    BankTransaction bankTransaction = parseBank(line);
                    bankTransaction.setDate(date);
                    bankTransaction.setReferenceNo(refNo);
                    bankTransaction.setAmount(bankTransaction.getAmount() * (isWithdrawal ? -1 : 1));
                    bankTransaction.setDividend(isDividend);
                    bankTransactions.add(bankTransaction);
                }
            } catch (DateTimeParseException ignore) {
                // Line not starts with a date. Just ignore
            } catch (ParseException e) {
                CommonUtil.handleException(e);
            }
        }
    }

    @Override
    public List<TradeLog> getTradeLogs() {
        return tradeLogs;
    }

    @Override
    public List<BankTransaction> getBankTransactions() {
        return bankTransactions;
    }

    private TradeLog parseTrade(String line, boolean isIpo) throws ParseException {
        TradeLog tradeLog = new TradeLog();
        var tokens = line.split(" ");
        tradeLog.setShares(CommonUtil.parseInt(tokens[0]));
        tradeLog.setStock(tokens[1]);
        if (isIpo) {
            tradeLog.setPrice(ipoPrice.get(tokens[1]));
        }
        else {
            tradeLog.setPrice(CommonUtil.parseDouble(tokens[2]));
        }

        return tradeLog;
    }

    private BankTransaction parseBank(String line) throws ParseException {
        BankTransaction bankTransaction = new BankTransaction();
        bankTransaction.setBroker(Broker.YAPSTER);
        var tokens = line.split(" ");
        bankTransaction.setAmount(CommonUtil.parseDouble(tokens[tokens.length - 2]));

        return bankTransaction;
    }

}
