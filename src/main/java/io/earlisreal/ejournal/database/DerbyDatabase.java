package io.earlisreal.ejournal.database;

import org.apache.derby.shared.common.reference.SQLState;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class DerbyDatabase {

    private static Connection connection;

    public static Connection getConnection() {
        return connection;
    }

    public boolean initialize() {
        try {
            connection = DriverManager.getConnection("jdbc:derby:eJournalDB;create=true", "eJournal", "eJournal");
            for (Table table : Table.values()) {
                if (!createTable(table.getCreateStatement())) {
                    break;
                }
                System.out.println(table.getName() + "Table Created");
            }
        } catch (SQLException sqlException) {
            System.out.println(sqlException.getMessage());
            sqlException.printStackTrace();
            return false;
        }
        return true;
    }

    private boolean createTable(String sql) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.execute();
            return true;
        } catch (SQLException sqlException) {
            if (sqlException.getSQLState().equals(SQLState.LANG_OBJECT_ALREADY_EXISTS_IN_OBJECT)) {
                throw sqlException;
            }
            return false;
        }
    }

}
