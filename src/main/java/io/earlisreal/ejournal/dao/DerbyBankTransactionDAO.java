package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.database.DerbyDatabase;
import io.earlisreal.ejournal.dto.BankTransaction;
import io.earlisreal.ejournal.util.Broker;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class DerbyBankTransactionDAO implements BankTransactionDAO {

    Connection connection = DerbyDatabase.getConnection();

    DerbyBankTransactionDAO() {}

    @Override
    public List<BankTransaction> queryAll() {
        List<BankTransaction> bankTransactions = new ArrayList<>();
        try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM bank_transaction");
             ResultSet resultSet = preparedStatement.executeQuery()) {

            while (resultSet.next()) {
                BankTransaction bankTransaction = new BankTransaction();
                bankTransaction.setId(resultSet.getInt(1));
                bankTransaction.setDate(LocalDate.parse(resultSet.getString(2)));
                bankTransaction.setDividend(resultSet.getBoolean(3));
                bankTransaction.setAmount(resultSet.getDouble(4));
                bankTransaction.setReferenceNo(resultSet.getString(5));
                bankTransaction.setBroker(Broker.values()[resultSet.getInt(6)]);
                bankTransactions.add(bankTransaction);
            }
        } catch (SQLException sqlException) {
            System.out.println(sqlException.getMessage());
            sqlException.printStackTrace();
        }

        return bankTransactions;
    }

    @Override
    public int insert(List<BankTransaction> bankTransactions) {
        int res = 0;
        for (BankTransaction bankTransaction : bankTransactions) {
            res += insert(bankTransaction) ? 1 : 0;
        }

        return res;
    }

    @Override
    public boolean insert(BankTransaction bankTransaction) {
        String sql = "INSERT INTO bank_transaction (date, dividend, amount, ref, broker) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setObject(1, bankTransaction.getDate().toString());
            preparedStatement.setBoolean(2, bankTransaction.isDividend());
            preparedStatement.setDouble(3, bankTransaction.getAmount());
            preparedStatement.setString(4, bankTransaction.getReferenceNo());
            preparedStatement.setInt(5, bankTransaction.getBroker().ordinal());

            preparedStatement.execute();
            return preparedStatement.getUpdateCount() > 0;
        } catch (SQLException ignore) {
            return false;
        }
    }

    @Override
    public boolean delete(int id) {
        String sql = "DELETE FROM bank_transaction WHERE id = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setInt(1, id);
            preparedStatement.execute();
            return preparedStatement.getUpdateCount() > 0;
        } catch (SQLException ignore) {
        }
        return false;
    }

}
