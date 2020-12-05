package io.earlisreal.ejournal.parser;

import io.earlisreal.ejournal.dto.TradeLog;

public interface InvoiceParser {

    String parseAsCsv(String invoice);

    TradeLog parseAsObject(String invoice);

}
