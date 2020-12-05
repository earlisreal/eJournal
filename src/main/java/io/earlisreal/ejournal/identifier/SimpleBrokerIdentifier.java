package io.earlisreal.ejournal.identifier;

import io.earlisreal.ejournal.parser.Broker;

public class SimpleBrokerIdentifier implements BrokerIdentifier {

    public String identify(String invoice) {
        for (Broker broker : Broker.values()) {
            if (invoice.contains(System.lineSeparator() + broker.getUniqueIdentifier() + System.lineSeparator())) {
                return broker.getName();
            }
        }

        return null;
    }

}
