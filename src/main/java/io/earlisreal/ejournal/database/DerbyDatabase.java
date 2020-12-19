package io.earlisreal.ejournal.database;

import io.earlisreal.ejournal.util.Configs;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentMap;

public class DerbyDatabase {

    private static Connection connection;

    private DerbyDatabase() {}

    public static Connection getConnection() {
        if (connection == null) {
            throw new RuntimeException("Uninitialized Derby Database Connection. " +
                    "Please invoke DerbyDatabase.initialize() first.");
        }
        return connection;
    }

    public static Connection initialize(ConcurrentMap<String, Boolean> settings) throws SQLException {
        createConnection();
        String key = "firstRun";
        if (settings.getOrDefault(key, true)) {
            createTables();
            settings.put(key, false);
        }
        return connection;
    }

    public static Connection initialize() throws SQLException {
        createConnection();
        createTables();
        return connection;
    }

    private static void createConnection() throws SQLException {
        connection = DriverManager.getConnection("jdbc:derby:" + Configs.DATA_DIR + "/eJournalDB;create=true", "eJournal", "eJournal");
    }

    private static void createTables() throws SQLException {
        for (Table table : Table.values()) {
            if (!execute(table.getCreateStatement())) {
                break;
            }
            System.out.println(table.getName() + " Table Created");

            if (table.getIndexes() != null) {
                for (String index : table.getIndexes()) {
                    execute(index);
                }
                System.out.println("Indexes Created");
            }
        }
    }

    private static boolean execute(String sql) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.execute();
            return true;
        } catch (SQLException sqlException) {
            if (!sqlException.getSQLState().equals("X0Y32")) {
                throw sqlException;
            }
            return false;
        }
    }

}
