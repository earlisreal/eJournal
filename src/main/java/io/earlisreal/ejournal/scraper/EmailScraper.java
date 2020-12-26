package io.earlisreal.ejournal.scraper;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;

public interface EmailScraper {

    String USER = "me";

    void scrape(Message message, Gmail gmail);

}
