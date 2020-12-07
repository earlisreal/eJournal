package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.dto.BankTransaction;

import java.util.List;

public interface BankTransactionDAO {

    boolean insert(BankTransaction bankTransaction);

    int insert(List<BankTransaction> bankTransactions);

}
