package io.earlisreal.ejournal.identifier;

import io.earlisreal.ejournal.parser.Broker;

public interface BrokerIdentifier {

    static String identify(String invoice) {
        for (Broker broker : Broker.values()) {
            if (invoice.contains(broker.getUniqueIdentifier())) {
                return broker.getName();
            }
        }

        throw new RuntimeException("Invoice of this format is not supported");
    }

}
