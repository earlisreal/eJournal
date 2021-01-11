package io.earlisreal.ejournal.scraper;

import io.earlisreal.ejournal.util.CommonUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;

public class PSECompanyScraper {

    public static final String URL_SOURCE = "https://edge.pse.com.ph/companyDirectory/search.ax";

    public String getCompanies() {
        StringBuilder out = new StringBuilder();
        try {
            int limit = 6;
            for (int page = 1; page <= limit; ++page) {
                Document document = Jsoup.connect(URL_SOURCE).data("pageNo", String.valueOf(page)).post();

                if (page == 1) {
                    String count = document.getElementsByClass("count").text();
                    count = count.substring(count.indexOf('/'));
                    limit = Integer.parseInt(count.substring(2, count.indexOf(']')));
                }

                Elements elements = document.select(".list > tbody > tr > td:nth-child(2) > a");
                elements.forEach(element -> {
                    out.append(element.text());

                    String onclick = element.attr("onclick");
                    onclick = onclick.substring(onclick.indexOf('(') + 2, onclick.indexOf("')"));
                    String id = onclick.substring(0, onclick.indexOf('\''));
                    String security = onclick.substring(onclick.lastIndexOf('\'') + 1);

                    out.append(',').append(id).append(',').append(security);
                    out.append(System.lineSeparator());
                });
            }
        } catch (IOException e) {
            CommonUtil.handleException(e);
        }

        return out.toString();
    }

}
