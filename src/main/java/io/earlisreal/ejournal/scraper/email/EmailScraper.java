package io.earlisreal.ejournal.scraper;

import com.google.api.services.gmail.Gmail;

public interface EmailScraper {

    String USER = "me";

    void scrape(Gmail service, String messageId);

}
