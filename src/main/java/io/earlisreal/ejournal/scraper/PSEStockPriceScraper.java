package io.earlisreal.ejournal.scraper;

import com.jsoniter.JsonIterator;
import com.jsoniter.any.Any;
import com.jsoniter.output.JsonStream;
import io.earlisreal.ejournal.service.StockService;
import io.earlisreal.ejournal.util.CommonUtil;
import io.earlisreal.ejournal.util.Country;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PSEStockPriceScraper implements StockPriceScraper {

    public static final String URL_SOURCE = "https://edge.pse.com.ph/common/DisclosureCht.ax";

    private final StockService service;

    PSEStockPriceScraper(StockService stockService) {
        service = stockService;
    }

    @Override
    public List<String> scrapeStockPrice(String stockCode) {
        return scrapeStockPrice(stockCode, service.getCompanyId(stockCode), service.getSecurityId(stockCode));
    }

    public List<String> scrapeStockPrice(String stock, String id, String security) {
        List<String> csv = new ArrayList<>();
        try {
            Map<String, String> body = new HashMap<>();
            body.put("cmpy_id", id);
            body.put("security_id", security);

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd-uuuu");
            body.put("startDate", CommonUtil.getLastDailyDate(stock, Country.PH).format(formatter));
            body.put("endDate", formatter.format(LocalDate.now()));

            String json = Jsoup.connect(URL_SOURCE)
                    .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType())
                    .requestBody(JsonStream.serialize(body))
                    .post()
                    .text();

            var records = JsonIterator.deserialize(json).get("chartData");
            for (Any record : records) {
                formatter = DateTimeFormatter.ofPattern("MMM dd, uuuu HH:mm:ss");
                LocalDate date = LocalDate.parse(record.get("CHART_DATE").toString(), formatter);
                String row = date.format(DateTimeFormatter.ofPattern("uuuu-MM-dd")) + ","
                        + record.get("OPEN") + ","
                        + record.get("HIGH") + ","
                        + record.get("LOW") + ","
                        + record.get("CLOSE") + ","
                        + record.get("VALUE");
                csv.add(row);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return csv;
    }

}
