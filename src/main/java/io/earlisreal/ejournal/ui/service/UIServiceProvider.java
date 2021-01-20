package io.earlisreal.ejournal.ui.service;

public final class UIServiceProvider {

    private static TradeDetailsDialogService tradeDetailsDialogService;

    public static TradeDetailsDialogService getTradeDetailsDialogService() {
        if (tradeDetailsDialogService == null) {
            synchronized (TradeDetailsDialogService.class) {
                if (tradeDetailsDialogService == null) {
                    tradeDetailsDialogService = new TradeDetailsDialogService();
                }
            }
        }

        return tradeDetailsDialogService;
    }

}
