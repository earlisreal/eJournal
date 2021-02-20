package io.earlisreal.ejournal.service;

import io.earlisreal.ejournal.dao.CacheDAO;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

public class SimpleCacheService implements CacheService {

    private enum Key {
        EMAIL, START_FILTER, END_FILTER
    }

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
        Optional<String> last = cacheDAO.get(Key.EMAIL + stripEmail(email));
        if (last.isEmpty()) return null;
        return Instant.parse(last.get());
    }

    @Override
    public void updateEmailLastSync(String email, Instant lastSync) {
        cacheDAO.update(Key.EMAIL + stripEmail(email), lastSync.toString());
    }

    @Override
    public void insertEmailLastSync(String email, Instant lastSync) {
        cacheDAO.insert(Key.EMAIL + stripEmail(email), lastSync.toString());
    }

    @Override
    public void updateStartFilter(LocalDate start) {
        String value = start == null ? null : start.toString();
        if (!cacheDAO.update(Key.START_FILTER.toString(), value)) {
            cacheDAO.insert(Key.START_FILTER.toString(), value);
        }
    }

    @Override
    public void updateEndFilter(LocalDate end) {
        String value = end == null ? null : end.toString();
        if (!cacheDAO.update(Key.END_FILTER.toString(), value)) {
            cacheDAO.insert(Key.END_FILTER.toString(), value);
        }
    }

    @Override
    public LocalDate getStartFilter() {
        Optional<String> start = cacheDAO.get(Key.START_FILTER.toString());
        if (start.isEmpty()) return null;
        return LocalDate.parse(start.get());
    }

    @Override
    public LocalDate getEndFilter() {
        Optional<String> end = cacheDAO.get(Key.END_FILTER.toString());
        if (end.isEmpty()) return null;
        return LocalDate.parse(end.get());
    }

    @Override
    public void insert(String key, String value) {
        cacheDAO.insert(key, value);
    }

    @Override
    public String get(String key) {
        return cacheDAO.get(key).orElse(null);
    }

    @Override
    public String get(String key, String defaultValue) {
        return cacheDAO.get(key).orElse(defaultValue);
    }

    private String stripEmail(String email) {
        return email.split("@")[0];
    }

}
