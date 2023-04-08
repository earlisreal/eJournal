package io.earlisreal.ejournal.ui;

import io.earlisreal.ejournal.database.FileDatabase;
import io.earlisreal.ejournal.service.ServiceProvider;
import io.earlisreal.ejournal.util.CommonUtil;
import javafx.application.Application;
import javafx.application.Preloader;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.time.Instant;
import java.util.Objects;

import static io.earlisreal.ejournal.util.CommonUtil.printExecutionTime;

public class UILauncher extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        var start = Instant.now();
        Parent root = FXMLLoader.load(Objects.requireNonNull(getClass().getResource("/fxml/main.fxml")));
        Scene scene = new Scene(root);

        primaryStage.setTitle("eJournal");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/eJournal-logo.png"))));
        primaryStage.show();

        notifyPreloader(new Preloader.ProgressNotification(1.00));
        printExecutionTime(start, "Initializing UILauncher: ");
    }

    @Override
    public void init() throws Exception {
        FileDatabase.initialize();
        ServiceProvider.getStartupService().run();
    }

    @Override
    public void stop() {
        System.out.println("Shutting Down");
        CommonUtil.getExecutorService().shutdown();
    }

}
