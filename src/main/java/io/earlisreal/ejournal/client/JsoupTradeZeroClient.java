package io.earlisreal.ejournal.client;

import com.jsoniter.JsonIterator;
import com.jsoniter.any.Any;
import org.apache.http.client.utils.URIBuilder;
import org.jsoup.Connection;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

public class JsoupTradeZeroClient implements TradeZeroClient {

    private final String username;
    private final String password;
    private final String loginUrl;
    private final String baseUrl;

    private String jwt;
    private LocalTime expiration;

    public JsoupTradeZeroClient(String username, String password) {
        this.username = username;
        this.password = password;

        if (username.endsWith("DEMO")) {
            loginUrl = DEMO_LOGIN_URL;
            baseUrl = DEMO_BASE_URL;
        }
        else {
            loginUrl = LOGIN_URL;
            baseUrl = BASE_URL;
        }

        expiration = LocalTime.now();
    }

    @Override
    public List<String> getTradesCsv(LocalDate start, LocalDate end) {
        try {
            if (LocalTime.now().isAfter(expiration) || jwt == null) {
                if (!login()) {
                    return Collections.emptyList();
                }
            }

            String url = new URIBuilder(baseUrl)
                    .setPathSegments("api", "Account", "GetCSVData", "7", start.toString(), end.toString())
                    .addParameter("jwt", jwt)
                    .toString();
            var response = Jsoup.connect(url)
                    .timeout(60_000)
                    .execute();

            if (response.statusCode() != 200) {
                jwt = null;
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
                .ignoreContentType(true)
                .execute();
        if (response.statusCode() != 200) {
            System.out.println("Login Fail");
            return false;
        }

        String json = response.body();
        Any data = JsonIterator.deserialize(json).get("Data");
        jwt = data.toString("jwt");

        refreshExpiration();
        return true;
    }

    private void refreshExpiration() {
        expiration = LocalTime.now().plusMinutes(29);
    }

}
