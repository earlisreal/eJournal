package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.model.TradeSummary;

import java.util.List;
import java.util.function.Consumer;

public interface IntradayService {

    default void download(List<TradeSummary> tradeSummaries) {
        download(tradeSummaries, ignored -> {});
    }

    void download(List<TradeSummary> tradeSummaries, Consumer<List<TradeSummary>> onDownloadFinish);

}
