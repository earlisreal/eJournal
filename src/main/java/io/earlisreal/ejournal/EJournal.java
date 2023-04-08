package io.earlisreal.ejournal;

import com.sun.javafx.application.LauncherImpl;
import io.earlisreal.ejournal.ui.SplashScreenLauncher;
import io.earlisreal.ejournal.ui.UILauncher;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class EJournal {

    public static void main(String[] args) {
        System.out.println("Welcome to eJournal!");
        Runtime.getRuntime().addShutdownHook(new Thread(EJournal::onShutdown));
        try {
            EJournal eJournal = new EJournal();
            eJournal.run(args);
        } catch (GeneralSecurityException | IOException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    public void run(String[] args) throws GeneralSecurityException, IOException {
        LauncherImpl.launchApplication(UILauncher.class, SplashScreenLauncher.class, args);
    }

    public static void onShutdown() {
        System.out.println("bye!");
    }

}
