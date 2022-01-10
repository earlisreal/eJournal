package io.earlisreal.ejournal.scraper;

import com.jsoniter.JsonIterator;
import com.jsoniter.any.Any;
import io.earlisreal.ejournal.dto.Stock;
import io.earlisreal.ejournal.util.CommonUtil;
import io.earlisreal.ejournal.util.Country;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class USCompanyScraper implements CompanyScraper {

    public static final String NYSE_URL = "https://api.nasdaq.com/api/screener/stocks?tableonly=true&exchange=nyse&download=true";
    public static final String NASDAQ_URL = "https://api.nasdaq.com/api/screener/stocks?tableonly=true&exchange=nasdaq&download=true";
    public static final String AMEX_URL = "https://api.nasdaq.com/api/screener/stocks?tableonly=true&exchange=amex&download=true";

    @Override
    public List<Stock> scrapeCompanies() {
        return Stream.of(NYSE_URL, NASDAQ_URL, AMEX_URL)
                .parallel()
                .map(this::getStocks)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private List<Stock> getStocks(String url) {
        List<Stock> stocks = new ArrayList<>();
        try {
            String json = Jsoup.connect(url)
                    .ignoreContentType(true)
                    .timeout(60_000)
                    .get()
                    .text();
            var records = JsonIterator.deserialize(json).get("data").get("rows");
            for (Any record : records) {
                Stock stock = new Stock();
                stock.setCode(record.toString("symbol"));
                if (stock.getCode().contains("^")) continue;
                String name = record.toString("name");
                if (name.length() > 69) {
                    name = name.substring(0, 67) + "..";
                }
                stock.setName(name);
                stock.setCountry(Country.US);
                double price = Double.parseDouble(record.toString("lastsale").substring(1));
                stock.setPrice(price);
                stocks.add(stock);
            }
        } catch (IOException e) {
            CommonUtil.handleException(e);
        }

        return stocks;
    }

}
