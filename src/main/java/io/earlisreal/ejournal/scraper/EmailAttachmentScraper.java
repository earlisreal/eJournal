package io.earlisreal.ejournal.scraper;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.input.PDFParser;
import io.earlisreal.ejournal.parser.invoice.InvoiceParserFactory;
import io.earlisreal.ejournal.service.ServiceProvider;
import io.earlisreal.ejournal.service.TradeLogService;
import io.earlisreal.ejournal.util.Broker;
import io.earlisreal.ejournal.util.CommonUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class EmailAttachmentScraper implements EmailScraper {

    private final TradeLogService service;
    private static EmailAttachmentScraper emailAttachmentScraper;

    EmailAttachmentScraper(TradeLogService tradeLogService) {
        this.service = tradeLogService;
    }

    @Override
    public void scrape(Message message, Gmail gmail) {
        List<TradeLog> tradeLogs = new ArrayList<>();
        var attachmentIds = getAttachmentIds(message.getPayload());
        for (String attachmentId : attachmentIds) {
            try {
                byte[] data = gmail.users().messages()
                        .attachments().get(USER, message.getId(), attachmentId).execute().decodeData();
                String invoice = new PDFParser().parse(data);
                Broker broker = CommonUtil.identifyBroker(invoice);
                TradeLog tradeLog = InvoiceParserFactory.getInvoiceParser(broker).parseAsObject(invoice);
                tradeLogs.add(tradeLog);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        service.insert(tradeLogs);
    }

    private List<String> getAttachmentIds(MessagePart messagePart) {
        List<String> attachmentIds = new ArrayList<>();
        if (messagePart.getFilename().toLowerCase().endsWith(".pdf")) {
            attachmentIds.add(messagePart.getBody().getAttachmentId());
        }

        if (messagePart.getParts() != null) {
            for (MessagePart part : messagePart.getParts()) {
                attachmentIds.addAll(getAttachmentIds(part));
            }
        }

        return attachmentIds;
    }

    public static EmailAttachmentScraper getInstance() {
        if (emailAttachmentScraper == null) {
            synchronized (EmailAttachmentScraper.class) {
                if (emailAttachmentScraper == null) {
                    emailAttachmentScraper = new EmailAttachmentScraper(ServiceProvider.getTradeLogService());
                }
            }
        }

        return emailAttachmentScraper;
    }

}
