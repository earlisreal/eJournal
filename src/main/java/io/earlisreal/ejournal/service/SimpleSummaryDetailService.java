package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dao.SummaryDetailDAO;
import io.earlisreal.ejournal.dto.SummaryDetail;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class SimpleSummaryDetailService implements SummaryDetailService {

    private final SummaryDetailDAO summaryDetailDAO;
    private final Map<String, SummaryDetail> detailMap;

    SimpleSummaryDetailService(SummaryDetailDAO summaryDetailDAO) {
        this.summaryDetailDAO = summaryDetailDAO;
        detailMap = summaryDetailDAO.getAll()
                .stream()
                .collect(Collectors.toMap(SummaryDetail::getId, summaryDetail -> summaryDetail));
    }

    @Override
    public Optional<SummaryDetail> getSummaryDetail(String id) {
        return Optional.ofNullable(detailMap.get(id));
    }

    @Override
    public void saveRating(String id, int rating) {
        if (detailMap.containsKey(id)) {
            SummaryDetail detail = detailMap.get(id);
            detail.setRating(rating);
            summaryDetailDAO.update(detail);
            return;
        }

        SummaryDetail detail = new SummaryDetail();
        detail.setId(id);
        detail.setRating(rating);
        if (summaryDetailDAO.insert(detail)) {
            detailMap.put(id, detail);
        }
    }

    @Override
    public void saveRemarks(String id, String remarks) {
        if (detailMap.containsKey(id)) {
            SummaryDetail detail = detailMap.get(id);
            if (Objects.equals(detail.getRemarks(), remarks)) return;

            detail.setRemarks(remarks);
            summaryDetailDAO.update(detail);
            return;
        }

        if ("".equals(remarks)) return;

        SummaryDetail detail = new SummaryDetail();
        detail.setId(id);
        detail.setRemarks(remarks);
        if (summaryDetailDAO.insert(detail)) {
            detailMap.put(id, detail);
        }
    }

}
