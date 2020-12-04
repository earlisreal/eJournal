package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.database.DerbyDatabase;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;

public class DerbyTradeLogDAO implements TradeLogDAO {

    Connection connection = DerbyDatabase.getConnection();

    @Override
    public boolean insertLog(Instant date, String stock, boolean isBuy, double price, int shares, String strategy, boolean isShort) {
        String sql = "INSERT INTO log (DATE, STOCK, BUY, PRICE, SHARES, STRATEGY, SHORT) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setDate(1, new Date(date.toEpochMilli()));
            preparedStatement.setString(2, stock);
            preparedStatement.setBoolean(3, isBuy);
            preparedStatement.setDouble(4, price);
            preparedStatement.setInt(5, shares);
            preparedStatement.setString(6, strategy);
            preparedStatement.setBoolean(7, isShort);
            return preparedStatement.execute();
        } catch (SQLException sqlException) {
            System.out.println(sqlException.getMessage());
            return false;
        }
    }

}
