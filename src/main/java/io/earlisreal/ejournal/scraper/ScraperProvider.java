package io.earlisreal.ejournal.scraper;

import io.earlisreal.ejournal.service.ServiceProvider;

public final class ScraperProvider {

    public static StockPriceScraper getStockPriceScraper() {
        return new PSEStockPriceScraper(ServiceProvider.getStockService());
    }

    public static StockScraper getStockListScraper() {
        return new PesobilityStockListScraper();
    }

    public static CompanyScraper getCompanyScraper() {
        return new PSECompanyScraper();
    }

}
