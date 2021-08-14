package io.earlisreal.ejournal.util;

import java.nio.file.Path;

public interface Configs {

    String DATA_DIR = System.getProperty("user.home") + "/.eJournal";
    Path PLOT_DIRECTORY = Path.of(Configs.DATA_DIR, "plot");
    Path INTRADAY_PLOT_DIRECTORY = Path.of(Configs.DATA_DIR, "plot", "intraday");
    Path STOCKS_DIRECTORY = Path.of(Configs.DATA_DIR, "stocks");

}
