package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.database.DerbyDatabase;
import io.earlisreal.ejournal.dto.BankTransaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class DerbyBankTransactionDAO implements BankTransactionDAO {

    Connection connection = DerbyDatabase.getConnection();

    DerbyBankTransactionDAO() {}

    @Override
    public boolean insert(BankTransaction bankTransaction) {
        String sql = "INSERT INTO bank_transaction (date, dividend, amount) VALUES (?, ?, ?)";
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setObject(1, bankTransaction.getDate().toString());
            preparedStatement.setBoolean(2, bankTransaction.isDividend());
            preparedStatement.setDouble(3, bankTransaction.getAmount());
            return true;
        } catch (SQLException sqlException) {
            System.out.println(sqlException.getMessage());
            sqlException.printStackTrace();
            return false;
        }
    }

}
