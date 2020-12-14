package io.earlisreal.ejournal.scraper;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import org.apache.http.entity.ContentType;

import java.io.IOException;

public class COLFinancialEmailScraper implements EmailScraper {

    @Override
    public void scrape(Gmail service, String messageId) {
        try {
            Message message = service.users().messages().get(USER, messageId).execute();
            for (MessagePart part : message.getPayload().getParts()) {
                if (part.getBody().getData() == null) continue;
                if (part.getMimeType().equals(ContentType.TEXT_PLAIN.getMimeType())) {
                    String body = new String(part.getBody().decodeData());
                    // TODO : Parse using the Invoice Parser
                }
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

}
