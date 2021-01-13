package io.earlisreal.ejournal.scraper;

import java.util.Map;

public interface StockListScraper {

    void parse();

    Map<String, String> getStockMap();

    Map<String, Double> getPriceMap();

}
