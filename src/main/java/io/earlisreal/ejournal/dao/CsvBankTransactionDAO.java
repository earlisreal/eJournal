package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.database.FileDatabase;
import io.earlisreal.ejournal.dto.BankTransaction;
import io.earlisreal.ejournal.util.ParseUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static io.earlisreal.ejournal.util.CommonUtil.handleException;

public class CsvBankTransactionDAO implements BankTransactionDAO {

    private static final Path PATH = FileDatabase.getBankTransactionPath();

    @Override
    public List<BankTransaction> queryAll() {
        try {
            return ParseUtil.parseBankTransactions(Files.readAllLines(PATH));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean insert(BankTransaction bankTransaction) {
        try {
            Files.writeString(PATH, bankTransaction.toCsv(), StandardOpenOption.APPEND);
        } catch (IOException e) {
            handleException(e);
            return false;
        }
        return true;
    }

    @Override
    public int insert(List<BankTransaction> bankTransactions) {
        return 0;
    }

    @Override
    public boolean delete(int id) {
        return false;
    }

}
