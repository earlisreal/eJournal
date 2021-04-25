package io.earlisreal.ejournal.service;

import java.time.Instant;
import java.time.LocalDate;

public interface CacheService {

    void clear(int secretParam);

    Instant getLastSync(String email);

    void updateEmailLastSync(String email, Instant lastSync);

    void insertEmailLastSync(String email, Instant lastSync);

    void updateStartFilter(LocalDate start);

    void updateEndFilter(LocalDate end);

    LocalDate getStartFilter();

    LocalDate getEndFilter();

    void saveUsdToPhp(double value);

    double getUsdToPhp();

    void insert(String key, String value);

    String get(String key);

    String get(String key, String defaultValue);

    void save(String key, String value);

}
