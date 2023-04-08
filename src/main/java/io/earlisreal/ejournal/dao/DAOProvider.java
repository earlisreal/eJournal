package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.database.FileDatabase;

public final class DAOProvider {

    private DAOProvider() {}

    private static PortfolioDAO portfolioDAO;

    private static final class TradeLogDAOHolder {
        private static final TradeLogDAO tradeLogDAO = new CsvTradeLogDAO(FileDatabase.getInstance());
    }

    public static TradeLogDAO getTradeLogDAO() {
        return TradeLogDAOHolder.tradeLogDAO;
    }

    private static final class BankTransactionDAOHolder {
        private static final BankTransactionDAO bankTransactionDAO = new CsvBankTransactionDAO();
    }

    public static BankTransactionDAO getBankTransactionDAO() {
        return BankTransactionDAOHolder.bankTransactionDAO;
    }

    private static final class StockDAOHolder {
        private static final StockDAO stockDAO = new CsvStockDAO();
    }

    public static StockDAO getStockDAO() {
        return StockDAOHolder.stockDAO;
    }

    private static final class PlanDAOHolder {
        private static final PlanDAO planDAO = new CsvPlanDAO();
    }

    public static PlanDAO getPlanDAO() {
        return PlanDAOHolder.planDAO;
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
                    return null;
                }
            }
        }

        return portfolioDAO;
    }

}
