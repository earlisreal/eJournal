package io.earlisreal.ejournal.scraper;

import com.jsoniter.JsonIterator;
import com.jsoniter.any.Any;
import com.jsoniter.output.JsonStream;
import io.earlisreal.ejournal.service.ServiceProvider;
import io.earlisreal.ejournal.service.StockService;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PSEStockPriceScraper {

    public static final String URL_SOURCE = "https://edge.pse.com.ph/common/DisclosureCht.ax";

    public List<String> scrapeStockPrice(String stockCode) {
        StockService service = ServiceProvider.getStockService();
        List<String> csv = new ArrayList<>();

        try {
            Map<String, String> body = new HashMap<>();
            body.put("cmpy_id", service.getCompanyId(stockCode));
            body.put("security_id", service.getSecurityId(stockCode));
            body.put("startDate", "01-06-2021");
            body.put("endDate", "01-08-2021");

            String json = Jsoup.connect(URL_SOURCE)
                    .header("Content-Type", "application/json")
                    .requestBody(JsonStream.serialize(body))
                    .post()
                    .text();

            var records = JsonIterator.deserialize(json).get("chartData");
            csv.add("OPEN,VALUE,CLOSE,CHART_DATE,HIGH,LOW");
            for (Any record : records) {
                String row = record.get("OPEN") + ","
                        + record.get("VALUE")
                        + "," + record.get("CLOSE")
                        + ",\"" + record.get("CHART_DATE") + "\","
                        +record.get("HIGH") + ","
                        + record.get("LOW");
                csv.add(row);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return csv;
    }

}
