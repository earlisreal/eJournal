package io.earlisreal.ejournal.client;

import java.time.LocalDate;
import java.util.List;

public interface NasdaqClient {

    String BASE_URL = "https://api.nasdaq.com/api/quote";

    List<String> getDailyHistory(String stock, LocalDate fromDate, LocalDate toDate);

}
