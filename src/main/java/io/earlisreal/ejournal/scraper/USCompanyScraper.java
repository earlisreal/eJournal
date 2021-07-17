package io.earlisreal.ejournal.scraper;

import io.earlisreal.ejournal.dto.Stock;
import io.earlisreal.ejournal.util.CommonUtil;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class USCompanyScraper implements CompanyScraper {

    public static final String NYSE_URL = "https://datahub.io/core/nyse-other-listings/r/nyse-listed.csv";
    public static final String NASDAQ_URL = "https://datahub.io/core/nasdaq-listings/r/nasdaq-listed-symbols.csv";

    @Override
    public List<Stock> scrapeCompanies() {
        List<Stock> stocks = new ArrayList<>();
        stocks.addAll(getStocks(NYSE_URL));
        stocks.addAll(getStocks(NASDAQ_URL));
        return stocks;
    }

    private List<Stock> getStocks(String url) {
        List<Stock> stocks = new ArrayList<>();
        try {
            var lines = Jsoup.connect(NYSE_URL).execute().body().split(System.lineSeparator());
            boolean first = true;
            for (String line : lines) {
                if (first) {
                    first = false;
                    continue;
                }

                var tokens = line.split(",");
                Stock stock = new Stock();
                stock.setCode(tokens[0]);
                stock.setName(tokens[1]);
                stocks.add(stock);
            }
        } catch (IOException e) {
            CommonUtil.handleException(e);
        }

        return stocks;
    }

}
