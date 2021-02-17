package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dao.CacheDAO;
import io.earlisreal.ejournal.dao.EmailLastSyncDAO;
import io.earlisreal.ejournal.dto.EmailLastSync;

import java.time.Instant;

public class SimpleCacheService implements CacheService {

    private final EmailLastSyncDAO emailLastSyncDAO;
    private final CacheDAO cacheDAO;

    SimpleCacheService(EmailLastSyncDAO emailLastSyncDAO, CacheDAO cacheDAO) {
        this.emailLastSyncDAO = emailLastSyncDAO;
        this.cacheDAO = cacheDAO;
    }


    @Override
    public void deleteAllEmailSync(int secretParam) {
        emailLastSyncDAO.deleteAll(secretParam);
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

    @Override
    public void insert(String key, String value) {
        cacheDAO.insert(key, value);
    }

    private String stripEmail(String email) {
        return email.split("@")[0];
    }

}
