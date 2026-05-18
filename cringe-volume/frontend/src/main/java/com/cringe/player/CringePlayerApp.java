package com.cringe.player;

import javafx.application.Application;
import javafx.application.HostServices;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.InputStream;

public class CringePlayerApp extends Application {

    private static HostServices hostServicesInstance;

    @Override
    public void start(Stage primaryStage) throws Exception {
        hostServicesInstance = getHostServices();

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/main.fxml")
        );
        Parent root = loader.load();

        Scene scene = new Scene(root, 520, 500);
        primaryStage.setTitle("Cringe Volume Player");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);

        // иконка окна (если есть clown.png в ресурсах)
        InputStream iconStream = getClass().getResourceAsStream("/icons/clown.png");
        if (iconStream != null) {
            primaryStage.getIcons().add(new Image(iconStream));
        }

        primaryStage.show();
    }

    public static HostServices hostServices() {
        return hostServicesInstance;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
