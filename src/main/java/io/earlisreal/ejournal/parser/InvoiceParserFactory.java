package io.earlisreal.ejournal.parser;

public interface InvoiceParserFactory {

    static InvoiceParser getInvoiceParser(String broker) {
        if (Broker.AAA.getName().equals(broker)) {
            return new AAAEquitiesInvoiceParser();
        }

        throw new RuntimeException("Broker name " + broker + " is not supported");
    }

}
