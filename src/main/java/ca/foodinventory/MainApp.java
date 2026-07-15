package ca.foodinventory;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.service.AppVersionService;
import ca.foodinventory.service.GitHubUpdateService;
import ca.foodinventory.service.GitHubUpdateService.UpdateInfo;
import ca.foodinventory.ui.MainView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.InputStream;
import java.util.Optional;

public class MainApp extends Application {

    private final GitHubUpdateService updateService =
            new GitHubUpdateService();

    @Override
    public void start(Stage stage) {
        DatabaseManager.initializeDatabase();

        MainView mainView = new MainView();

        Scene scene = new Scene(mainView.getView(), 1200, 700);

        scene.getStylesheets().add(
                getClass().getResource("/style.css").toExternalForm()
        );

        InputStream iconStream =
                getClass().getResourceAsStream("/images/logo.png");

        System.out.println("Icon found: " + (iconStream != null));

        if (iconStream != null) {
            Image appIcon = new Image(iconStream);
            stage.getIcons().add(appIcon);
        } else {
            System.out.println(
                    "Logo not found at: /images/logo.png"
            );
        }

        stage.setTitle(
                "ESM Operations Manager "
                        + AppVersionService.getDisplayVersion()
        );

        stage.setScene(scene);
        stage.show();

        checkForUpdates();
    }

    private void checkForUpdates() {
        updateService.checkForUpdate()
                .thenAccept(optionalUpdate ->
                        optionalUpdate.ifPresent(updateInfo ->
                                Platform.runLater(
                                        () -> showUpdateDialog(updateInfo)
                                )
                        )
                );
    }

    private void showUpdateDialog(UpdateInfo updateInfo) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);

        alert.setTitle("Update Available");
        alert.setHeaderText(
                "A newer version of ESM Operations Manager is available."
        );

        alert.setContentText(
                "Installed version: v"
                        + updateInfo.installedVersion()
                        + System.lineSeparator()
                        + "Latest version: v"
                        + updateInfo.latestVersion()
                        + System.lineSeparator()
                        + System.lineSeparator()
                        + "Would you like to download the update now?"
        );

        ButtonType downloadButton = new ButtonType(
                "Download Update",
                ButtonBar.ButtonData.OK_DONE
        );

        ButtonType laterButton = new ButtonType(
                "Not Now",
                ButtonBar.ButtonData.CANCEL_CLOSE
        );

        alert.getButtonTypes().setAll(
                downloadButton,
                laterButton
        );

        Optional<ButtonType> result = alert.showAndWait();

        if (result.isPresent()
                && result.get() == downloadButton) {

            boolean opened =
                    updateService.openUpdate(updateInfo);

            if (!opened) {
                showUnableToOpenUpdateAlert(updateInfo);
            }
        }
    }

    private void showUnableToOpenUpdateAlert(
            UpdateInfo updateInfo
    ) {
        Alert alert = new Alert(Alert.AlertType.ERROR);

        alert.setTitle("Unable to Open Update");
        alert.setHeaderText(
                "The update page could not be opened automatically."
        );

        alert.setContentText(
                "Please visit the GitHub Releases page manually:"
                        + System.lineSeparator()
                        + updateInfo.releasePageUrl()
        );

        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
