package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.dto.Stock;
import io.earlisreal.ejournal.util.CommonUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DerbyStockDAO implements StockDAO {

    private final Connection connection;

    DerbyStockDAO(Connection connection) {
        this.connection = connection;
    }

    @Override
    public void updateStocks(List<Stock> stocks) {
        for (Stock stock : stocks) {
            if (isExisting(stock.getCode())) {
                updateStockPrice(stock);
            } else {
                insertStock(stock);
            }
        }
    }

    @Override
    public void updateStockId(List<Stock> stocks) {
        for (Stock stock : stocks) {
            String sql = "UPDATE stock SET company_id = ?, security_id = ? WHERE code = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                preparedStatement.setString(1, stock.getCompanyId());
                preparedStatement.setString(2, stock.getSecurityId());
                preparedStatement.setString(3, stock.getCode());
                preparedStatement.execute();
            } catch (SQLException sqlException) {
                CommonUtil.handleException(sqlException);
            }
        }
    }

    @Override
    public Map<String, Stock> getStockMap() {
        Map<String, Stock> stockMap = new HashMap<>();
        try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM stock");
             ResultSet resultSet = preparedStatement.executeQuery()) {

            while (resultSet.next()) {
                Stock stock = new Stock();
                stock.setCode(resultSet.getString(1));
                stock.setName(resultSet.getString(2));
                stock.setCompanyId(resultSet.getString(3));
                stock.setSecurityId(resultSet.getString(4));
                stock.setPrice(resultSet.getDouble(5));
                String lastDate = resultSet.getString(6);
                if (lastDate != null) stock.setLastDate(LocalDate.parse(lastDate));
                stockMap.put(stock.getCode(), stock);
            }
        } catch (SQLException sqlException) {
            CommonUtil.handleException(sqlException);
        }

        return stockMap;
    }

    @Override
    public boolean updateLastDate(String stock, LocalDate localDate) {
        String sql = "UPDATE stock SET last_date = ? WHERE code = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setDate(1, CommonUtil.toSqlDate(localDate));
            preparedStatement.setString(2, stock);
            preparedStatement.execute();
            return preparedStatement.getUpdateCount() > 0;
        } catch (SQLException sqlException) {
            CommonUtil.handleException(sqlException);
        }
        return false;
    }

    private void insertStock(Stock stock) {
        String sql = "INSERT INTO stock (code, name, price) VALUES (?, ?, ?)";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, stock.getCode());
            preparedStatement.setString(2, stock.getName());
            preparedStatement.setDouble(3, stock.getPrice());
            preparedStatement.execute();
        } catch (SQLException sqlException) {
            CommonUtil.handleException(sqlException);
        }
    }

    private void updateStockPrice(Stock stock) {
        String sql = "UPDATE stock SET price = ? WHERE code = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setDouble(1, stock.getPrice());
            preparedStatement.setString(2, stock.getCode());
            preparedStatement.execute();
        } catch (SQLException sqlException) {
            CommonUtil.handleException(sqlException);
        }
    }

    private boolean isExisting(String stock) {
        String sql = "SELECT 1 FROM stock WHERE code = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, stock);
            ResultSet resultSet = preparedStatement.executeQuery();
            return resultSet.next();
        } catch (SQLException sqlException) {
            CommonUtil.handleException(sqlException);
        }
        return false;
    }

}
