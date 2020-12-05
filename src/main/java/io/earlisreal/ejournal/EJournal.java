package io.earlisreal.ejournal;

import io.earlisreal.ejournal.database.DerbyDatabase;
import io.earlisreal.ejournal.service.ServiceProvider;

import java.sql.Connection;
import java.sql.SQLException;

public class EJournal {

    public static void main(String[] args) {
        System.out.println("Welcome to eJournal!");
        try (Connection ignored = DerbyDatabase.initialize()) {
            EJournal eJournal = new EJournal();
            eJournal.run(args);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }

        System.out.println("bye!");
    }

    public void run(String[] args) {
        if (args.length > 0) {
            if (args[0].equals("csv")) {
                ServiceProvider.getTradeLogService().insertCsvFromConsole();
            }
        }
    }

}
