package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dto.SummaryDetail;

import java.util.Optional;

public interface SummaryDetailService {

    Optional<SummaryDetail> getSummaryDetail(String id);

    void saveRating(String id, int rating);

    void saveRemarks(String id, String remarks);

}
