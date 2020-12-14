package io.earlisreal.ejournal.input;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import io.earlisreal.ejournal.util.Broker;
import org.apache.http.entity.ContentType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

public class EmailParser {

    private static final String USER = "me";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    private Gmail service;

    public EmailParser() throws GeneralSecurityException, IOException {
        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        service = new Gmail.Builder(httpTransport, JSON_FACTORY, getCredentials(httpTransport))
                .setApplicationName("eJournal")
                .build();
    }

    public List<String> parse() {
        List<String> records = new ArrayList<>();

        try {
            StringBuilder query = new StringBuilder();
            for (int i = 0; i < Broker.values().length; ++i) {
                if (i > 0) query.append(" OR ");
                query.append("(").append(Broker.values()[i].getEmailFilter()).append(")");
            }
            System.out.println(query.toString());

            ListMessagesResponse messageResponse = null;
            messageResponse = service.users().messages()
                    .list(USER).setQ(query.toString()).execute();
            var messages = messageResponse.getMessages();
            // TODO Email parsing can be done in parallel
            for (Message m : messages) {
                Message message = service.users().messages().get(USER, m.getId()).execute();
                System.out.println(message.getSnippet());
                parseAttachments(message, records);
                parseBody(message);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return records;
    }

    private void parseBody(Message message) {
        for (MessagePart part : message.getPayload().getParts()) {
            if (part.getBody().getData() == null) continue;
            if (part.getMimeType().equals(ContentType.TEXT_PLAIN.getMimeType())) {
                String body = new String(part.getBody().decodeData());
                // TODO : Parse using the Invoice Parser
            }
        }
    }

    private void parseAttachments(Message message, List<String> destinationList) throws IOException {
        var attachmentIds = getAttachmentIds(message.getPayload());
        for (String attachmentId : attachmentIds) {
            byte[] data = service.users().messages().attachments().get(USER, message.getId(), attachmentId).execute().decodeData();
            String text = new PDFParser().parse(data);
            destinationList.add(text);
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

    private Credential getCredentials(NetHttpTransport httpTransport) throws IOException {
        InputStream inputStream = getClass().getResourceAsStream("/client_id.json");
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(inputStream));
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets, List.of(GmailScopes.GMAIL_READONLY, GmailScopes.GMAIL_LABELS))
                .setDataStoreFactory(new FileDataStoreFactory(new File("tokens")))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

}
