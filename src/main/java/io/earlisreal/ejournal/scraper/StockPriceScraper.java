package io.earlisreal.ejournal.scraper;

import java.util.List;

public interface StockPriceScraper {

    List<String> scrapeStockPrice(String stockCode);

}
