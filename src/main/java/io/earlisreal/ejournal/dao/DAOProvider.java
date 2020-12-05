package io.earlisreal.ejournal.dao;

public class DAOProvider {

    private static TradeLogDAO tradeLogDAO;
    private static StrategyDAO strategyDAO;

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

}
