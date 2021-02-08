package io.earlisreal.ejournal.parser.invoice;

import io.earlisreal.ejournal.service.ServiceProvider;
import io.earlisreal.ejournal.util.Broker;

public interface InvoiceParserFactory {

    static InvoiceParser getInvoiceParser(Broker broker) {
        if (Broker.AAA == broker) {
            return new AAAEquitiesInvoiceParser(ServiceProvider.getStockService());
        }
        if (Broker.YAPSTER == broker) {
            return new YapsterInvoiceParser();
        }

        throw new RuntimeException("Broker name " + broker + " is not supported");
    }

}
