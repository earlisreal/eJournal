package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dao.DAOProvider;
import io.earlisreal.ejournal.dao.DerbyTradeLogDAO;

public class ServiceProvider {

    private static TradeLogService tradeLogService;

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

}