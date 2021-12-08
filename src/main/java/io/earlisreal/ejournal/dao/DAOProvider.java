package io.earlisreal.ejournal.dao;

import static io.earlisreal.ejournal.database.DerbyDatabase.getConnection;

public class DAOProvider {

    private DAOProvider() {}

    private static TradeLogDAO tradeLogDAO;
    private static BankTransactionDAO bankTransactionDAO;
    private static StockDAO stockDAO;
    private static PlanDAO planDAO;
    private static CacheDAO cacheDAO;
    private static SummaryDetailDAO summaryDetailDAO;
    private static AlphaSummaryDAO alphaSummaryDAO;

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

    public static CacheDAO getCacheDAO() {
        if (cacheDAO == null) {
            synchronized (DAOProvider.class) {
                if (cacheDAO == null) {
                    cacheDAO = new DerbyCacheDAO(getConnection());
                }
            }
        }

        return cacheDAO;
    }

    public static SummaryDetailDAO getSummaryDetailDAO() {
        if (summaryDetailDAO == null) {
            synchronized (DAOProvider.class) {
                if (summaryDetailDAO == null) {
                    summaryDetailDAO = new DerbySummaryDetailDAO(getConnection());
                }
            }
        }

        return summaryDetailDAO;
    }

    public static AlphaSummaryDAO getAlphaSummaryDAO() {
        if (alphaSummaryDAO == null) {
            synchronized (DAOProvider.class) {
                if (alphaSummaryDAO == null) {
                    alphaSummaryDAO = new SQLAlphaSummaryDAO(getConnection());
                }
            }
        }

        return alphaSummaryDAO;
    }

}
