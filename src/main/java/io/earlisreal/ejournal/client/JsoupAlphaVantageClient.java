package io.earlisreal.ejournal.client;

import org.apache.http.client.utils.URIBuilder;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

import static io.earlisreal.ejournal.util.CommonUtil.handleException;

public class JsoupAlphaVantageClient implements AlphaVantageClient {

    private final String apiKey;

    public JsoupAlphaVantageClient(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public List<String> get1minuteHistory(String symbol) {
        try {
            String url = new URIBuilder(URL)
                    .addParameter("function", "TIME_SERIES_INTRADAY_EXTENDED")
                    .addParameter("symbol", symbol)
                    .addParameter("interval", "1min")
                    .addParameter("slice", "year1month1")
                    .addParameter("apikey", apiKey)
                    .addParameter("adjusted", "false")
                    .toString();

            String data = Jsoup.connect(url).ignoreContentType(true).execute().body();
            String newLine = "\r\n";
            data = data.substring(data.indexOf(newLine) + newLine.length());
            return Arrays.asList(data.split(newLine));
        } catch (URISyntaxException | IOException e) {
            handleException(e);
        }

        return Collections.emptyList();
    }

}
