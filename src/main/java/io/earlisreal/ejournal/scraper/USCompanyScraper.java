package io.earlisreal.ejournal.scraper;

import io.earlisreal.ejournal.dto.Stock;
import io.earlisreal.ejournal.util.CommonUtil;
import io.earlisreal.ejournal.util.Country;
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
            var lines = Jsoup.connect(url).execute().body().split("\r\n");
            boolean first = true;
            for (String line : lines) {
                if (first) {
                    first = false;
                    continue;
                }

                int separator = line.indexOf(",");
                Stock stock = new Stock();
                String code = line.substring(0, separator);
                if (code.contains("$") || code.contains(".")) continue;

                stock.setCode(code);
                String name;
                if (line.charAt(separator + 1) == '"') name = line.substring(separator + 2, line.length() - 1);
                else name = line.substring(separator + 1);
                if (name.length() > 69) {
                    name = name.substring(0, 67) + "..";
                }
                stock.setName(name);
                stock.setCountry(Country.US);
                stocks.add(stock);
            }
        } catch (IOException e) {
            CommonUtil.handleException(e);
        }

        return stocks;
    }

}
