package io.earlisreal.ejournal.ui;

import io.earlisreal.ejournal.database.DerbyDatabase;
import io.earlisreal.ejournal.service.ServiceProvider;
import javafx.application.Application;
import javafx.application.Preloader;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.util.Objects;

public class UILauncher extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(Objects.requireNonNull(getClass().getResource("/fxml/main.fxml")));
        Scene scene = new Scene(root);

        primaryStage.setTitle("eJournal");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/eJournal-logo.png"))));
        primaryStage.show();

        notifyPreloader(new Preloader.ProgressNotification(1.00));
    }

    @Override
    public void init() throws Exception {
        DerbyDatabase.initialize();
        ServiceProvider.getStartupService().run();
    }

}
