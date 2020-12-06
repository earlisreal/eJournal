package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.dto.BankTransaction;

public interface BankTransactionDAO {

    boolean insert(BankTransaction bankTransaction);

}
