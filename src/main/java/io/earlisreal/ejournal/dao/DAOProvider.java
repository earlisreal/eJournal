package io.earlisreal.ejournal.dao;

public class DAOProvider {

    private DAOProvider() {}

    private static TradeLogDAO tradeLogDAO;
    private static StrategyDAO strategyDAO;
    private static BankTransactionDAO bankTransactionDAO;

    public static TradeLogDAO getTradeLogDAO() {
        if (tradeLogDAO == null) {
            synchronized (DAOProvider.class) {
                if (tradeLogDAO == null) {
                    tradeLogDAO = new DerbyTradeLogDAO();
                }
            }
        }

        return tradeLogDAO;
    }

    public static StrategyDAO getStrategyDAO() {
        if (strategyDAO == null) {
            synchronized (DAOProvider.class) {
                if (strategyDAO == null) {
                    strategyDAO = new DerbyStrategyDAO();
                }
            }
        }

        return strategyDAO;
    }

    public static BankTransactionDAO getBankTransactionDAO() {
        if (bankTransactionDAO == null) {
            synchronized (DAOProvider.class) {
                if (bankTransactionDAO == null) {
                    bankTransactionDAO = new DerbyBankTransactionDAO();
                }
            }
        }

        return bankTransactionDAO;
    }

}