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
import io.earlisreal.ejournal.scraper.EmailAttachmentScraper;
import io.earlisreal.ejournal.service.ServiceProvider;
import io.earlisreal.ejournal.util.Broker;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static io.earlisreal.ejournal.scraper.EmailScraper.USER;

public class EmailParser {

    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    private final Gmail service;

    public EmailParser() throws GeneralSecurityException, IOException {
        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        service = new Gmail.Builder(httpTransport, JSON_FACTORY, getCredentials(httpTransport))
                .setApplicationName("eJournal")
                .build();
    }

    public List<String> parse() {
        List<String> records = new ArrayList<>();
        try {
            String email = service.users().getProfile(USER).execute().getEmailAddress();
            Instant lastQuery = ServiceProvider.getCacheService().getLastSync(email);

            StringBuilder query = new StringBuilder("(");
            Broker[] brokers = Broker.values();
            for (int i = 0; i < Broker.values().length; ++i) {
                if (brokers[i].getEmailFilter() == null) continue;
                if (i > 0) query.append(" OR ");
                query.append("(").append(brokers[i].getEmailFilter()).append(")");
            }
            query.append(") ").append(" AND after:").append(lastQuery.getEpochSecond());

            ListMessagesResponse messageResponse = service.users().messages().list(USER)
                    .setMaxResults(10_000L)
                    .setQ(query.toString())
                    .execute();
            EmailAttachmentScraper scraper = EmailAttachmentScraper.getInstance();
            messageResponse.getMessages().parallelStream().forEach(m -> scraper.scrape(service, m.getId()));
        } catch (IOException e) {
            e.printStackTrace();
        }

        // TODO : Get the first message (latest) and update the settings last email sync

        return records;
    }

    private Credential getCredentials(NetHttpTransport httpTransport) throws IOException {
        InputStream inputStream = getClass().getResourceAsStream("/client_id.json");
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(inputStream));
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets, List.of(GmailScopes.GMAIL_READONLY))
                .setDataStoreFactory(new FileDataStoreFactory(new File("tokens")))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

}
