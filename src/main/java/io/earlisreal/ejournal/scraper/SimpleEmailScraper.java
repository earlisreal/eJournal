package io.earlisreal.ejournal.scraper;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import io.earlisreal.ejournal.input.PDFParser;

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
                            .attachments().get("me", message.getId(), attachmentId).execute().decodeData();
                    String text = new PDFParser().parse(data);
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
