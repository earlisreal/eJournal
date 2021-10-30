package io.earlisreal.ejournal.database;

import java.util.List;

public enum Table {

    TRADE_SUMMARY ("summary_detail", "CREATE TABLE summary_detail (" +
            "id VARCHAR(16) CONSTRAINT summary_detail_pk PRIMARY KEY, " +
            "rating SMALLINT DEFAULT 0, " +
            "remarks LONG VARCHAR, " +
            "strategy_id INT)",
            null),
    CACHE ("cache", "CREATE TABLE cache (" +
            "\"key\" VARCHAR(35) CONSTRAINT cache_pk PRIMARY KEY, " +
            "value VARCHAR(200))",
            null),
    STOCK ("stock", "CREATE TABLE stock (" +
            "code VARCHAR(5), " +
            "name VARCHAR(69), " +
            "company_id VARCHAR(4), " +
            "security_id VARCHAR(4), " +
            "price DOUBLE, " +
            "last_date DATE, " +
            "country VARCHAR(2), " +
            "CONSTRAINT stock_pk PRIMARY KEY (code, country))",
            null),
    BANK_TRANSACTION ("bank_transaction", "CREATE TABLE bank_transaction (" +
            "id INT GENERATED ALWAYS AS IDENTITY CONSTRAINT bank_transaction_pk PRIMARY KEY, " +
            "date DATE," +
            "dividend BOOLEAN, " +
            "amount DOUBLE, " +
            "ref VARCHAR(15), " +
            "broker SMALLINT)",
            List.of("CREATE UNIQUE INDEX bank_transaction_ref_uindex ON bank_transaction (ref)")),
    LOG ("log", "CREATE TABLE log (" +
            "id INT GENERATED ALWAYS AS IDENTITY CONSTRAINT log_pk PRIMARY KEY, " +
            "datetime TIMESTAMP, " +
            "stock VARCHAR(5), " +
            "buy BOOLEAN, " +
            "price DOUBLE, " +
            "shares DOUBLE, " +
            "fees DOUBLE, " +
            "short BOOLEAN DEFAULT FALSE, " +
            "invoice VARCHAR(16), " +
            "broker SMALLINT)",
            List.of("CREATE UNIQUE INDEX log_invoice_uindex ON log (invoice)")),
    PLAN ("plan", "CREATE TABLE plan (" +
            "id INT GENERATED ALWAYS AS IDENTITY CONSTRAINT plan_pk PRIMARY KEY, " +
            "date DATE, " +
            "stock VARCHAR(5), " +
            "entry DOUBLE, " +
            "stop DOUBLE, " +
            "risk DOUBLE, " +
            "broker SMALLINT)",
            null);

    private final String name;
    private final String createStatement;
    private final List<String> indexes;

    Table(String name, String createStatement, List<String> indexes) {
        this.name = name;
        this.createStatement = createStatement;
        this.indexes = indexes;
    }

    public String getName() {
        return name;
    }

    public String getCreateStatement() {
        return createStatement;
    }

    public List<String> getIndexes() {
        return indexes;
    }

}
