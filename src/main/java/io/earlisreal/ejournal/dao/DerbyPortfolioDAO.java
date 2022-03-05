package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.dto.Portfolio;
import io.earlisreal.ejournal.util.CommonUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DerbyPortfolioDAO implements PortfolioDAO {

    private final Connection connection;

    DerbyPortfolioDAO(Connection connection) {
        this.connection = connection;
    }

    @Override
    public List<Portfolio> getAll() {
        List<Portfolio> portfolios = new ArrayList<>();

        try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM portfolio");
             ResultSet resultSet = preparedStatement.executeQuery()) {

            while (resultSet.next()) {
                int id = resultSet.getInt(1);
                String name = resultSet.getString(2);
                portfolios.add(new Portfolio(id, name));
            }
        } catch (SQLException sqlException) {
            CommonUtil.handleException(sqlException);
        }

        return portfolios;
    }

    @Override
    public boolean save(Portfolio portfolio) {
        if (portfolio.getId() == null) {
            return insert(portfolio);
        }
        return update(portfolio);
    }

    private boolean insert(Portfolio portfolio) {
        String sql = "INSERT INTO portfolio (name) VALUES (?)";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, portfolio.getName());
            preparedStatement.execute();
            return preparedStatement.getUpdateCount() > 0;
        } catch (SQLException sqlException) {
            return false;
        }
    }

    private boolean update(Portfolio portfolio) {
        String sql = "UPDATE portfolio SET name = ? WHERE id = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(2, portfolio.getId());
            preparedStatement.setString(1, portfolio.getName());
            preparedStatement.execute();
            return preparedStatement.getUpdateCount() > 0;
        } catch (SQLException sqlException) {
            CommonUtil.handleException(sqlException);
        }
        return false;
    }

}
