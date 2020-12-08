package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dao.BankTransactionDAO;
import io.earlisreal.ejournal.dto.BankTransaction;

import java.util.List;

public class SimpleBankTransactionService implements BankTransactionService {

    private final BankTransactionDAO bankTransactionDAO;

    SimpleBankTransactionService(BankTransactionDAO bankTransactionDAO) {
        this.bankTransactionDAO = bankTransactionDAO;
    }

    @Override
    public List<BankTransaction> getAll() {
        return bankTransactionDAO.queryAll();
    }

    @Override
    public void insert(List<BankTransaction> bankTransactions) {
        int inserted = bankTransactionDAO.insert(bankTransactions);
        System.out.println(inserted + " Transactions inserted");
    }

}
