package io.earlisreal.ejournal.service;

import java.time.Instant;

public interface CacheService {

    void clear(int secretParam);

    Instant getLastSync(String email);

    void updateEmailLastSync(String email, Instant lastSync);

    void insertEmailLastSync(String email, Instant lastSync);

    void insert(String key, String value);

    String get(String key);

}
