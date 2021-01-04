package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.dto.BankTransaction;

import java.util.List;

public interface BankTransactionDAO {

    List<BankTransaction> queryAll();

    boolean insert(BankTransaction bankTransaction);

    int insert(List<BankTransaction> bankTransactions);

    boolean delete(int id);

}
