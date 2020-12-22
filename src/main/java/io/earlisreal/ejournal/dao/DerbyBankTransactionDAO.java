package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.database.DerbyDatabase;
import io.earlisreal.ejournal.dto.BankTransaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static io.earlisreal.ejournal.dto.BankTransaction.COLUMN_COUNT;

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
                bankTransactions.add(bankTransaction);
            }
        } catch (SQLException sqlException) {
            System.out.println(sqlException.getMessage());
            sqlException.printStackTrace();
        }

        return bankTransactions;
    }

    @Override
    public boolean insert(BankTransaction bankTransaction) {
        return insert(List.of(bankTransaction)) > 0;
    }

    @Override
    public int insert(List<BankTransaction> bankTransactions) {
        String sql = "INSERT INTO bank_transaction (date, dividend, amount, ref)" + generateValues(bankTransactions.size());
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            for (int i = 0; i < bankTransactions.size(); ++i) {
                setParameters(preparedStatement, bankTransactions.get(i), i);
            }
            preparedStatement.execute();
            return preparedStatement.getUpdateCount();
        } catch (SQLException sqlException) {
            System.out.println(sqlException.getMessage());
            return 0;
        }
    }

    private String generateValues(int rows) {
        return " VALUES (?, ?, ?, ?)" + ", (?, ?, ?, ?)".repeat(Math.max(0, rows - 1));
    }

    private void setParameters(PreparedStatement preparedStatement, BankTransaction bankTransaction, int row) throws SQLException {
        preparedStatement.setObject(1 + (row * COLUMN_COUNT), bankTransaction.getDate().toString());
        preparedStatement.setBoolean(2 + (row * COLUMN_COUNT), bankTransaction.isDividend());
        preparedStatement.setDouble(3 + (row * COLUMN_COUNT), bankTransaction.getAmount());
        preparedStatement.setString(4 + (row * COLUMN_COUNT), bankTransaction.getReferenceNo());
    }

}
