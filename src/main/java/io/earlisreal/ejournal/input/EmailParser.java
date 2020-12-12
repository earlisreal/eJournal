package io.earlisreal.ejournal.input;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

public class EmailParser {

    private static final JacksonFactory jsonFactory = JacksonFactory.getDefaultInstance();

    public void read() {
        try {
            NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            Gmail service = new Gmail.Builder(httpTransport, jsonFactory, getCredentials(httpTransport))
                    .setApplicationName("eJournal")
                    .build();

            String user = "me";
            var messageResponse = service.users().messages().list(user)
                    .setLabelIds(List.of("INBOX")).execute();
            var messages = messageResponse.getMessages();
            System.out.println("Messages: " + messages.size());
            for (Message m : messages) {
                Message message = service.users().messages().get(user, m.getId()).execute();
                var attachmentIds = getAttachmentIds(message.getPayload().getParts());
                for (String attachmentId : attachmentIds) {
                    byte[] data = service.users().messages().attachments().get(user, m.getId(), attachmentId).execute().decodeData();
                    String text = new PDFParser().parse(data);
                    // TODO : Save to DB or do something
                    System.out.println(text);
                }
            }
        } catch (GeneralSecurityException | IOException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    private List<String> getAttachmentIds(List<MessagePart> parts) {
        List<String> attachmentIds = new ArrayList<>();
        for (MessagePart part : parts) {
            if (part.getFilename().endsWith(".pdf")) {
                attachmentIds.add(part.getBody().getAttachmentId());
            }
            else if (part.getParts() != null){
                attachmentIds.addAll(getAttachmentIds(part.getParts()));
            }
        }

        return attachmentIds;
    }

    private Credential getCredentials(NetHttpTransport httpTransport) throws IOException {
        InputStream inputStream = getClass().getResourceAsStream("/client_id.json");
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(jsonFactory, new InputStreamReader(inputStream));
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, jsonFactory, clientSecrets, List.of(GmailScopes.GMAIL_READONLY, GmailScopes.GMAIL_LABELS))
                .setDataStoreFactory(new FileDataStoreFactory(new File("tokens")))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

}
