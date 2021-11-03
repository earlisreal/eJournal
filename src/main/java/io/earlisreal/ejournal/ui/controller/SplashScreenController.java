package io.earlisreal.ejournal.ui.controller;

import javafx.application.Preloader;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class SplashScreenController extends Preloader {

    public ProgressBar progressBar;

    private Stage primaryStage;

    @Override
    public void start(Stage primaryStage) throws Exception {
        this.primaryStage = primaryStage;
        System.out.println("start");
        System.out.println(System.currentTimeMillis());
        Parent root = FXMLLoader.load(getClass().getResource("/fxml/splash-screen.fxml"));
        Scene scene = new Scene(root);

        primaryStage.setTitle("eJournal");
        primaryStage.setScene(scene);
        primaryStage.initStyle(StageStyle.UNDECORATED);
        primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/eJournal-logo.png")));
        primaryStage.show();
        System.out.println("shown");
    }

    @Override
    public void handleStateChangeNotification(StateChangeNotification info) {
        if (info.getType() == StateChangeNotification.Type.BEFORE_START) {
            primaryStage.hide();
        }
    }

}
