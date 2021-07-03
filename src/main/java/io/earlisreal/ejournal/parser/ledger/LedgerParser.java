package io.earlisreal.ejournal.parser.ledger;

import io.earlisreal.ejournal.dto.BankTransaction;
import io.earlisreal.ejournal.dto.TradeLog;

import java.util.List;

public interface LedgerParser {

    default void parse(List<String> lines) {
        throw new UnsupportedOperationException();
    }

    default void parse(String filename) {
        throw new UnsupportedOperationException();
    }

    List<TradeLog> getTradeLogs();

    List<BankTransaction> getBankTransactions();

}
