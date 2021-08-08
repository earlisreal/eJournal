package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dto.Stock;

import java.time.LocalDate;
import java.util.List;

public interface IntradayService {

    void download(Stock stock, List<LocalDate> dates);

}
