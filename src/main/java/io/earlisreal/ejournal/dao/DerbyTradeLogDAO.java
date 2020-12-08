package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.database.DerbyDatabase;
import io.earlisreal.ejournal.dto.TradeLog;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static io.earlisreal.ejournal.dto.TradeLog.COLUMN_COUNT;

public class DerbyTradeLogDAO implements TradeLogDAO {

    Connection connection = DerbyDatabase.getConnection();

    DerbyTradeLogDAO() {}

    @Override
    public List<TradeLog> queryAll() {
        List<TradeLog> logs = new ArrayList<>();
        try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM log");
             ResultSet resultSet = preparedStatement.executeQuery()) {

            while (resultSet.next()) {
                TradeLog tradeLog = new TradeLog();
                tradeLog.setId(resultSet.getInt(1));
                tradeLog.setDate(LocalDate.parse(resultSet.getString(2)));
                tradeLog.setStock(resultSet.getString(3));
                tradeLog.setBuy(resultSet.getBoolean(4));
                tradeLog.setPrice(resultSet.getDouble(5));
                tradeLog.setShares(resultSet.getInt(6));
                tradeLog.setStrategyId(resultSet.getInt(7));
                tradeLog.setShort(resultSet.getBoolean(8));
                logs.add(tradeLog);
            }
        } catch (SQLException sqlException) {
            sqlException.printStackTrace();
        }

        return logs;
    }

    @Override
    public boolean insertLog(TradeLog tradeLog) {
        String sql = generateInsertStatement(1);
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            setParameters(preparedStatement, tradeLog, 0);
            preparedStatement.execute();
            return preparedStatement.getUpdateCount() > 0;
        } catch (SQLException sqlException) {
            System.out.println(sqlException.getMessage());
            return false;
        }
    }

    public int insertLog(List<TradeLog> tradeLogs) {
        String sql = generateInsertStatement(tradeLogs.size());
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            for (int i = 0; i < tradeLogs.size(); ++i) {
                setParameters(preparedStatement, tradeLogs.get(i), i);
            }
            preparedStatement.execute();
            return preparedStatement.getUpdateCount();
        } catch (SQLException sqlException) {
            System.out.println(sqlException.getMessage());
            return 0;
        }
    }

    private String generateInsertStatement(int rowCount) {
        return "INSERT INTO log (date, stock, buy, price, shares, strategy_id, short) VALUES (?, ?, ?, ?, ?, ?, ?)"
                + ", (?, ?, ?, ?, ?, ?, ?)".repeat(Math.max(0, rowCount - 1));
    }

    private void setParameters(PreparedStatement preparedStatement, TradeLog tradeLog, int rowIndex) throws SQLException {
        preparedStatement.setDate(1 + (rowIndex * COLUMN_COUNT), new Date(tradeLog.getDateInstant().toEpochMilli()));
        preparedStatement.setString(2 + (rowIndex * COLUMN_COUNT), tradeLog.getStock());
        preparedStatement.setBoolean(3 + (rowIndex * COLUMN_COUNT), tradeLog.isBuy());
        preparedStatement.setDouble(4 + (rowIndex * COLUMN_COUNT), tradeLog.getPrice());
        preparedStatement.setInt(5 + (rowIndex * COLUMN_COUNT), tradeLog.getShares());
        preparedStatement.setObject(6 + (rowIndex * COLUMN_COUNT), tradeLog.getStrategyId());
        preparedStatement.setBoolean(7 + (rowIndex * COLUMN_COUNT), tradeLog.isShort());
    }

}
