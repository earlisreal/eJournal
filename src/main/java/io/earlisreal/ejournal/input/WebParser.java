package io.earlisreal.ejournal.input;

import io.earlisreal.ejournal.util.CommonUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WebParser {

    public static final String STOCK_SOURCE_URL = "https://www.pesobility.com/stock";
    List<List<String>> table;

    public WebParser() {
        table = new ArrayList<>();
    }

    public void parse() {
        try {
            Document document = Jsoup.connect(STOCK_SOURCE_URL).get();
            Elements rows = document.select("#MAIN_BODY > div > div > table > tbody > tr");
            rows.forEach(element -> {
                var columns = element.getElementsByTag("td").eachText();
                table.add(columns);
            });

        } catch (IOException e) {
            CommonUtil.handleException(e);
        }
    }

    public Map<String, String> getStockMap() {
        Map<String, String> map = new HashMap<>();
        for (List<String> columns : table) {
            String code = columns.get(0);
            String name = columns.get(1);
            map.put(name, code);
        }
        return map;
    }

    public Map<String, Double> getPriceMap() {
        Map<String, Double> map = new HashMap<>();
        for (List<String> columns : table) {
            String code = columns.get(0);
            double price = Double.parseDouble(columns.get(2).substring(0, columns.get(2).indexOf(" ")));
            map.put(code, price);
        }
        return map;
    }

}
