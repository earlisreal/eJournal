package io.earlisreal.ejournal.database;

import io.earlisreal.ejournal.util.Configs;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public final class FileDatabase {

    public static final String LOG_PATH = Configs.DATA_DIR + "/trade-logs.csv";
    public static final String CACHE_PATH = Configs.DATA_DIR + "/cache.csv";
    public static final String SUMMARY_PATH = Configs.DATA_DIR + "/summary.csv";

    private static final class FileDatabaseHolder {
        private static final FileDatabase FILE_DATABASE = new FileDatabase();
    }

    public static FileDatabase getInstance() {
        return FileDatabaseHolder.FILE_DATABASE;
    }

    private FileDatabase() {
        createFile(getLogPath());
        createFile(getCachePath());
        createFile(getSummaryPath());
    }

    public BufferedWriter getWriter(String src) {
        Path path = Paths.get(src);
        try {
            return Files.newBufferedWriter(path, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public BufferedReader getReader(String src) {
        Path path = Paths.get(src);
        try {
            return Files.newBufferedReader(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void createFile(Path path) {
        if (!Files.exists(path)) {
            try {
                Files.createFile(path);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static Path getLogPath() {
        return Paths.get(LOG_PATH);
    }

    public static Path getCachePath() {
        return Paths.get(CACHE_PATH);
    }

    public static Path getSummaryPath() {
        return Paths.get(SUMMARY_PATH);
    }

}
