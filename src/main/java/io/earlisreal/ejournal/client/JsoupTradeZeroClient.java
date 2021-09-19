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

public class JsoupTradeZeroClient implements TradeZeroClient {

    private final String username;
    private final String password;
    private final String loginUrl;
    private final String baseUrl;

    private Map<String, String> cookies;
    private LocalTime expiration;
    private boolean isMaintenance;

    public JsoupTradeZeroClient(String username, String password) {
        this.username = username;
        this.password = password;

        if (username.endsWith("DEMO")) {
            loginUrl = DEMO_HOME_URL;
            baseUrl = DEMO_BASE_URL;
        }
        else {
            loginUrl = HOME_URL;
            baseUrl = BASE_URL;
        }

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

            String url = new URIBuilder(baseUrl)
                    .setPathSegments("api", "GetCSVData", "7", start.toString(), end.toString())
                    .toString();
            var response = Jsoup.connect(url)
                    .cookies(cookies)
                    .timeout(60_000)
                    .execute();

            if (!response.hasCookie("Username")) {
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
        var response = Jsoup.connect(loginUrl)
                .data("username", username)
                .data("password", password)
                .method(Connection.Method.POST)
                .execute();
        if (response.statusCode() != 200 || !response.hasCookie("Username")) {
            System.out.println("Login Fail");
            return false;
        }

        refreshExpiration();
        cookies = response.cookies();
        return true;
    }

    private void refreshExpiration() {
        expiration = LocalTime.now().plusMinutes(29);
    }

}
