package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dto.BankTransaction;

import java.util.List;

public interface BankTransactionService {

    List<BankTransaction> getAll();

    void insert(List<BankTransaction> bankTransactions);

}
