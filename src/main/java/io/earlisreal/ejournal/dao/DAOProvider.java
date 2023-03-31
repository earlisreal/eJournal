package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.database.FileDatabase;

import static io.earlisreal.ejournal.database.DerbyDatabase.getConnection;

public final class DAOProvider {

    private DAOProvider() {}

    private static BankTransactionDAO bankTransactionDAO;
    private static StockDAO stockDAO;
    private static PlanDAO planDAO;
    private static PortfolioDAO portfolioDAO;

    private static final class TradeLogDAOHolder {
        private static final TradeLogDAO tradeLogDAO = new CsvTradeLogDAO(FileDatabase.getInstance());
    }

    public static TradeLogDAO getTradeLogDAO() {
        return TradeLogDAOHolder.tradeLogDAO;
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

    private static final class CacheDAOHolder {
        private static final CacheDAO cacheDAO = new CsvCacheDAO(FileDatabase.getInstance());
    }

    public static CacheDAO getCacheDAO() {
        return CacheDAOHolder.cacheDAO;
    }

    private static final class SummaryDetailDAOHolder {
        private static final SummaryDetailDAO summaryDetailDAO = new CsvSummaryDetailDAO();
    }

    public static SummaryDetailDAO getSummaryDetailDAO() {
        return SummaryDetailDAOHolder.summaryDetailDAO;
    }

    public static PortfolioDAO getPortfolioDAO() {
        if (portfolioDAO == null) {
            synchronized (DAOProvider.class) {
                if (portfolioDAO == null) {
                    portfolioDAO = new DerbyPortfolioDAO(getConnection());
                }
            }
        }

        return portfolioDAO;
    }

}
