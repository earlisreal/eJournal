package io.earlisreal.ejournal.dao;

import io.earlisreal.ejournal.database.FileDatabase;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;

public class CsvCacheDAO implements CacheDAO {

    private final FileDatabase fileDatabase;

    public CsvCacheDAO(FileDatabase fileDatabase) {
        this.fileDatabase = fileDatabase;
    }

    @Override
    public Optional<String> get(String key) {
        try {
            var lines = Files.readAllLines(FileDatabase.getCachePath());
            for (String line : lines) {
                if (line.startsWith(key + ",")) {
                    return Optional.of(line.substring(line.indexOf(',') + 1));
                }
            }
            return Optional.empty();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean save(String key, String value) {
        try {
            var lines = Files.readAllLines(FileDatabase.getCachePath());
            String cacheValue = key + "," + value;
            for (int i = 0; i < lines.size(); ++i) {
                if (lines.get(i).startsWith(key + ",")) {
                    if (value == null) {
                        lines.remove(i);
                    } else {
                        lines.set(i, cacheValue);
                    }
                    Files.write(FileDatabase.getCachePath(), lines);
                    return true;
                }
            }
            if (value != null) {
                lines.add(cacheValue);
                Files.write(FileDatabase.getCachePath(), lines);
            }
            return true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean clear(int secretParam) {
        return false;
    }

}
