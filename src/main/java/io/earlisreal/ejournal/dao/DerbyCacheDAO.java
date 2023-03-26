package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.util.CommonUtil;
import org.apache.derby.shared.common.reference.SQLState;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import static io.earlisreal.ejournal.util.CommonUtil.handleException;

public class DerbyCacheDAO implements CacheDAO {

    private final Connection connection;

    DerbyCacheDAO(Connection connection) {
        this.connection = connection;
    }

    @Override
    public Optional<String> get(String key) {
        String sql = "SELECT value FROM cache WHERE \"key\" = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, key);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                return Optional.ofNullable(resultSet.getString(1));
            }
        } catch (SQLException sqlException) {
            CommonUtil.handleException(sqlException);
        }

        return Optional.empty();
    }

    @Override
    public boolean save(String key, String value) {
        if (!insert(key, value)) {
            return update(key, value);
        }
        return true;
    }

    public boolean insert(String key, String value) {
        String sql = "INSERT INTO cache (\"key\", value) VALUES (?, ?)";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, key);
            preparedStatement.setString(2, value);
            preparedStatement.execute();
            return preparedStatement.getUpdateCount() > 0;
        } catch (SQLException sqlException) {
            if (!sqlException.getSQLState().equals(SQLState.LANG_DUPLICATE_KEY_CONSTRAINT)) {
                CommonUtil.handleException(sqlException);
            }
            return false;
        }
    }

    @Override
    public boolean clear(int secretParam) {
        String sql = "DELETE FROM CACHE WHERE 1 = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, secretParam);
            preparedStatement.execute();
            return preparedStatement.getUpdateCount() > 0;
        } catch (SQLException sqlException) {
            handleException(sqlException);
        }

        return false;
    }

    public boolean update(String key, String value) {
        String sql = "UPDATE cache SET value = ? WHERE \"key\" = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(2, key);
            preparedStatement.setString(1, value);
            preparedStatement.execute();
            return preparedStatement.getUpdateCount() > 0;
        } catch (SQLException sqlException) {
            CommonUtil.handleException(sqlException);
        }
        return false;
    }

}
