package io.earlisreal.ejournal.parser.email;

import io.earlisreal.ejournal.dto.BankTransaction;
import io.earlisreal.ejournal.dto.TradeLog;

import java.util.List;

public interface EmailParser {

    List<TradeLog> parseTradeLogs(String subject, String body);

    List<BankTransaction> parseBankTransactions(String subject, String body);

}
