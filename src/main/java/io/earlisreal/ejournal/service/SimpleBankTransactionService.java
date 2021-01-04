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
        for (BankTransaction bankTransaction : bankTransactions) {
            if (bankTransaction.getReferenceNo() == null) {
                bankTransaction.setReferenceNo((bankTransaction.getAmount() > 0 ? 1 : 0) + bankTransaction.getDate().toString());
            }
        }
        int inserted = bankTransactionDAO.insert(bankTransactions);
        System.out.println(inserted + " Bank Transactions inserted");
    }

    @Override
    public boolean delete(int id) {
        boolean success = bankTransactionDAO.delete(id);
        if (success) {
            System.out.println("Bank Transaction Deleted: " + id);
        }
        return success;
    }

}
