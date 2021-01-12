package io.earlisreal.ejournal.scraper;

import io.earlisreal.ejournal.service.ServiceProvider;

public final class ScraperProvider {

    public static StockPriceScraper getStockPriceScraper() {
        return new PSEStockPriceScraper(ServiceProvider.getStockService());
    }

}
