package io.earlisreal.ejournal.parser.ledger;

import io.earlisreal.ejournal.dto.BankTransaction;
import io.earlisreal.ejournal.dto.TradeLog;

import java.text.NumberFormat;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class COLFinancialLedgerParser implements LedgerParser {

    private final NumberFormat numberFormat;

    private List<TradeLog> tradeLogs;
    private List<BankTransaction> bankTransactions;

    COLFinancialLedgerParser() {
        numberFormat = NumberFormat.getInstance(Locale.getDefault());
    }

    public void parse(List<String> lines) {
        tradeLogs = new ArrayList<>();
        bankTransactions = new ArrayList<>();

        for (int i = 0; i < lines.size(); ++i) {
            if (lines.get(i).contains("TRX DATE")) {
                for (int j = i + 4; !lines.get(j).startsWith("---"); ++j) {
                    try {
                        if (!lines.get(j).trim().contains("BUY") && !lines.get(j).trim().contains("SELL")) {
                            bankTransactions.add(parseBankTransaction(lines.get(j)));
                        } else {
                            tradeLogs.add(parseTradeLog(lines.get(j)));
                        }
                    }
                    catch (ParseException e) {
                        System.out.println(e.getMessage());
                        e.printStackTrace();
                    }
                    ++i;
                }
                if (lines.get(i).contains("STOCK POS.")) {
                    break;
                }
            }
        }

        System.out.println(tradeLogs);
        System.out.println(bankTransactions);
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
            double amount = numberFormat.parse(tokens[5].trim()).doubleValue();
            bankTransaction.setAmount(amount);
        if (tokens[2].equals("WFUNDS")) {
            bankTransaction.setAmount(bankTransaction.getAmount() * -1);
        }
        else if (!tokens[2].trim().equals("OR")) {
            bankTransaction.setDividend(true);
        }

        return bankTransaction;
    }

    private TradeLog parseTradeLog(String line) throws ParseException {
        String[] tokens = line.split(":");
        LocalDate date = LocalDate.parse(tokens[1], DateTimeFormatter.ofPattern("MMdduuuu"));
        boolean isBuy = tokens[2].trim().equals("BUY");
        String stock = tokens[4].trim();
        int shares = numberFormat.parse(tokens[5].trim()).intValue();
        double price = numberFormat.parse(tokens[6].trim()).doubleValue();
        return new TradeLog(date, stock, isBuy, price, shares, null, false);
    }

}