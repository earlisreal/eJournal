package io.earlisreal.ejournal.service;

public interface StartupService {

    void run();

    void manageStockList();

    void createDirectories();

    void addStockPriceListener(StartupListener listener);

}
