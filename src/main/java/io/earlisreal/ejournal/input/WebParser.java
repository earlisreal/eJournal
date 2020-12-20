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

import static io.earlisreal.ejournal.util.CommonUtil.trimStockName;

public class WebParser {

    public static final String STOCK_SOURCE_URL = "https://www.pesobility.com/stock";
    private final List<List<String>> table;
    private final Map<String, String> stockMap;
    private final Map<String, Double> priceMap;

    public WebParser() {
        table = new ArrayList<>();
        stockMap = new HashMap<>();
        priceMap = new HashMap<>();
    }

    public void parse() {
        priceMap.clear();
        stockMap.clear();
        table.clear();
        try {
            Document document = Jsoup.connect(STOCK_SOURCE_URL).get();
            Elements rows = document.select("#MAIN_BODY > div > div > table > tbody > tr");
            rows.forEach(element -> {
                var columns = element.getElementsByTag("td").eachText();
                System.out.println(columns);
                table.add(columns);
            });

        } catch (IOException e) {
            CommonUtil.handleException(e);
        }
    }

    public Map<String, String> getStockMap() {
        if (!stockMap.isEmpty()) return stockMap;

        for (List<String> columns : table) {
            String code = columns.get(0);
            String name = columns.get(1);
            stockMap.put(trimStockName(name), code);
        }
        return stockMap;
    }

    public Map<String, Double> getPriceMap() {
        if (!priceMap.isEmpty()) return priceMap;

        for (List<String> columns : table) {
            String code = columns.get(0);
            double price = Double.parseDouble(columns.get(2).substring(0, columns.get(2).indexOf(" ")));
            priceMap.put(code, price);
        }
        return priceMap;
    }

}
