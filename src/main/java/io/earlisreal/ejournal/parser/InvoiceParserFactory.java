package io.earlisreal.ejournal.parser;

import io.earlisreal.ejournal.util.Broker;

public interface InvoiceParserFactory {

    static InvoiceParser getInvoiceParser(String broker) {
        if (Broker.AAA.getName().equals(broker)) {
            return new AAAEquitiesInvoiceParser();
        }
        if (Broker.YAPSTER.getName().equals(broker)) {
            return new YapsterInvoiceParser();
        }

        throw new RuntimeException("Broker name " + broker + " is not supported");
    }

}
