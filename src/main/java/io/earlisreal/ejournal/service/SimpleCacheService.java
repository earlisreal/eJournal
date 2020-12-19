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
        EmailLastSync emailLastSync = emailLastSyncDAO.query(stripEmail(email));
        if (emailLastSync == null) return null;
        return emailLastSync.getLastSync();
    }

    @Override
    public void updateEmailLastSync(String email, Instant lastSync) {
        email = stripEmail(email);
        emailLastSyncDAO.update(new EmailLastSync(email, lastSync));
    }

    @Override
    public void insertEmailLastSync(String email, Instant lastSync) {
        email = stripEmail(email);
        emailLastSyncDAO.insert(new EmailLastSync(email, lastSync));
    }

    private String stripEmail(String email) {
        return email.split("@")[0];
    }

}
