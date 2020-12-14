package io.earlisreal.ejournal.scraper;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import io.earlisreal.ejournal.dto.TradeLog;
import io.earlisreal.ejournal.input.PDFParser;
import io.earlisreal.ejournal.parser.invoice.InvoiceParserFactory;
import io.earlisreal.ejournal.util.Broker;
import io.earlisreal.ejournal.util.CommonUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SimpleEmailScraper implements EmailScraper {

    public void scrape(Gmail service, String messageId) {
        try {
            Message message = service.users().messages().get(USER, messageId).execute();
            var attachmentIds = getAttachmentIds(message.getPayload());
            for (String attachmentId : attachmentIds) {
                try {
                    byte[] data = service.users().messages()
                            .attachments().get(USER, message.getId(), attachmentId).execute().decodeData();
                    String invoice = new PDFParser().parse(data);
                    Broker broker = CommonUtil.identifyBroker(invoice);
                    TradeLog tradeLog = InvoiceParserFactory.getInvoiceParser(broker).parseAsObject(invoice);
                    // TODO : Detect if the the tradeLog is already in the database
                    // TODO : Insert to DB
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private List<String> getAttachmentIds(MessagePart messagePart) {
        List<String> attachmentIds = new ArrayList<>();
        if (messagePart.getFilename().endsWith(".pdf")) {
            attachmentIds.add(messagePart.getBody().getAttachmentId());
        }

        if (messagePart.getParts() != null) {
            for (MessagePart part : messagePart.getParts()) {
                attachmentIds.addAll(getAttachmentIds(part));
            }
        }

        return attachmentIds;
    }

}
