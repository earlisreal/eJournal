package io.earlisreal.ejournal.ui.service;

import io.earlisreal.ejournal.service.ServiceProvider;

import java.io.IOException;

public final class UIServiceProvider {

    private static TradeDetailsDialogService tradeDetailsDialogService;

    public static TradeDetailsDialogService getTradeDetailsDialogService() throws IOException {
        if (tradeDetailsDialogService == null) {
            synchronized (TradeDetailsDialogService.class) {
                if (tradeDetailsDialogService == null) {
                    tradeDetailsDialogService = new TradeDetailsDialogService(ServiceProvider.getPlotService());
                }
            }
        }

        return tradeDetailsDialogService;
    }

}
