package io.earlisreal.ejournal.parser.ledger;

import io.earlisreal.ejournal.dto.BankTransaction;
import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.util.Broker;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TradeZeroLedgerParser implements LedgerParser {

    private List<TradeLog> tradeLogs;

    @Override
    public void parse(List<String> lines) {
        TradeLog[] logs = new TradeLog[lines.size() - 1];
        for (int i = 1; i < lines.size(); i++) {
            TradeLog log = new TradeLog();
            logs[i - 1] = log;

            String[] column = lines.get(i).split(",");
            var date = LocalDate.parse(column[1], DateTimeFormatter.ofPattern("MM/dd/uuuu"));
            log.setDate(date.atTime(LocalTime.parse(column[9], DateTimeFormatter.ofPattern("HH:mm:ss"))));

            String side = column[5];
            if ("B".equals(side)) {
                log.setBuy(true);
            }
            else if ("S".equals(side)) {
                log.setBuy(false);
            }
            else {
                log.setShort(true);
                log.setBuy("BC".equals(side));
            }

            double fees = 0;
            for (int j = 10; j < 17; j++) {
                fees += Double.parseDouble(column[j]);
            }
            log.setFees(fees);

            log.setStock(column[6]);
            log.setInvoiceNo((log.isBuy() ? "B" : "S") + log.getStock() + log.getDate().toEpochSecond(ZoneOffset.UTC));
            log.setShares(Integer.parseInt(column[7]));
            log.setPrice(Double.parseDouble(column[8]));
            log.setBroker(Broker.TRADE_ZERO);
        }

        tradeLogs = Arrays.asList(logs);
    }

    @Override
    public List<TradeLog> getTradeLogs() {
        return tradeLogs;
    }

    @Override
    public List<BankTransaction> getBankTransactions() {
        return Collections.emptyList();
    }

}
