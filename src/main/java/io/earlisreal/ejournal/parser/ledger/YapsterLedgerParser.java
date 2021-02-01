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
import java.util.List;

public class YapsterLedgerParser implements LedgerParser {

    private final List<TradeLog> tradeLogs;
    private final List<BankTransaction> bankTransactions;

    YapsterLedgerParser() {
        tradeLogs = new ArrayList<>();
        bankTransactions = new ArrayList<>();
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
                boolean isBuy = line.contains("BI#");
                boolean isDividend = line.contains("CM#");
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
                    TradeLog tradeLog = parseTrade(line);
                    tradeLog.setDate(date);
                    tradeLog.setInvoiceNo(refNo);
                    tradeLog.setBuy(isBuy);
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
                // Dividend Just ignore
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

    private TradeLog parseTrade(String line) throws ParseException {
        TradeLog tradeLog = new TradeLog();
        var tokens = line.split(" ");
        tradeLog.setShares(CommonUtil.parseInt(tokens[0]));
        tradeLog.setStock(tokens[1]);
        tradeLog.setPrice(CommonUtil.parseDouble(tokens[2]));
        tradeLog.setBroker(Broker.YAPSTER);

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
