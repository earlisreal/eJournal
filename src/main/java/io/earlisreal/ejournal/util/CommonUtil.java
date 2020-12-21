package io.earlisreal.ejournal.util;

import java.text.NumberFormat;
import java.text.ParseException;

public interface CommonUtil {

    static void handleException(Exception e) {
        System.out.println(e.getMessage());
        e.printStackTrace();
    }

    static Broker identifyBroker(String invoice) {
        for (Broker broker : Broker.values()) {
            if (broker.getUniqueIdentifier() != null && invoice.contains(broker.getUniqueIdentifier())) {
                return broker;
            }
        }

        throw new RuntimeException("Invoice of this format is not supported");
    }

    static int parseInt(String text) throws ParseException {
        if (text.isBlank()) return 0;
        return NumberFormat.getNumberInstance().parse(text).intValue();
    }

    static double parseDouble(String text) throws ParseException {
        if (text.isBlank()) return 0;
        return NumberFormat.getNumberInstance().parse(text).doubleValue();
    }

    static String trimStockName(String name) {
        int index = name.lastIndexOf(", Inc.");
        if (index != -1) {
            name = name.substring(0, index);
        }
        return name.toUpperCase();
    }

}
