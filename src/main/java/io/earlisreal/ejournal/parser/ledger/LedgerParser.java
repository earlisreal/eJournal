package io.earlisreal.ejournal.parser.ledger;

import io.earlisreal.ejournal.dto.TradeLog;

import java.util.List;

public interface LedgerParser {

    List<TradeLog> parseAsObjects(List<String> lines);

}
