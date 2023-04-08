package io.earlisreal.ejournal.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static io.earlisreal.ejournal.util.Configs.DEBUG_MODE;
import static java.time.temporal.ChronoUnit.MILLIS;

public final class CommonUtil {

    private static final DateTimeFormatter COMMON_FORMATTER = DateTimeFormatter.ofPattern("MM-dd-uuuu HH:mm:ss");

    private CommonUtil() {}

    public static void handleException(Throwable e) {
        System.out.println(e.getMessage());
        e.printStackTrace();
    }

    public static Broker identifyBroker(String invoice) {
        for (Broker broker : Broker.values()) {
            if (broker.getUniqueIdentifier() != null && invoice.contains(broker.getUniqueIdentifier())) {
                return broker;
            }
        }

        throw new RuntimeException("Invoice of this format is not supported");
    }

    public static Broker identifyBrokerLenient(String invoice) {
        for (Broker broker : Broker.values()) {
            if (broker.getUniqueIdentifier() != null
                    && invoice.toUpperCase().contains(broker.getUniqueIdentifier().toUpperCase())) {
                return broker;
            }
        }

        throw new RuntimeException("Invoice of this format is not supported");
    }

    public static int parseInt(String text) throws ParseException {
        if (text.isBlank()) return 0;
        return NumberFormat.getNumberInstance().parse(text).intValue();
    }

    public static double parseDouble(String text) throws ParseException {
        if (text == null || text.isBlank()) return 0;
        return NumberFormat.getNumberInstance().parse(text).doubleValue();
    }

    public static String trimStockName(String name) {
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

    public static String prettify(LocalDateTime localDateTime) {
        return COMMON_FORMATTER.format(localDateTime);
    }

    public static String prettify(double num) {
        return NumberFormat.getNumberInstance().format(round(num));
    }

    public static double round(double num) {
        return Math.round(num * 100) / 100.0;
    }

    public static Date toSqlDate(LocalDate localDate) {
        return new Date(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

    public static Timestamp toTimestamp(LocalDateTime localDateTime) {
        return new Timestamp(localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

    public static void runAsync(Runnable runnable) {
        new Thread(runnable).start();
    }

    public static String normalize(long seconds) {
        long minutes = seconds / 60;
        seconds %= 60;
        long hours = minutes / 60;
        minutes %= 60;
        long days = hours / 24;
        hours %= 60;
        String hold = "";
        if (days > 0) hold += days + " days";
        else {
            if (hours > 0) hold += hours + "h ";
            if (minutes > 0) hold += minutes + "m ";
            if (seconds > 0) hold += seconds + "s";
        }
        return hold;
    }

    private static final class ExecutorServiceHolder {
        private static final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(5);
    }

    public static ScheduledExecutorService getExecutorService() {
        return ExecutorServiceHolder.executorService;
    }

    public static LocalDate getLastDailyDate(String symbol, Country country) {
        Path path = Configs.STOCKS_DIRECTORY.resolve(country.name()).resolve("daily").resolve(symbol + ".csv");
        return getLastDate(path, ",");
    }

    public static LocalDate getLastIntraDate(String symbol, Country country) {
        Path path = Configs.STOCKS_DIRECTORY.resolve(country.name()).resolve(symbol + ".csv");
        return getLastDate(path, " ");
    }

    private static LocalDate getLastDate(Path path, String pivot) {
        try {
            String content = Files.readString(path);
            int count = 0;
            for (int i = content.length() - 1; i >= 0; --i) {
                if (content.charAt(i) == '\r') {
                    ++count;
                }
                if (count == 2) {
                    content = content.substring(i + 2);
                    try {
                        return LocalDate.parse(content.substring(0, content.indexOf(pivot)));
                    } catch (StringIndexOutOfBoundsException | DateTimeParseException e) {
                        System.out.println(path);
                    }
                }
            }
        } catch (IOException ignored) {}
        return LocalDate.of(2010, 1, 1);
    }

    public static void printExecutionTime(Instant start, String message) {
        if (DEBUG_MODE) {
            System.out.println(message + MILLIS.between(start, Instant.now()));
        }
    }

}
