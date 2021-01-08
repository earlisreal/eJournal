package io.earlisreal.ejournal.ui.service;

import io.earlisreal.ejournal.input.EmailParser;
import javafx.concurrent.Service;
import javafx.concurrent.Task;

public class EmailSyncService extends Service<Void> {

    @Override
    protected Task<Void> createTask() {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                EmailParser.getInstance().parse();

                return null;
            }
        };
    }

}
