package io.earlisreal.ejournal.ui;

import javafx.application.Preloader;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.InputStream;
import java.time.Instant;

import static io.earlisreal.ejournal.util.Configs.DEBUG_MODE;
import static java.time.temporal.ChronoUnit.MILLIS;

public class SplashScreenLauncher extends Preloader {

    private Stage primaryStage;
    private final Instant start = Instant.now();

    @Override
    public void start(Stage primaryStage) throws Exception {
        this.primaryStage = primaryStage;
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/splash-screen.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root);

        primaryStage.setTitle("eJournal");
        primaryStage.setScene(scene);
        primaryStage.initStyle(StageStyle.UNDECORATED);
        InputStream inputStream = getClass().getResourceAsStream("/eJournal-logo.png");
        assert inputStream != null;
        primaryStage.getIcons().add(new Image(inputStream));
        primaryStage.show();
    }

    @Override
    public void handleApplicationNotification(PreloaderNotification info) {
        if (info instanceof ProgressNotification progressNotification) {
            if (progressNotification.getProgress() == 1.0) {
                primaryStage.hide();
                if (DEBUG_MODE)
                    System.out.println("Splash Screen Time: " + MILLIS.between(start, Instant.now()));
            }
        }
    }

}
