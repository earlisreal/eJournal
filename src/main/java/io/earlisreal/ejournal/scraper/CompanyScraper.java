package io.earlisreal.ejournal.scraper;

import io.earlisreal.ejournal.dto.Stock;

import java.util.List;

public interface CompanyScraper {

    List<Stock> scrapeCompanies();

}
