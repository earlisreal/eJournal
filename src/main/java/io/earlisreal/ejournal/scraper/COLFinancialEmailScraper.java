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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class COLFinancialEmailScraper implements EmailScraper {

    private final TradeLogService service;

    COLFinancialEmailScraper(TradeLogService tradeLogService) {
        this.service = tradeLogService;
    }

    @Override
    public void scrape(Message message, Gmail gmail) {
        InvoiceParser parser = InvoiceParserFactory.getInvoiceParser(Broker.COL);
        List<TradeLog> tradeLogs = new ArrayList<>();
        // TODO : Parse Dividends, Withdrawal, and Deposits
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
    }

    private String generateInvoiceNo(String header) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE, MMM dd, uuuu 'at' h:mm a");
        String date = header.substring(header.indexOf("Date: ") + 6, header.indexOf("PM" + System.lineSeparator()) + 2);
        LocalDateTime dateTime = LocalDateTime.parse(date, formatter);
        return dateTime.format(DateTimeFormatter.ofPattern("uuuuMMddHHmm"));
    }

}
