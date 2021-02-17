package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.util.CommonUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static io.earlisreal.ejournal.util.CommonUtil.handleException;

public class DerbyCacheDAO implements CacheDAO {

    private final Connection connection;

    DerbyCacheDAO(Connection connection) {
        this.connection = connection;
    }

    @Override
    public String get(String key) {
        String sql = "SELECT value FROM cache WHERE \"key\" = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, key);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                return resultSet.getString(1);
            }
        } catch (SQLException sqlException) {
            CommonUtil.handleException(sqlException);
        }

        return null;
    }

    @Override
    public boolean insert(String key, String value) {
        String sql = "INSERT INTO cache (\"key\", value) VALUES (?, ?)";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, key);
            preparedStatement.setString(2, value);
            preparedStatement.execute();
            return preparedStatement.getUpdateCount() > 0;
        } catch (SQLException sqlException) {
            CommonUtil.handleException(sqlException);
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

    @Override
    public boolean update(String key, String value) {
        String sql = "UPDATE cache SET value = ? WHERE \"key\" = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, key);
            preparedStatement.setString(2, value);
            preparedStatement.execute();
            return preparedStatement.getUpdateCount() > 0;
        } catch (SQLException sqlException) {
            CommonUtil.handleException(sqlException);
        }
        return false;
    }

}
