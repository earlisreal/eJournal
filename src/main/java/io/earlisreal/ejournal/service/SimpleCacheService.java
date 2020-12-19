package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dao.EmailLastSyncDAO;
import io.earlisreal.ejournal.dto.EmailLastSync;

import java.time.Instant;

public class SimpleCacheService implements CacheService {

    private final EmailLastSyncDAO emailLastSyncDAO;

    SimpleCacheService(EmailLastSyncDAO emailLastSyncDAO) {
        this.emailLastSyncDAO = emailLastSyncDAO;
    }


    @Override
    public Instant getLastSync(String email) {
        return emailLastSyncDAO.query(email).getLastSync();
    }

    @Override
    public void updateEmailLastSync(String email, Instant lastSync) {
        emailLastSyncDAO.insert(new EmailLastSync(email, lastSync));
    }

}
