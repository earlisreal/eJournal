package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.client.NasdaqClient;
import io.earlisreal.ejournal.model.TradeSummary;
import io.earlisreal.ejournal.util.Configs;
import io.earlisreal.ejournal.util.Country;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static io.earlisreal.ejournal.util.CommonUtil.handleException;

public class SimpleDataService implements DataService {

    private final NasdaqClient client;
    private final List<Consumer<TradeSummary>> listeners;

    public SimpleDataService(NasdaqClient client) {
        this.client = client;
        listeners = new ArrayList<>();
    }

    @Override
    public void downloadDailyData(List<TradeSummary> summaries) {
        Set<TradeSummary> set = new HashSet<>(summaries);
        for (TradeSummary summary : set) {
            Path path = getDataPath(summary);
            LocalDate fromDate = getLastDate(path);
            if (fromDate.isEqual(LocalDate.now())) {
                continue;
            }

            if (summary.getCountry() == Country.US) {
                var data = client.getDailyHistory(summary.getStock(), fromDate, LocalDate.now().plusDays(1));
                appendToFile(path, data);
                notifyListeners(summary);
            }
        }
    }

    private LocalDate getLastDate(Path path) {
        try {
            String content = Files.readString(path);
            content = content.substring(content.lastIndexOf("\n"));
            return LocalDate.parse(content.substring(0, content.indexOf(' ')));
        } catch (IOException e) {
            return LocalDate.of(2010, 1, 1);
        }
    }

    private void appendToFile(Path path, List<String> data) {
        try {
            Files.write(path, data, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
        } catch (IOException e) {
            handleException(e);
        }
    }

    private Path getDataPath(TradeSummary summary) {
        return Configs.STOCKS_DIRECTORY
                .resolve(summary.getCountry().name())
                .resolve("daily")
                .resolve(summary.getStock() + ".csv");
    }

    @Override
    public void addListener(Consumer<TradeSummary> listener) {
        listeners.add(listener);
    }

    private void notifyListeners(TradeSummary summary) {
        for (var listener : listeners) {
            listener.accept(summary);
        }
    }

}
