package io.earlisreal.ejournal.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

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

    public static Connection initialize() throws SQLException {
        connection = DriverManager.getConnection("jdbc:derby:eJournalDB;create=true", "eJournal", "eJournal");
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
        return connection;
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
