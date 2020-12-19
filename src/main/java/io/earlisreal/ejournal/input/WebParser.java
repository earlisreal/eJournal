package io.earlisreal.ejournal.input;

import io.earlisreal.ejournal.util.CommonUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;

public class WebParser {

    public static final String STOCK_SOURCE_URL = "https://www.pesobility.com/stock";

    public void parse() {
        try {
            Document document = Jsoup.connect(STOCK_SOURCE_URL).get();
            Elements rows = document.select("#MAIN_BODY > div > div > table > tbody > tr");
            rows.forEach(element -> {
                var columns = element.getElementsByTag("td").eachText();
                System.out.println(columns);
            });
        } catch (IOException e) {
            CommonUtil.handleException(e);
        }
    }

}
