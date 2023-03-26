package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.database.DerbyDatabase;
import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.util.Broker;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static io.earlisreal.ejournal.util.CommonUtil.handleException;
import static io.earlisreal.ejournal.util.CommonUtil.toSqlDate;
import static io.earlisreal.ejournal.util.CommonUtil.toTimestamp;

public class DerbyTradeLogDAO implements TradeLogDAO {

    Connection connection = DerbyDatabase.getConnection();

    DerbyTradeLogDAO() {}

    @Override
    public List<TradeLog> queryAll() {
        String sql = "SELECT * FROM log";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql);
             ResultSet resultSet = preparedStatement.executeQuery()) {

            return mapToTradeLogs(resultSet);
        } catch (SQLException ignore) {
        }

        return new ArrayList<>();
    }

    private List<TradeLog> mapToTradeLogs(ResultSet resultSet) throws SQLException {
        List<TradeLog> logs = new ArrayList<>();
        while (resultSet.next()) {
            logs.add(map(resultSet));
        }

        return logs;
    }

    @Override
    public List<TradeLog> insertLog(List<TradeLog> tradeLogs) {
        List<TradeLog> res = new ArrayList<>();
        for (TradeLog log : tradeLogs) {
            if (insertLog(log)) {
                res.add(log);
            }
        }

        return res;
    }

    @Override
    public boolean delete(int id) {
        String sql = "DELETE FROM log WHERE id = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, id);
            preparedStatement.execute();
            return preparedStatement.getUpdateCount() > 0;
        } catch (SQLException sqlException) {
            handleException(sqlException);
        }

        return false;
    }

    @Override
    public boolean update(TradeLog tradeLog) {
        String sql = "UPDATE log SET price = ?, shares = ? WHERE invoice = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setDouble(1, tradeLog.getPrice());
            preparedStatement.setDouble(2, tradeLog.getShares());
            preparedStatement.setString(3, tradeLog.getInvoiceNo());
            preparedStatement.execute();
            return preparedStatement.getUpdateCount() > 0;
        } catch (SQLException ignore) {
            return false;
        }
    }

    @Override
    public boolean insertLog(TradeLog tradeLog) {
        String sql = "INSERT INTO log (datetime, stock, buy, price, shares, fees, short, invoice, broker) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setTimestamp(1, toTimestamp(tradeLog.getDate()));
            preparedStatement.setString(2, tradeLog.getStock());
            preparedStatement.setBoolean(3, tradeLog.isBuy());
            preparedStatement.setDouble(4, tradeLog.getPrice());
            preparedStatement.setDouble(5, tradeLog.getShares());
            preparedStatement.setDouble(6, tradeLog.getFees());
            preparedStatement.setBoolean(7, tradeLog.isShort());
            preparedStatement.setString(8, tradeLog.getInvoiceNo());
            preparedStatement.setInt(9, tradeLog.getBroker().ordinal());

            preparedStatement.execute();
            return preparedStatement.getUpdateCount() > 0;
        } catch (SQLException ignore) {
            return false;
        }
    }

    private Optional<TradeLog> query(String reference) {
        String sql = "SELECT * FROM log WHERE invoice = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, reference);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                return Optional.of(map(resultSet));
            }
        } catch (SQLException e) {
            handleException(e);
        }

        return Optional.empty();
    }

    private TradeLog map(ResultSet resultSet) throws SQLException {
        TradeLog tradeLog = new TradeLog();
        tradeLog.setId(resultSet.getInt(1));
        tradeLog.setDate(LocalDateTime.parse(resultSet.getString(2).replace(' ', 'T')));
        tradeLog.setStock(resultSet.getString(3));
        tradeLog.setBuy(resultSet.getBoolean(4));
        tradeLog.setPrice(resultSet.getDouble(5));
        tradeLog.setShares(resultSet.getDouble(6));
        tradeLog.setFees(resultSet.getDouble(7));
        tradeLog.setShort(resultSet.getBoolean(8));
        tradeLog.setInvoiceNo(resultSet.getString(9));
        tradeLog.setBroker(Broker.values()[resultSet.getInt(10)]);
        return tradeLog;
    }

}
