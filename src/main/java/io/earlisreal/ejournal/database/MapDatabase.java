package io.earlisreal.ejournal.database;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentMap;

import static io.earlisreal.ejournal.util.Configs.DATA_DIR;

public class MapDatabase {

    private static DB db;
    private static ConcurrentMap<String, String> stockMap;
    private static ConcurrentMap<String, Boolean> settings;

    public static DB initialize() throws IOException {
        if (db == null) {
            Path basePath = Path.of(DATA_DIR);
            if (!Files.exists(basePath)) {
                Files.createDirectory(basePath);
            }
            db = DBMaker.fileDB(basePath.resolve(Path.of("eJournal.dat")).toFile()).fileMmapEnableIfSupported().make();
        }
        return db;
    }

    public static ConcurrentMap<String, String> getStockMap() {
        if (stockMap == null) {
            synchronized (MapDatabase.class) {
                if (stockMap == null) {
                    stockMap = db.hashMap("stockMap", Serializer.STRING, Serializer.STRING).createOrOpen();
                }
            }
        }
        return stockMap;
    }

    public static ConcurrentMap<String, Boolean> getSettingsMap() {
        if (settings == null) {
            synchronized (MapDatabase.class) {
                if (settings == null) {
                    settings = db.hashMap("settings", Serializer.STRING, Serializer.BOOLEAN).createOrOpen();
                }
            }
        }
        return settings;
    }

}
