package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.database.DerbyDatabase;
import io.earlisreal.ejournal.dto.BankTransaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import static io.earlisreal.ejournal.dto.BankTransaction.COLUMN_COUNT;

public class DerbyBankTransactionDAO implements BankTransactionDAO {

    Connection connection = DerbyDatabase.getConnection();

    DerbyBankTransactionDAO() {}

    @Override
    public boolean insert(BankTransaction bankTransaction) {
        String sql = "INSERT INTO bank_transaction (date, dividend, amount)" + generateValues(1);
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            setParameters(preparedStatement, bankTransaction, 0);
            return true;
        } catch (SQLException sqlException) {
            System.out.println(sqlException.getMessage());
            sqlException.printStackTrace();
            return false;
        }
    }

    @Override
    public int insert(List<BankTransaction> bankTransactions) {
        String sql = "INSERT INTO bank_transaction (date, dividend, amount)" + generateValues(bankTransactions.size());
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            for (int i = 0; i < bankTransactions.size(); ++i) {
                setParameters(preparedStatement, bankTransactions.get(i), i);
            }
            preparedStatement.execute();
            return preparedStatement.getUpdateCount();
        } catch (SQLException sqlException) {
            System.out.println(sqlException.getMessage());
            sqlException.printStackTrace();
            return 0;
        }
    }

    private String generateValues(int rows) {
        return "VALUES (?, ?, ?)" + ", (?, ?, ?)".repeat(Math.max(0, rows - 1));
    }

    private void setParameters(PreparedStatement preparedStatement, BankTransaction bankTransaction, int row) throws SQLException {
        preparedStatement.setObject(1 + (row * COLUMN_COUNT), bankTransaction.getDate().toString());
        preparedStatement.setBoolean(2 + (row * COLUMN_COUNT), bankTransaction.isDividend());
        preparedStatement.setDouble(3 + (row * COLUMN_COUNT), bankTransaction.getAmount());
    }

}
