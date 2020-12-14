package io.earlisreal.ejournal.parser.invoice;

import io.earlisreal.ejournal.util.Broker;

public interface InvoiceParserFactory {

    static InvoiceParser getInvoiceParser(Broker broker) {
        if (Broker.AAA == broker) {
            return new AAAEquitiesInvoiceParser();
        }
        if (Broker.YAPSTER == broker) {
            return new YapsterInvoiceParser();
        }
        if (Broker.COL == broker) {
            return new COLFinancialInvoiceParser();
        }

        throw new RuntimeException("Broker name " + broker + " is not supported");
    }

}
