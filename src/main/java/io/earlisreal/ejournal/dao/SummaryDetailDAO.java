package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.dto.SummaryDetail;

import java.util.List;

public interface SummaryDetailDAO {

    List<SummaryDetail> getAll();

    boolean insert(SummaryDetail summaryDetail);

    boolean update(SummaryDetail summaryDetail);

}
