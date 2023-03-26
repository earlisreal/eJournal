package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.database.FileDatabase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import static io.earlisreal.ejournal.database.DerbyDatabase.getConnection;

public class DAOProvider {

    private DAOProvider() {}

    private static TradeLogDAO tradeLogDAO;
    private static BankTransactionDAO bankTransactionDAO;
    private static StockDAO stockDAO;
    private static PlanDAO planDAO;
    private static CacheDAO cacheDAO;
    private static SummaryDetailDAO summaryDetailDAO;
    private static PortfolioDAO portfolioDAO;

    public static TradeLogDAO getTradeLogDAO() {
        if (tradeLogDAO == null) {
            synchronized (DAOProvider.class) {
                if (tradeLogDAO == null) {
                    try {
                        Path path = Paths.get(FileDatabase.getPath());
                        var reader = Files.newBufferedReader(path);
                        var writer = Files.newBufferedWriter(path, StandardOpenOption.APPEND);
                        tradeLogDAO = new CsvTradeLogDAO(reader, writer);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
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
