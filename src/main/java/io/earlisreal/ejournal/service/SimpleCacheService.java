package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dao.CacheDAO;

import java.time.Instant;

public class SimpleCacheService implements CacheService {

    private static final String EMAIL_KEY = "email";

    private final CacheDAO cacheDAO;

    SimpleCacheService(CacheDAO cacheDAO) {
        this.cacheDAO = cacheDAO;
    }

    @Override
    public void clear(int secretParam) {
        cacheDAO.clear(secretParam);
    }

    @Override
    public Instant getLastSync(String email) {
        String last = cacheDAO.get("email" + stripEmail(email));
        if (last == null) return null;
        return Instant.parse(last);
    }

    @Override
    public void updateEmailLastSync(String email, Instant lastSync) {
        cacheDAO.update(EMAIL_KEY + stripEmail(email), lastSync.toString());
    }

    @Override
    public void insertEmailLastSync(String email, Instant lastSync) {
        cacheDAO.insert(EMAIL_KEY + stripEmail(email), lastSync.toString());
    }

    @Override
    public void insert(String key, String value) {
        cacheDAO.insert(key, value);
    }

    private String stripEmail(String email) {
        return email.split("@")[0];
    }

}
