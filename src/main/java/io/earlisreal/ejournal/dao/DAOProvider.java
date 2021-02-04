package io.earlisreal.ejournal.dao;

import static io.earlisreal.ejournal.database.DerbyDatabase.getConnection;

public class DAOProvider {

    private DAOProvider() {}

    private static TradeLogDAO tradeLogDAO;
    private static StrategyDAO strategyDAO;
    private static BankTransactionDAO bankTransactionDAO;
    private static EmailLastSyncDAO emailLastSyncDAO;
    private static StockDAO stockDAO;
    private static PlanDAO planDAO;

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

    public static EmailLastSyncDAO getEmailLastSyncDAO() {
        if (emailLastSyncDAO == null) {
            synchronized (DAOProvider.class) {
                if (emailLastSyncDAO == null) {
                    emailLastSyncDAO = new DerbyEmailLastSyncDAO();
                }
            }
        }

        return emailLastSyncDAO;
    }

    public static StockDAO getStockDAO() {
        if (stockDAO == null) {
            synchronized (DAOProvider.class) {
                if (stockDAO == null) {
                    stockDAO = new DerbyStockDAO(getConnection());
                }
            }
        }

        return stockDAO;
    }

    public static PlanDAO getPlanDAO() {
        if (planDAO == null) {
            synchronized (DAOProvider.class) {
                if (planDAO == null) {
                    planDAO = new DerbyPlanDAO();
                }
            }
        }

        return planDAO;
    }

}
