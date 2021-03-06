package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.database.DerbyDatabase;
import io.earlisreal.ejournal.dto.Strategy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DerbyStrategyDAO implements StrategyDAO {

    Connection connection = DerbyDatabase.getConnection();

    DerbyStrategyDAO() {}

    public List<Strategy> queryAll() {
        List<Strategy> strategies = new ArrayList<>();
        try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM strategy");
             ResultSet resultSet = preparedStatement.executeQuery()) {

            while (resultSet.next()) {
                Strategy strategy = new Strategy();
                strategy.setId(resultSet.getInt(1));
                strategy.setName(resultSet.getString(2));
                strategy.setDescription(resultSet.getString(3));
                strategies.add(strategy);
            }
        } catch (SQLException sqlException) {
            System.out.println(sqlException.getMessage());
            sqlException.printStackTrace();
        }

        return strategies;
    }

    @Override
    public boolean insert(Strategy strategy) {
        String sql = "INSERT INTO strategy (name, description) VALUES (?, ?)";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, strategy.getName());
            preparedStatement.setString(2, strategy.getDescription());
            preparedStatement.execute();
            return preparedStatement.getUpdateCount() > 0;
        } catch (SQLException sqlException) {
            System.out.println(sqlException.getMessage());
            sqlException.printStackTrace();
        }
        return false;
    }

}
