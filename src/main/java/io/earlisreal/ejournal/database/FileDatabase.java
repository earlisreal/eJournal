package io.earlisreal.ejournal.database;

import io.earlisreal.ejournal.util.Configs;

public class FileDatabase {

    public static String getPath() {
        return Configs.DATA_DIR + "/trade-logs.csv";
    }

}
