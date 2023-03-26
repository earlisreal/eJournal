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
            try (var stream = Files.lines(FileDatabase.getCachePath())) {
                return stream.map(line -> line.split(","))
                        .filter(columns -> key.equals(columns[0]))
                        .map(columns -> columns[1])
                        .findFirst();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean insert(String key, String value) {
        var writer = fileDatabase.getWriter();
        try {
            writer.write(key + "," + value);
            writer.newLine();
            return true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean clear(int secretParam) {
        return false;
    }

    @Override
    public boolean update(String s, String toString) {
        return false;
    }

}
