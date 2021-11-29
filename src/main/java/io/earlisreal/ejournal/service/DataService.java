package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.model.TradeSummary;

import java.util.List;
import java.util.function.Consumer;

public interface DataService {

    void downloadDailyData(List<TradeSummary> summaries);

    void addListener(Consumer<TradeSummary> listener);

}
