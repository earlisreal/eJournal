package io.earlisreal.ejournal.client;

import io.earlisreal.ejournal.exception.AlphaVantageLimitException;

import java.util.List;

public interface AlphaVantageClient {

    String URL = "https://www.alphavantage.co/query";

    List<String> get1minuteHistory(String symbol, String slice) throws AlphaVantageLimitException;

}
