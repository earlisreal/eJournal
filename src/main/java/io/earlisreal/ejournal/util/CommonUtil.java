package io.earlisreal.ejournal.util;

import java.sql.Date;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

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

    static Broker identifyBrokerLenient(String invoice) {
        for (Broker broker : Broker.values()) {
            if (broker.getUniqueIdentifier() != null
                    && invoice.toUpperCase().contains(broker.getUniqueIdentifier().toUpperCase())) {
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
        if (text == null || text.isBlank()) return 0;
        return NumberFormat.getNumberInstance().parse(text).doubleValue();
    }

    static String trimStockName(String name) {
        var extensions = List.of(" PHILIPPINES, INC", " PHILIPPINES, INC.", ", INC", ", INC.",
                " INCORPORATED", " CORP", " CORP.", " CORPORATION");
        name = name.toUpperCase();
        for (String extension : extensions) {
            if (name.endsWith(extension)) {
                name = name.substring(0, name.lastIndexOf(extension));
            }
        }
        return name.toUpperCase();
    }

    static String prettify(double num) {
        return NumberFormat.getNumberInstance().format(round(num));
    }

    static double round(double num) {
        return Math.round(num * 100) / 100.0;
    }

    static Date toSqlDate(LocalDate localDate) {
        return new Date(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

    static Timestamp toTimestamp(LocalDateTime localDateTime) {
        return new Timestamp(localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

}
