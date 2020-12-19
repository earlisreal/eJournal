package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.database.DerbyDatabase;
import io.earlisreal.ejournal.dto.EmailLastSync;

import java.sql.*;
import java.time.Instant;

public class DerbyEmailLastSyncDAO implements EmailLastSyncDAO {

    Connection connection = DerbyDatabase.getConnection();

    DerbyEmailLastSyncDAO() {}

    @Override
    public EmailLastSync query(String email) {
        try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM email_sync");
             ResultSet resultSet = preparedStatement.executeQuery()) {
            if (resultSet.next()) {
                EmailLastSync emailLastSync = new EmailLastSync();
                emailLastSync.setEmail(resultSet.getString(1));
                emailLastSync.setLastSync(Instant.ofEpochMilli(resultSet.getTimestamp(2).getTime()));
                return  emailLastSync;
            }
        } catch (SQLException sqlException) {
            System.out.println(sqlException.getMessage());
            sqlException.printStackTrace();
        }

        return null;
    }

    @Override
    public boolean insert(EmailLastSync emailLastSync) {
        String sql = "INSERT INTO strategy (name, description) VALUES (?, ?)";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, emailLastSync.getEmail());
            preparedStatement.setTimestamp(2, new Timestamp(emailLastSync.getLastSync().toEpochMilli()));
            preparedStatement.execute();
            return preparedStatement.getUpdateCount() > 0;
        } catch (SQLException sqlException) {
            System.out.println(sqlException.getMessage());
            sqlException.printStackTrace();
        }
        return false;
    }

}
