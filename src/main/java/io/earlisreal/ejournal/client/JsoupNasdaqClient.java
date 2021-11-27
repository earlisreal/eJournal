package io.earlisreal.ejournal.client;

import com.jsoniter.JsonIterator;
import com.jsoniter.any.Any;
import org.apache.http.client.utils.URIBuilder;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.earlisreal.ejournal.util.CommonUtil.handleException;

public class JsoupNasdaqClient implements NasdaqClient {

    @Override
    public List<String> getDailyHistory(String stock, LocalDate fromDate, LocalDate toDate) {
        System.out.println("Downloading " + stock);
        try {
            String url = new URIBuilder(BASE_URL)
                    .setPathSegments("api", "quote", stock, "historical")
                    .setParameter("assetclass", "stocks")
                    .setParameter("limit", "9999")
                    .setParameter("fromdate", fromDate.toString())
                    .setParameter("todate", toDate.toString())
                    .toString();
            String json = Jsoup.connect(url).ignoreContentType(true).get().text();
            Any data = JsonIterator.deserialize(json).get("data");
            List<String> csv = new ArrayList<>(data.toInt("totalRecords"));
            Any records = data.get("tradesTable").get("rows");
            for (Any record : records) {
                String date = record.toString("date");
                String open = parsePrice(record.toString("open"));
                String high = parsePrice(record.toString("high"));
                String low = parsePrice(record.toString("low"));
                String close = parsePrice(record.toString("close"));
                String volume = record.toString("volume").replace(",", "");
                csv.add(date + "," + open + "," + high + "," + low + "," + close + "," + volume);
            }
            return csv;
        } catch (URISyntaxException | IOException e) {
            handleException(e);
            return Collections.emptyList();
        }
    }

    private String parsePrice(String price) {
        return price.substring(1);
    }

}
