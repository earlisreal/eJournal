package io.earlisreal.ejournal.util;

import java.nio.file.Path;

public interface Configs {

    String DATA_DIR = System.getProperty("user.home") + "/.eJournal";
    Path plotDirectory = Path.of(Configs.DATA_DIR, "plot");
    Path stocksDirectory = Path.of(Configs.DATA_DIR, "stocks");

}
