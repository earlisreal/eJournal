package io.earlisreal.ejournal.client;

import java.time.LocalDate;
import java.util.List;

public interface TradeZeroClient {

    String DEMO_HOME_URL = "https://demo.tradezero.co/Home";
    String DEMO_BASE_URL = "https://demo.tradezero.co";
    String HOME_URL = "https://tradezero.co/Home";
    String BASE_URL = "https://tradezero.co";

    List<String> getTradesCsv(LocalDate start, LocalDate end);

}
