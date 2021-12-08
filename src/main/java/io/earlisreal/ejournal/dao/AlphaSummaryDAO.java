package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.dto.AlphaSummary;
import io.earlisreal.ejournal.model.TradeSummary;

import java.util.List;

public interface AlphaSummaryDAO {

    List<AlphaSummary> getAll();

    boolean insert(List<TradeSummary> summaries);

    boolean delete(int id);

}
