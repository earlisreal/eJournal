package io.earlisreal.ejournal.client;

public abstract class TradeZeroClientFactory {

    private TradeZeroClientFactory() {}

    public static TradeZeroClient getClient(String username, String password) {
        if (username.endsWith("DEMO")) {
            return new DemoTradeZeroClient(username, password);
        }
        return new LiveTradeZeroClient(username, password);
    }

}
