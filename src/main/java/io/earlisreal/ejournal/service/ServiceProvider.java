package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dao.DAOProvider;

public class ServiceProvider {

    private static TradeLogService tradeLogService;
    private static StrategyService strategyService;
    private static BankTransactionService bankTransactionService;

    private ServiceProvider() {}

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

}
