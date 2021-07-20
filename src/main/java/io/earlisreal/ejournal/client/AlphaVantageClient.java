package io.earlisreal.ejournal.client;

import java.util.List;

public interface AlphaVantageClient {

    String URL = "https://www.alphavantage.co/query";

    List<String> get1minuteHistory(String symbol);

}
