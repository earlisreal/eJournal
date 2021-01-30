package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dao.BankTransactionDAO;
import io.earlisreal.ejournal.dto.BankTransaction;
import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.util.ParseUtil;

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
    public int insert(List<BankTransaction> bankTransactions) {
        if (bankTransactions.isEmpty()) return 0;

        for (BankTransaction bankTransaction : bankTransactions) {
            if (bankTransaction.getReferenceNo() == null) {
                bankTransaction.setReferenceNo((bankTransaction.getAmount() > 0 ? 1 : 0) + bankTransaction.getDate().toString());
            }
        }
        int inserted = bankTransactionDAO.insert(bankTransactions);
        System.out.println(inserted + " Bank Transactions inserted");
        return inserted;
    }

    @Override
    public boolean delete(int id) {
        boolean success = bankTransactionDAO.delete(id);
        if (success) {
            System.out.println("Bank Transaction Deleted: " + id);
        }
        return success;
    }

    @Override
    public void insertCsv(List<String> csv) {
        List<BankTransaction> bankTransactions = ParseUtil.parseBankTransactions(csv);
        bankTransactionDAO.insert(bankTransactions);
    }

}
