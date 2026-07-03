package ca.foodinventory;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.ui.MainView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.InputStream;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) {
        DatabaseManager.initializeDatabase();

        MainView mainView = new MainView();

        Scene scene = new Scene(mainView.getView(), 1200, 700);
        scene.getStylesheets().add(
                getClass().getResource("/style.css").toExternalForm()
        );

        InputStream iconStream = getClass().getResourceAsStream("/images/logo.png");

        System.out.println("Icon found: " + (iconStream != null));

        if (iconStream != null) {
            Image appIcon = new Image(iconStream);
            stage.getIcons().add(appIcon);
        } else {
            System.out.println("Logo not found at: /images/logo.png");
        }

        stage.setTitle("ESM 8679 Inventory Manager");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}