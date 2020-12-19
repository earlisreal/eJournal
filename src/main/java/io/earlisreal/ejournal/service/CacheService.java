package io.earlisreal.ejournal.service;

import java.time.Instant;

public interface CacheService {

    Instant getLastSync(String email);

    void updateEmailLastSync(String email, Instant lastSync);

}
