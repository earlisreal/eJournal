package io.earlisreal.ejournal.scraper;

import io.earlisreal.ejournal.dto.Stock;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PSECompanyScraper implements CompanyScraper {

    public static final String URL_SOURCE = "https://edge.pse.com.ph/companyDirectory/search.ax";

    PSECompanyScraper() {}

    @Override
    public List<Stock> scrapeCompanies() {
        List<Stock> companies = new ArrayList<>();
        int limit = 1;
        for (int page = 1; page <= limit; ++page) {
            Document document;
            try {
                document = Jsoup.connect(URL_SOURCE).data("pageNo", String.valueOf(page)).post();
            } catch (IOException e) {
                return companies;
            }

            if (page == 1) {
                String count = document.getElementsByClass("count").text();
                count = count.substring(count.indexOf('/'));
                limit = Integer.parseInt(count.substring(2, count.indexOf(']')));
            }

            Elements elements = document.select(".list > tbody > tr > td:nth-child(2) > a");
            elements.forEach(element -> {
                String onclick = element.attr("onclick");
                onclick = onclick.substring(onclick.indexOf('(') + 2, onclick.indexOf("')"));

                Stock company = new Stock();
                company.setCompanyId(onclick.substring(0, onclick.indexOf('\'')));
                company.setSecurityId(onclick.substring(onclick.lastIndexOf('\'') + 1));
                companies.add(company);
            });
        }

        return companies;
    }

}
