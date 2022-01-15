package io.earlisreal.ejournal.ui.service;

import javafx.scene.web.WebEngine;

public final class UIServiceProvider {

    private static volatile ChartService chartService;

    private static final class TradeDetailsDialogServiceHolder {
        private static final TradeDetailsDialogService INSTANCE = new TradeDetailsDialogService();
    }

    public static TradeDetailsDialogService getTradeDetailsDialogService() {
        return TradeDetailsDialogServiceHolder.INSTANCE;
    }

    public static ChartService getChartService(WebEngine webEngine) {
        if (chartService == null) {
            synchronized (ChartService.class) {
                if (chartService == null) {
                    chartService = new SimpleChartService(webEngine);
                }
            }
        }

        return chartService;
    }

}
