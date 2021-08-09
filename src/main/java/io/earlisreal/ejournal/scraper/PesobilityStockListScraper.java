package io.earlisreal.ejournal.scraper;

import io.earlisreal.ejournal.dto.Stock;
import io.earlisreal.ejournal.util.CommonUtil;
import io.earlisreal.ejournal.util.Country;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static io.earlisreal.ejournal.util.CommonUtil.handleException;
import static io.earlisreal.ejournal.util.CommonUtil.trimStockName;

public class PesobilityStockListScraper implements StockScraper {

    public static final String STOCK_SOURCE_URL = "https://www.pesobility.com/stock";

    @Override
    public List<Stock> scrape() {
        List<Stock> stocks = new ArrayList<>();
        try {
            Document document = Jsoup.connect(STOCK_SOURCE_URL).get();
            Elements rows = document.select("#MAIN_BODY > div > div > table > tbody > tr");
            rows.forEach(element -> {
                var columns = element.getElementsByTag("td").eachText();

                Stock stock = new Stock();
                stock.setCode(columns.get(0));
                stock.setName(trimStockName(columns.get(1)));
                stock.setCountry(Country.PH);
                if (columns.size() > 2) {
                    int spaceIndex = columns.get(2).indexOf(" ");
                    if (spaceIndex == -1) return;
                    stock.setPrice(Double.parseDouble(columns.get(2).substring(0, spaceIndex)));
                }
                stocks.add(stock);
            });
        } catch (Exception e) {
            handleException(e);
        }

        return stocks;
    }

}
