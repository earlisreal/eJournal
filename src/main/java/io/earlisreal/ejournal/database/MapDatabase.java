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
    private static ConcurrentMap<String, String> stockNameMap;
    private static ConcurrentMap<String, Double> stockPriceMap;
    private static ConcurrentMap<String, String> stockSecurityMap;
    private static ConcurrentMap<String, Long> stockDateMap;
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

    public static ConcurrentMap<String, String> getStockNameMap() {
        if (stockNameMap == null) {
            synchronized (MapDatabase.class) {
                if (stockNameMap == null) {
                    stockNameMap = db.hashMap("stockNameMap", Serializer.STRING, Serializer.STRING).createOrOpen();
                }
            }
        }
        return stockNameMap;
    }

    public static ConcurrentMap<String, String> getStockSecurityMap() {
        if (stockSecurityMap == null) {
            synchronized (MapDatabase.class) {
                if (stockSecurityMap == null) {
                    stockSecurityMap = db.hashMap("stockSecurityMap", Serializer.STRING, Serializer.STRING).createOrOpen();
                }
            }
        }
        return stockSecurityMap;
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

    public static ConcurrentMap<String, Long> getStockDateMap() {
        if (stockDateMap == null) {
            synchronized (MapDatabase.class) {
                if (stockDateMap == null) {
                    stockDateMap = db.hashMap("stockDateMap", Serializer.STRING, Serializer.LONG).createOrOpen();
                }
            }
        }
        return stockDateMap;
    }

    public static ConcurrentMap<String, Double> getStockPriceMap() {
        if (stockPriceMap == null) {
            synchronized (MapDatabase.class) {
                if (stockPriceMap == null) {
                    stockPriceMap = db.hashMap("stockPriceMap", Serializer.STRING, Serializer.DOUBLE).createOrOpen();
                }
            }
        }
        return stockPriceMap;
    }

}
