package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dao.DAOProvider;

public class ServiceProvider {

    private static TradeLogService tradeLogService;
    private static StrategyService strategyService;
    private static BankTransactionService bankTransactionService;
    private static CacheService cacheService;
    private static StockService stockService;
    private static AnalyticsService analyticsService;

    private ServiceProvider() {}

    public static AnalyticsService getAnalyticsService() {
        if (analyticsService == null) {
            synchronized (ServiceProvider.class) {
                if (analyticsService == null) {
                    analyticsService = new SimpleAnalyticsService(getTradeLogService(), getBankTransactionService());
                }
            }
        }

        return analyticsService;
    }

    public static TradeLogService getTradeLogService() {
        if (tradeLogService == null) {
            synchronized (ServiceProvider.class) {
                if (tradeLogService == null) {
                    tradeLogService = new SimpleTradeLogService(DAOProvider.getTradeLogDAO(), DAOProvider.getStrategyDAO());
                }
            }
        }

        return tradeLogService;
    }

    public static StrategyService getStrategyService() {
        if (strategyService == null) {
            synchronized (ServiceProvider.class) {
                if (strategyService == null) {
                    strategyService = new SimpleStrategyService(DAOProvider.getStrategyDAO());
                }
            }
        }

        return strategyService;
    }

    public static BankTransactionService getBankTransactionService() {
        if (bankTransactionService == null) {
            synchronized (ServiceProvider.class) {
                if (bankTransactionService == null) {
                    bankTransactionService = new SimpleBankTransactionService(DAOProvider.getBankTransactionDAO());
                }
            }
        }

        return bankTransactionService;
    }

    public static CacheService getCacheService() {
        if (cacheService == null) {
            synchronized (ServiceProvider.class) {
                if (cacheService == null) {
                    cacheService = new SimpleCacheService(DAOProvider.getEmailLastSyncDAO());
                }
            }
        }

        return cacheService;
    }

    public static StockService getStockService() {
        if (stockService == null) {
            synchronized (ServiceProvider.class) {
                if (stockService == null) {
                    stockService = new SimpleStockService(DAOProvider.getStockDAO());
                }
            }
        }

        return stockService;
    }

}
