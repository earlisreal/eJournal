package io.earlisreal.ejournal.ui.service;

import io.earlisreal.ejournal.model.TradeSummary;
import io.earlisreal.ejournal.util.Interval;

public interface ChartService {

    void setSummary(TradeSummary summary);

    void setInterval(Interval interval);

    boolean isIntradayAvailable();

    boolean isDailyAvailable();

}
