package io.earlisreal.ejournal.client;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

public interface TradeZeroClient {

    String LOGIN_URL = "https://www.tradezero.co/api/login/loginjwt";
    String BASE_URL = "https://tradezero.co";

    List<String> getTradesCsv(LocalDate start, LocalDate end);

    boolean login() throws IOException;

}
