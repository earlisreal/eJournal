package io.earlisreal.ejournal.util;

public interface BrokerIdentifier {

    static Broker identify(String invoice) {
        for (Broker broker : Broker.values()) {
            if (invoice.contains(broker.getUniqueIdentifier())) {
                return broker;
            }
        }

        throw new RuntimeException("Invoice of this format is not supported");
    }

}
