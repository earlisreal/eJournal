package io.earlisreal.ejournal.client;

import org.apache.http.client.utils.URIBuilder;
import org.jsoup.Connection;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DemoTradeZeroClient implements TradeZeroClient {

    public static final String DEMO_LOGIN_URL = "https://demo.tradezero.co/Home";
    public static final String DEMO_BASE_URL = "https://demo.tradezero.co";
    private final String username;
    private final String password;

    private LocalTime expiration;
    private Map<String, String> cookies;

    public DemoTradeZeroClient(String username, String password) {
        this.username = username;
        this.password = password;
        expiration = LocalTime.now();
    }

    @Override
    public List<String> getTradesCsv(LocalDate start, LocalDate end) {
        try {
            if (LocalTime.now().isAfter(expiration) || cookies == null) {
                if (!login()) {
                    return Collections.emptyList();
                }
            }
            String url = new URIBuilder(DEMO_BASE_URL)
                    .setPathSegments("api", "GetCSVData", "7", start.toString(), end.toString())
                    .toString();
            var response = Jsoup.connect(url)
                    .cookies(cookies)
                    .timeout(60_000)
                    .execute();

            if (response.statusCode() != 200) {
                cookies = null;
                return Collections.emptyList();
            }

            refreshExpiration();
            String raw = response.body();
            return List.of(raw.split("\n"));
        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public boolean login() throws IOException {
        var response = Jsoup.connect(DEMO_LOGIN_URL)
                .data("username", username)
                .data("password", password)
                .method(Connection.Method.POST)
                .ignoreContentType(true)
                .execute();
        if (response.statusCode() != 200) {
            System.out.println("Login Fail");
            return false;
        }

        cookies = response.cookies();

        refreshExpiration();
        return true;
    }

    private void refreshExpiration() {
        expiration = LocalTime.now().plusMinutes(29);
    }

}
