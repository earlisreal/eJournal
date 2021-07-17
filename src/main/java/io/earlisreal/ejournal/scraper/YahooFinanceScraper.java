package io.earlisreal.ejournal.scraper;

import io.earlisreal.ejournal.service.StockService;
import io.earlisreal.ejournal.util.CommonUtil;
import org.apache.http.client.utils.URIBuilder;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static io.earlisreal.ejournal.util.CommonUtil.handleException;

public class YahooFinanceScraper implements StockPriceScraper {

    public static final String BASE_URL = "https://query1.finance.yahoo.com/v7/finance/download/";

    private final StockService service;

    public YahooFinanceScraper(StockService service) {
        this.service = service;
    }

    @Override
    public List<String> scrapeStockPrice(String stockCode) {
        List<String> csv = new ArrayList<>();
        try {
            var start = LocalDate.of(2000, 1, 1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
            String url = new URIBuilder(BASE_URL + stockCode)
                    .setParameter("period1", String.valueOf(start))
                    .setParameter("period2", String.valueOf(ZonedDateTime.now().toEpochSecond()))
                    .setParameter("interval", "1d")
                    .toString();
            String data = Jsoup.connect(url).execute().body();

            boolean first = true;
            for (String line : data.split("\n")) {
                if (first) {
                    first = false;
                    continue;
                }

                var tokens = line.split(",");
                csv.add(tokens[0] + "," + tokens[1] + ","  + tokens[2] + "," + tokens[3] + "," + tokens[4] + "," + tokens[6]);
            }
        } catch (IOException | URISyntaxException e) {
            handleException(e);
        }

        return csv;
    }

}
