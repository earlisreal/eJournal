package io.earlisreal.ejournal.database;

import java.util.List;

public enum Table {

    BANK_TRANSACTION ("bank_transaction", "CREATE TABLE bank_transaction (" +
            "id INT GENERATED ALWAYS AS IDENTITY CONSTRAINT bank_transaction_pk PRIMARY KEY, " +
            "date DATE," +
            "dividend BOOLEAN, " +
            "amount DOUBLE, " +
            "ref VARCHAR(15))",
            List.of("CREATE UNIQUE INDEX bank_transaction_ref_uindex ON bank_transaction (ref)")),
    STRATEGY("strategy", "CREATE TABLE strategy (" +
            "id INT GENERATED ALWAYS AS IDENTITY CONSTRAINT strategy_pk PRIMARY KEY, " +
            "name VARCHAR(20), " +
            "description LONG VARCHAR)",
            List.of("CREATE UNIQUE INDEX strategy_name_uindex ON strategy (name)")),
    LOG("log", "CREATE TABLE log (" +
            "id INT GENERATED ALWAYS AS IDENTITY CONSTRAINT log_pk PRIMARY KEY, " +
            "date DATE, " +
            "stock VARCHAR(5), " +
            "buy BOOLEAN, " +
            "price DOUBLE, " +
            "shares INT, " +
            "strategy_id INT CONSTRAINT log_strategy_id_fk REFERENCES strategy ON DELETE SET null, " +
            "short BOOLEAN DEFAULT FALSE, " +
            "invoice VARCHAR(8), " +
            "broker SMALLINT)",
            List.of("CREATE UNIQUE INDEX log_invoice_uindex ON log (invoice)")),
    SETTINGS("settings", "CREATE TABLE email_sync (" +
            "email VARCHAR(30) NOT NULL CONSTRAINT EMAIL_SYNC_PK PRIMARY KEY, " +
            "last_sync TIMESTAMP)",
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
