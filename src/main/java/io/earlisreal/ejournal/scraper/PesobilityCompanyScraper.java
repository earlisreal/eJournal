package io.earlisreal.ejournal.scraper;

import io.earlisreal.ejournal.dto.Stock;
import io.earlisreal.ejournal.service.StockService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.List;

import static io.earlisreal.ejournal.util.CommonUtil.handleException;

public class PesobilityCompanyScraper implements CompanyScraper {

    public static final String URL_SOURCE = "https://www.pesobility.com/stock/";

    private final StockService stockService;

    PesobilityCompanyScraper(StockService stockService) {
        this.stockService = stockService;
    }

    @Override
    public List<Stock> scrapeCompanies() {
        List<Stock> stocks = stockService.getEmptyIds();
        stocks.parallelStream().forEach(stock -> {
            try {
                Document document = Jsoup.connect(URL_SOURCE + stock.getCode()).get();
                String link = document.selectFirst("p > a.button").attr("href");
                link = link.substring(link.indexOf('?') + 1);
                String[] tokens = link.split("&");
                String companyId = tokens[0].split("=")[1];
                String securityId = tokens[1].split("=")[1];
                stock.setCompanyId(companyId);
                stock.setSecurityId(securityId);
            } catch (IOException e) {
                handleException(e);
            }
        });
        return stocks;
    }

}
