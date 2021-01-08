package io.earlisreal.ejournal.ui.service;

import io.earlisreal.ejournal.input.EmailParser;
import javafx.concurrent.Service;
import javafx.concurrent.Task;

public class EmailSyncService extends Service<Integer> {

    @Override
    protected Task<Integer> createTask() {
        return new Task<>() {
            @Override
            protected Integer call() throws Exception {
                return EmailParser.getInstance().parse();
            }
        };
    }

}
