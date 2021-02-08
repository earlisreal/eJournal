package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.database.DerbyDatabase;
import io.earlisreal.ejournal.dto.EmailLastSync;
import io.earlisreal.ejournal.util.CommonUtil;

import java.sql.*;
import java.time.Instant;

public class DerbyEmailLastSyncDAO implements EmailLastSyncDAO {

    Connection connection = DerbyDatabase.getConnection();

    DerbyEmailLastSyncDAO() {}

    @Override
    public EmailLastSync query(String email) {
        String sql = "SELECT * FROM email_sync WHERE email = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, email);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                EmailLastSync emailLastSync = new EmailLastSync();
                emailLastSync.setEmail(resultSet.getString(1));
                emailLastSync.setLastSync(Instant.ofEpochMilli(resultSet.getTimestamp(2).getTime()));
                return  emailLastSync;
            }
        } catch (SQLException sqlException) {
            CommonUtil.handleException(sqlException);
        }

        return null;
    }

    @Override
    public boolean insert(EmailLastSync emailLastSync) {
        String sql = "INSERT INTO email_sync (last_sync, email) VALUES (?, ?)";
        return execute(sql, emailLastSync);
    }

    @Override
    public boolean update(EmailLastSync emailLastSync) {
        String sql = "UPDATE email_sync SET last_sync = ? WHERE email = ?";
        return execute(sql, emailLastSync);
    }

    @Override
    public boolean deleteAll(int secretParam) {
        String sql = "DELETE FROM email_sync WHERE 1 = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, secretParam);
            preparedStatement.execute();
            return preparedStatement.getUpdateCount() > 0;
        } catch (SQLException sqlException) {
            CommonUtil.handleException(sqlException);
        }
        return false;
    }

    private boolean execute(String sql, EmailLastSync emailLastSync) {
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setTimestamp(1, new Timestamp(emailLastSync.getLastSync().toEpochMilli()));
            preparedStatement.setString(2, emailLastSync.getEmail());
            preparedStatement.execute();
            return preparedStatement.getUpdateCount() > 0;
        } catch (SQLException sqlException) {
            CommonUtil.handleException(sqlException);
        }
        return false;
    }

}
