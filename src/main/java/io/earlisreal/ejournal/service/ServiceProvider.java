package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.client.AlphaVantageClient;
import io.earlisreal.ejournal.client.JsoupAlphaVantageClient;
import io.earlisreal.ejournal.dao.DAOProvider;
import io.earlisreal.ejournal.scraper.ScraperProvider;

import java.util.ArrayList;
import java.util.List;

public final class ServiceProvider {

    private static TradeLogService tradeLogService;
    private static BankTransactionService bankTransactionService;
    private static CacheService cacheService;
    private static StockService stockService;
    private static AnalyticsService analyticsService;
    private static StartupService startupService;
    private static PlotService plotService;
    private static PlanService planService;
    private static IntradayService intradayService;

    private ServiceProvider() {}

    public static StartupService getStartupService() {
        if (startupService == null) {
            synchronized (ServiceProvider.class) {
                if (startupService == null) {
                    // TODO : Remove this circular dependency of ScraperProvider and ServiceProvider
                    startupService = new SimpleStartupService(ScraperProvider.getStockListScraper(),
                            ScraperProvider.getCompanyScraper(), ScraperProvider.getExchangeRateScraper(),
                            getStockService(), getTradeLogService(), getAnalyticsService(), getCacheService(),
                            ScraperProvider.getUsCompanyScraper(), ServiceProvider.getPlotService());
                }
            }
        }

        return startupService;
    }

    public static PlotService getPlotService() {
        if (plotService == null) {
            synchronized (ServiceProvider.class) {
                if (plotService == null) {
                    plotService = new SimplePlotService(getStockService(), ScraperProvider.getStockPriceScraper(),
                            getIntradayService());
                }
            }
        }

        return plotService;
    }

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
                    tradeLogService = new SimpleTradeLogService(DAOProvider.getTradeLogDAO());
                }
            }
        }

        return tradeLogService;
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
                    cacheService = new SimpleCacheService(DAOProvider.getCacheDAO());
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

    public static PlanService getPlanService() {
        if (planService == null) {
            synchronized (ServiceProvider.class) {
                if (planService == null) {
                    planService = new SimplePlanService(DAOProvider.getPlanDAO());
                }
            }
        }

        return planService;
    }

    public static IntradayService getIntradayService() {
        if (intradayService == null) {
            synchronized (ServiceProvider.class) {
                if (intradayService == null) {
                    String apiKey = System.getenv("AV_API_KEYS");
                    if (apiKey == null) throw new RuntimeException("AV_API_KEYS not found. Please set Alpha Vantage apiKeys to environment variables (comma separated)");
                    List<AlphaVantageClient> clients = new ArrayList<>();
                    for (String key : apiKey.split(",")) {
                        clients.add(new JsoupAlphaVantageClient(key));
                    }
                    intradayService = new AsyncIntradayService(clients, getStockService());
                }
            }
        }

        return intradayService;
    }

}
