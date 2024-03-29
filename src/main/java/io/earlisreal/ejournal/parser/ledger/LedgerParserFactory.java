package io.earlisreal.ejournal.parser.ledger;

import io.earlisreal.ejournal.util.Broker;

public interface LedgerParserFactory {

    static LedgerParser getLedgerParser(Broker broker) {
        if (Broker.COL == broker) {
            return new COLFinancialLedgerParser();
        }
        if (Broker.YAPSTER == broker) {
            return new YapsterLedgerParser();
        }
        if (Broker.AAA == broker) {
            return new AAALedgerParser();
        }
        if (Broker.ETORO == broker) {
            return new EToroLedgerParser();
        }
        if (Broker.TRADE_ZERO == broker) {
            return new TradeZeroLedgerParser();
        }

        throw new RuntimeException("Ledger Parser of Broker name " + broker + " is not supported");
    }

}
