package io.earlisreal.ejournal.scraper;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.parser.invoice.InvoiceParser;
import io.earlisreal.ejournal.parser.invoice.InvoiceParserFactory;
import io.earlisreal.ejournal.service.TradeLogService;
import io.earlisreal.ejournal.util.Broker;
import org.apache.http.entity.ContentType;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * This Will be discontinued for a while. As COL Financial has no official invoice. The Email Their are sending
 * as BUY or SELL confirmation doesn't have the reference number same on their ledger.
 *
 * So Automatic Scraping from Email will not be supported for COL Financial.
 */
public class COLFinancialEmailScraper implements EmailScraper {

    private final TradeLogService service;

    COLFinancialEmailScraper(TradeLogService tradeLogService) {
        this.service = tradeLogService;
    }

    @Override
    public void scrape(Gmail gmail, String messageId) {
        try {
            InvoiceParser parser = InvoiceParserFactory.getInvoiceParser(Broker.COL);
            Message message = gmail.users().messages().get(USER, messageId).execute();
            List<TradeLog> tradeLogs = new ArrayList<>();
            for (MessagePart part : message.getPayload().getParts()) {
                if (part.getBody().getData() == null) continue;
                if (part.getMimeType().equals(ContentType.TEXT_PLAIN.getMimeType())) {
                    String body = new String(part.getBody().decodeData());
                    for (String invoice : body.split("Account Name:")) {
                        if (!invoice.startsWith(System.lineSeparator())) {
                            parser.setInvoiceNo(generateInvoiceNo(invoice));
                        }
                        else {
                            TradeLog tradeLog = parser.parseAsObject(invoice);
                            tradeLogs.add(tradeLog);
                        }
                    }
                }
            }
            service.insert(tradeLogs);
        } catch (IOException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    private String generateInvoiceNo(String header) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE, MMM dd, uuuu 'at' h:mm a");
        String date = header.substring(header.indexOf("Date: ") + 6, header.indexOf("PM" + System.lineSeparator()) + 2);
        LocalDateTime dateTime = LocalDateTime.parse(date, formatter);
        return dateTime.format(DateTimeFormatter.ofPattern("uuuuMMddHHmm"));
    }

}
