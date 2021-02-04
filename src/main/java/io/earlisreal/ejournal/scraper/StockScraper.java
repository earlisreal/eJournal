package io.earlisreal.ejournal.scraper;

import io.earlisreal.ejournal.dto.Stock;

import java.util.List;

public interface StockScraper {

    List<Stock> scrape();

}
