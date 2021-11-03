package io.earlisreal.ejournal.ui;

import javafx.application.Preloader;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.InputStream;

public class SplashScreenLauncher extends Preloader {

    private Stage primaryStage;

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
        if (info instanceof ProgressNotification) {
            ProgressNotification progressNotification = (ProgressNotification) info;
            if (progressNotification.getProgress() == 1.0) {
                primaryStage.hide();
            }
        }
    }

}
