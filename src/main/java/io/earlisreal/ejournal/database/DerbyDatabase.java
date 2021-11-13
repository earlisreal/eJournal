package io.earlisreal.ejournal.database;

import io.earlisreal.ejournal.util.Configs;
import org.apache.derby.jdbc.EmbeddedDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DerbyDatabase {

    private static DataSource dataSource;
    private static Connection connection;
    private static List<Connection> connections;

    private DerbyDatabase() {}

    public static void initialize() throws SQLException {
        connections = new ArrayList<>();
        initializeDataSource();
        createTables();
    }

    private static void initializeDataSource() {
        EmbeddedDataSource embeddedDataSource = new EmbeddedDataSource();
        embeddedDataSource.setDatabaseName(Configs.DATA_DIR + "/eJournalDB");
        embeddedDataSource.setUser("eJournal");
        embeddedDataSource.setPassword("eJournal");
        embeddedDataSource.setCreateDatabase("create");
        dataSource = embeddedDataSource;
        connection = getConnection();
    }

    private static void createTables() throws SQLException {
        for (Table table : Table.values()) {
            if (!execute(table.getCreateStatement())) {
                continue;
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

    public static Connection getConnection() {
        if (dataSource == null) {
            throw new RuntimeException("Uninitialized Derby Database Connection. " +
                    "Please invoke DerbyDatabase.initialize() first.");
        }

        try {
            Connection newConnection = dataSource.getConnection();
            connections.add(newConnection);
            return newConnection;
        } catch (SQLException sqlException) {
            System.out.println(sqlException.getMessage());
            return connection;
        }
    }

    public static void close() {
        if (connections == null) {
            return;
        }

        for (Connection connection : connections) {
            try {
                connection.commit();
                connection.close();
            }
            catch (SQLException ex) {
                System.out.println(ex.getMessage());
            }
        }
    }

}
