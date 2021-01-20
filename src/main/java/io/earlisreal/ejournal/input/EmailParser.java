package io.earlisreal.ejournal.input;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponseException;
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
import io.earlisreal.ejournal.service.CacheService;
import io.earlisreal.ejournal.service.ServiceProvider;
import io.earlisreal.ejournal.util.Broker;
import io.earlisreal.ejournal.util.Configs;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.List;

import static io.earlisreal.ejournal.scraper.EmailAttachmentScraper.USER;

public class EmailParser {

    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    private static EmailParser instance;

    private Gmail service;

    private final Path tokenPath;

    private EmailParser() {
        tokenPath = Paths.get(Configs.DATA_DIR, "tokens");
    }

    public static EmailParser getInstance() {
        if (instance == null) {
            synchronized (EmailParser.class) {
                if (instance == null) {
                    instance = new EmailParser();
                }
            }
        }

        return instance;
    }

    public int parse() throws GeneralSecurityException, IOException {
        if (service == null) {
            newService();
        }

        Instant syncTime = Instant.now();
        CacheService cacheService = ServiceProvider.getCacheService();
        int res = 0;
        try {
            String email = service.users().getProfile(USER).execute().getEmailAddress();
            Instant lastQuery = cacheService.getLastSync(email);

            StringBuilder query = new StringBuilder("(");
            Broker[] brokers = Broker.values();
            for (int i = 0; i < Broker.values().length; ++i) {
                if (brokers[i].getEmailFilter() == null) continue;
                if (i > 0) query.append(" OR ");
                query.append("(").append(brokers[i].getEmailFilter()).append(")");
            }
            query.append(")");
            if (lastQuery != null) {
                query.append(" AND after:").append(lastQuery.getEpochSecond());
            }

            ListMessagesResponse messageResponse = service.users().messages().list(USER)
                    .setMaxResults(10_000L)
                    .setQ(query.toString())
                    .execute();
            EmailAttachmentScraper scraper = EmailAttachmentScraper.getInstance();
            var messages = messageResponse.getMessages();
            if (messages != null) {
                res = messages.parallelStream().reduce(0, (i, m) -> i + scraper.scrape(service, m.getId()), Integer::sum);
            }

            if (lastQuery == null) {
                cacheService.insertEmailLastSync(email, syncTime);
            }
            else {
                cacheService.updateEmailLastSync(email, syncTime);
            }
        } catch (TokenResponseException e) {
            Path storedCredential = tokenPath.resolve("StoredCredential");
            if (!Files.exists(storedCredential)) {
                throw e;
            }

            Files.delete(storedCredential);
            newService();
            parse();
        }
        return res;
    }

    private void newService() throws GeneralSecurityException, IOException {
        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        service = new Gmail.Builder(httpTransport, JSON_FACTORY, getCredentials(httpTransport))
                .setApplicationName("eJournal")
                .build();
    }

    private Credential getCredentials(NetHttpTransport httpTransport) throws IOException {
        InputStream inputStream = getClass().getResourceAsStream("/client_id.json");
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(inputStream));
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets, List.of(GmailScopes.GMAIL_READONLY))
                .setDataStoreFactory(new FileDataStoreFactory(tokenPath.toFile()))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

}
