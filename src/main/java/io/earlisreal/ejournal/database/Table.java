package io.earlisreal.ejournal.database;

import java.util.List;

public enum Table {

    STRATEGY("strategy", "CREATE TABLE strategy(" +
            "id INT GENERATED ALWAYS AS IDENTITY CONSTRAINT strategy_pk PRIMARY KEY, " +
            "name VARCHAR(20) NOT NULL," +
            "description LONG VARCHAR)",
            List.of("CREATE UNIQUE INDEX strategy_name_uindex ON strategy (name)")),
    LOG("log", "CREATE TABLE log(" +
            "id INT GENERATED ALWAYS AS IDENTITY CONSTRAINT log_pk PRIMARY KEY, " +
            "date DATE, " +
            "stock VARCHAR(5), " +
            "buy BOOLEAN, " +
            "price DOUBLE, " +
            "shares INT, " +
            "strategy_id INT CONSTRAINT log_strategy_id_fk REFERENCES strategy ON DELETE SET DEFAULT, " +
            "short BOOLEAN DEFAULT FALSE)",
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
