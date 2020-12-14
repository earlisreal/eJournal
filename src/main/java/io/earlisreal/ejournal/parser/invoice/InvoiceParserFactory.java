package io.earlisreal.ejournal.parser.invoice;

import io.earlisreal.ejournal.util.Broker;

public interface InvoiceParserFactory {

    static InvoiceParser getInvoiceParser(String broker) {
        if (Broker.AAA.getName().equals(broker)) {
            return new AAAEquitiesInvoiceParser();
        }
        if (Broker.YAPSTER.getName().equals(broker)) {
            return new YapsterInvoiceParser();
        }
        if (Broker.COL.getName().equals(broker)) {
            return new COLFinancialInvoiceParser();
        }

        throw new RuntimeException("Broker name " + broker + " is not supported");
    }

}
