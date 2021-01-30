package io.earlisreal.ejournal.parser.ledger;

import io.earlisreal.ejournal.dto.BankTransaction;
import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.util.Broker;
import io.earlisreal.ejournal.util.CommonUtil;

import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class AAALedgerParser implements LedgerParser {

    private List<TradeLog> tradeLogs;
    private List<BankTransaction> bankTransactions;

    @Override
    public void parse(List<String> lines) {
        tradeLogs = new ArrayList<>();
        bankTransactions = new ArrayList<>();
        for (int i = 0; i < lines.size(); ++i) {
            if (lines.get(i).startsWith("BEGINNING BALANCE:")) {
                parse(lines, i + 1);
                break;
            }
        }
    }

    private void parse(List<String> lines, int start) {
        for (int i = start; i < lines.size(); ++i) {
            try {
                String line = lines.get(i);
                if (line.startsWith("ENDING BALANCE:")) break;

                int dateIndex = line.indexOf(' ');
                LocalDate date = LocalDate.parse(line.substring(0, dateIndex), DateTimeFormatter.ofPattern("MM/dd/uuuu"));
                line = line.substring(dateIndex + 1);
                String reference = line.substring(line.indexOf('#') + 1, line.indexOf(' '));
                String[] tokens = line.split(" ");
                if (line.startsWith("BI#") || line.startsWith("SI#")) {
                    TradeLog tradeLog = new TradeLog();
                    tradeLog.setBuy(line.startsWith("BI#"));
                    tradeLog.setStock(tokens[2]);
                    tradeLog.setDate(date);
                    tradeLog.setInvoiceNo(reference);
                    tradeLog.setShares(CommonUtil.parseInt(tokens[1]));
                    tradeLog.setPrice(CommonUtil.parseDouble(tokens[4]));
                    tradeLog.setBroker(Broker.AAA);
                    tradeLogs.add(tradeLog);
                }
                else {
                    BankTransaction bankTransaction = new BankTransaction();
                    bankTransaction.setDate(date);
                    bankTransaction.setBroker(Broker.AAA);
                    bankTransaction.setReferenceNo(reference);
                    double amount = 0;
                    if (line.startsWith("OR#")) {
                        amount = CommonUtil.parseDouble(tokens[tokens.length - 2]);
                    }
                    else if (line.startsWith("CV#")) {
                        amount = CommonUtil.parseDouble(tokens[tokens.length - 3]) * -1;
                    }
                    else {
                        bankTransaction.setDividend(true);
                    }
                    bankTransaction.setAmount(amount);
                    bankTransactions.add(bankTransaction);
                }
            } catch (ParseException exception) {
                CommonUtil.handleException(exception);
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

}
