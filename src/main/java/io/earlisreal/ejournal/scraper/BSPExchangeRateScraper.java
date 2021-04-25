package io.earlisreal.ejournal.scraper;

import com.jsoniter.JsonIterator;
import com.jsoniter.any.Any;
import com.jsoniter.output.JsonStream;
import io.earlisreal.ejournal.util.CommonUtil;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.text.ParseException;

public class BSPExchangeRateScraper implements ExchangeRateScraper {

    public static final String BSP_ENDPOINT = "https://www.bsp.gov.ph/_api/web/lists/getByTitle('Exchange%20Rate')/items";

    @Override
    public double getUsdToPhp() {
        try {
            String json = Jsoup.connect(BSP_ENDPOINT)
                    .header(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType())
                    .ignoreContentType(true)
                    .get()
                    .text();

            var records = JsonIterator.deserialize(json).get("value");
            for (Any record : records) {
                String symbol = record.get("Symbol").toString();
                if ("USD".equals(symbol)) {
                    return CommonUtil.parseDouble(record.get("PHPequivalent").toString());
                }
            }
        } catch (IOException | ParseException  e) {
            CommonUtil.handleException(e);
        }
        return 0;
    }

}
