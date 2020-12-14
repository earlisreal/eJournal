package io.earlisreal.ejournal.util;

public interface CommonUtil {

    static void handleException(Exception e) {
        System.out.println(e.getMessage());
        e.printStackTrace();
    }

    static Broker identifyBroker(String invoice) {
        for (Broker broker : Broker.values()) {
            if (invoice.contains(broker.getUniqueIdentifier())) {
                return broker;
            }
        }

        throw new RuntimeException("Invoice of this format is not supported");
    }

}
