package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dto.BankTransaction;

import java.util.List;

public interface BankTransactionService {

    List<BankTransaction> getAll();

    int insert(List<BankTransaction> bankTransactions);

    boolean delete(int id);

    int insertCsv(List<String> csv);

}
