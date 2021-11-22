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

public class JsoupNasdaqClient implements NasdaqClient {

    @Override
    public List<String> getDailyHistory(String stock, LocalDate fromDate, LocalDate toDate) {
        try {
            String url = new URIBuilder(BASE_URL)
                    .setPathSegments(stock, "historical")
                    .setParameter("assetclass", "stocks")
                    .setParameter("limit", "9999")
                    .setParameter("fromdate", fromDate.toString())
                    .setParameter("todate", toDate.toString())
                    .toString();
            String json = Jsoup.connect(url).execute().body();
            Any data = JsonIterator.deserialize(json).get("data");
            List<String> csv = new ArrayList<>(data.toInt("totalRecords"));
            Any records = data.get("rows");
            for (Any record : records) {
                String date = record.toString("data");
                String open = record.toString("open");
                String high = record.toString("high");
                String low = record.toString("low");
                String close = record.toString("close");
                String volume = record.toString("volume");
                csv.add(date + "," + open + "," + high + "," + low + "," + close + "," + volume);
            }
            return csv;
        } catch (URISyntaxException | IOException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

}
