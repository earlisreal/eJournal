package io.earlisreal.ejournal.scraper;

import io.earlisreal.ejournal.util.Configs;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class PSECompanyScraper {

    public static final String URL_SOURCE = "https://edge.pse.com.ph/companyDirectory/search.ax";

    public void saveCompanies() {
        try {
            List<String> companies = scrapeCompanies();
            Files.write(Paths.get(Configs.DATA_DIR, "companies.csv"), companies);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<String> scrapeCompanies() throws IOException {
        List<String> companies = new ArrayList<>();
        int limit = 1;
        for (int page = 1; page <= limit; ++page) {
            Document document = Jsoup.connect(URL_SOURCE).data("pageNo", String.valueOf(page)).post();

            if (page == 1) {
                String count = document.getElementsByClass("count").text();
                count = count.substring(count.indexOf('/'));
                limit = Integer.parseInt(count.substring(2, count.indexOf(']')));
            }

            Elements elements = document.select(".list > tbody > tr > td:nth-child(2) > a");
            elements.forEach(element -> {
                StringBuilder out = new StringBuilder(element.text());

                String onclick = element.attr("onclick");
                onclick = onclick.substring(onclick.indexOf('(') + 2, onclick.indexOf("')"));
                String id = onclick.substring(0, onclick.indexOf('\''));
                String security = onclick.substring(onclick.lastIndexOf('\'') + 1);

                out.append(',').append(id).append(',').append(security);
                companies.add(out.toString());
            });
        }

        return companies;
    }

}
