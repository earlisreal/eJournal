package io.earlisreal.ejournal.parser.ledger;

import io.earlisreal.ejournal.dto.BankTransaction;
import io.earlisreal.ejournal.dto.TradeLog;

import java.util.List;

public interface LedgerParser {

    void parse(List<String> lines);

    List<TradeLog> getTradeLogs();

    List<BankTransaction> getBankTransactions();

}
