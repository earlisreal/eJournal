package io.earlisreal.ejournal.dto;

import io.earlisreal.ejournal.model.TradeSummary;

import java.util.List;

public class AlphaSummary {

    private String id;
    private List<TradeSummary> summaries;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<TradeSummary> getSummaries() {
        return summaries;
    }

    public void setSummaries(List<TradeSummary> summaries) {
        this.summaries = summaries;
    }

}
