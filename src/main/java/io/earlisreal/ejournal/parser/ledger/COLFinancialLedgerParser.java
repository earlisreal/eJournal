package io.earlisreal.ejournal.parser.ledger;

import io.earlisreal.ejournal.dto.BankTransaction;
import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.util.Broker;

import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

import static io.earlisreal.ejournal.util.CommonUtil.*;

public class COLFinancialLedgerParser implements LedgerParser {

    private List<TradeLog> tradeLogs;
    private List<BankTransaction> bankTransactions;

    COLFinancialLedgerParser() {}

    public void parse(List<String> lines) {
        tradeLogs = new ArrayList<>();
        bankTransactions = new ArrayList<>();

        Map<String, TradeLog> tradeMap = new HashMap<>();
        int transactionIndex = 4;
        for (int i = 0; i < lines.size(); ++i) {
            if (lines.get(i).startsWith(":TRX DATE")) {
                for (int j = i + transactionIndex; !lines.get(j).startsWith("---"); ++j) {
                    try {
                        if (!lines.get(j).trim().contains("BUY") && !lines.get(j).trim().contains("SELL")) {
                            bankTransactions.add(parseBankTransaction(lines.get(j)));
                        } else {
                            TradeLog tradeLog = parseTradeLog(lines.get(j));
                            if (tradeLog.getStock().isBlank()) {
                                TradeLog lastTradeLog = tradeLogs.get(tradeLogs.size() - 1);
                                lastTradeLog.setShares(lastTradeLog.getShares() + tradeLog.getShares());
                            }
                            else {
                                if (tradeMap.containsKey(tradeLog.getInvoiceNo())) {
                                    TradeLog sameLog = tradeMap.get(tradeLog.getInvoiceNo());
                                    sameLog.setPrice(tradeLog.getPrice() + sameLog.getPrice());
                                    sameLog.setShares(sameLog.getShares() + tradeLog.getShares());
                                }
                                else {
                                    tradeLogs.add(tradeLog);
                                    tradeMap.put(tradeLog.getInvoiceNo(), tradeLog);
                                }
                            }
                        }
                    }
                    catch (DateTimeParseException | ParseException ignored) {
                    }
                    ++i;
                }
                transactionIndex = 2;
            }
            if (lines.get(i).startsWith(":STOCK POS.")) {
                break;
            }
        }

        for (TradeLog tradeLog : tradeLogs) {
            tradeLog.setPrice(tradeLog.getPrice() / tradeLog.getShares());
        }
    }

    public List<TradeLog> getTradeLogs() {
        return tradeLogs;
    }

    public List<BankTransaction> getBankTransactions() {
        return bankTransactions;
    }

    private BankTransaction parseBankTransaction(String line) throws ParseException {
        String[] tokens = line.split(":");
        BankTransaction bankTransaction = new BankTransaction();

        bankTransaction.setDate(LocalDate.parse(tokens[1], DateTimeFormatter.ofPattern("MMdduuuu")));
        double amount = parseDouble(tokens[5].trim());
        bankTransaction.setBroker(Broker.COL);
        bankTransaction.setAmount(amount);
        String action = tokens[2].trim();
        if (action.equals("WFUNDS")) {
            bankTransaction.setAmount(bankTransaction.getAmount() * -1);
        }
        if (action.equals("CDIV+")) {
            bankTransaction.setDividend(true);
        }
        bankTransaction.setReferenceNo(tokens[3].trim());

        return bankTransaction;
    }

    private TradeLog parseTradeLog(String line) throws ParseException {
        String[] tokens = line.split(":");
        LocalDate date = LocalDate.parse(tokens[1], DateTimeFormatter.ofPattern("MMdduuuu"));
        boolean isBuy = tokens[2].trim().equals("BUY");
        String stock = tokens[4].trim();
        int shares = parseInt(tokens[5].trim());
        double price = parseDouble(tokens[7].trim());
        String referenceNo = date.format(DateTimeFormatter.ofPattern("MMdduu")) + (isBuy ? "1" : "0") + stock;
        return new TradeLog(date, stock, isBuy, price, shares, referenceNo, Broker.COL);
    }

}
