package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dao.BankTransactionDAO;

public class SimpleBankTransactionService implements BankTransactionService {

    private final BankTransactionDAO bankTransactionDAO;

    SimpleBankTransactionService(BankTransactionDAO bankTransactionDAO) {
        this.bankTransactionDAO = bankTransactionDAO;
    }

}
