package ca.foodinventory;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.service.AppVersionService;
import ca.foodinventory.service.GitHubUpdateService;
import ca.foodinventory.service.GitHubUpdateService.UpdateInfo;
import ca.foodinventory.ui.MainView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class MainApp extends Application {

    private final GitHubUpdateService updateService =
            new GitHubUpdateService();

    private Stage primaryStage;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
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
            stage.getIcons().add(new Image(iconStream));
        } else {
            System.out.println("Logo not found at: /images/logo.png");
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
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(primaryStage);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle("Update Available");
        dialog.setHeaderText(null);

        ButtonType downloadButton = new ButtonType(
                updateInfo.downloadUrl() == null
                        ? "Open Release Page"
                        : "Download & Install",
                ButtonBar.ButtonData.OK_DONE
        );
        ButtonType laterButton = new ButtonType(
                "Not Now",
                ButtonBar.ButtonData.CANCEL_CLOSE
        );

        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().setAll(downloadButton, laterButton);

        Label installedVersion = new Label(
                "Current version:  " + updateInfo.installedVersion()
        );
        installedVersion.setStyle(
                "-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #202020;"
        );

        Label latestVersion = new Label(
                "New version:      " + updateInfo.latestVersion()
        );
        latestVersion.setStyle(
                "-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #202020;"
        );

        Label downloadSize = new Label(
                "Download size:   " + formatBytes(updateInfo.downloadSizeBytes())
        );
        downloadSize.setStyle("-fx-text-fill: #202020;");

        VBox versionDetails = new VBox(
                6,
                installedVersion,
                latestVersion,
                downloadSize
        );

        Label notesHeading = new Label("What's New");
        notesHeading.setStyle(
                "-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #202020;"
        );

        TextArea releaseNotes = new TextArea(updateInfo.releaseNotes());
        releaseNotes.setEditable(false);
        releaseNotes.setWrapText(true);
        releaseNotes.setPrefRowCount(8);
        releaseNotes.setMinHeight(170);
        releaseNotes.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(releaseNotes, Priority.ALWAYS);

        VBox content = new VBox(
                12,
                createDialogHeading(
                        "ESM Operations Manager "
                                + updateInfo.latestVersion()
                                + " is available."
                ),
                versionDetails,
                notesHeading,
                releaseNotes
        );
        content.setPadding(new Insets(8));
        content.setPrefWidth(560);
        content.setMinWidth(520);

        pane.setContent(content);
        configureDialogPane(pane, 620);
        styleDialogButton(pane, downloadButton, true);
        styleDialogButton(pane, laterButton, false);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != downloadButton) {
            return;
        }

        if (updateInfo.downloadUrl() == null) {
            if (!updateService.openReleasePage(updateInfo)) {
                showError(
                        "Unable to Open Release",
                        "The GitHub release page could not be opened."
                );
            }
            return;
        }

        showDownloadDialog(updateInfo);
    }

    private void showDownloadDialog(UpdateInfo updateInfo) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(primaryStage);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle("Downloading Update");
        dialog.setHeaderText(null);

        ButtonType cancelButton = new ButtonType(
                "Cancel",
                ButtonBar.ButtonData.CANCEL_CLOSE
        );
        dialog.getDialogPane().getButtonTypes().add(cancelButton);

        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);

        Label progressLabel = new Label("Preparing download...");
        VBox content = new VBox(
                12,
                createDialogHeading(
                        "Downloading ESM Operations Manager v"
                                + updateInfo.latestVersion()
                ),
                progressBar,
                progressLabel
        );
        content.setPadding(new Insets(10));
        content.setPrefWidth(480);
        DialogPane pane = dialog.getDialogPane();
        pane.setContent(content);
        configureDialogPane(pane, 540);
        styleDialogButton(pane, cancelButton, false);

        AtomicBoolean cancellationRequested = new AtomicBoolean(false);
        AtomicReference<Path> completedInstaller = new AtomicReference<>();
        AtomicReference<Throwable> downloadFailure = new AtomicReference<>();
        dialog.setOnCloseRequest(event -> cancellationRequested.set(true));

        updateService.downloadUpdate(
                        updateInfo,
                        (downloadedBytes, totalBytes) ->
                                Platform.runLater(() -> {
                                    if (totalBytes > 0) {
                                        progressBar.setProgress(
                                                (double) downloadedBytes
                                                        / totalBytes
                                        );
                                        progressLabel.setText(
                                                formatBytes(downloadedBytes)
                                                        + " / "
                                                        + formatBytes(totalBytes)
                                        );
                                    } else {
                                        progressBar.setProgress(
                                                ProgressIndicator.INDETERMINATE_PROGRESS
                                        );
                                        progressLabel.setText(
                                                formatBytes(downloadedBytes)
                                                        + " downloaded"
                                        );
                                    }
                                }),
                        cancellationRequested::get
                )
                .thenAccept(installerPath ->
                        Platform.runLater(() -> {
                            completedInstaller.set(installerPath);
                            dialog.close();
                        })
                )
                .exceptionally(exception -> {
                    Platform.runLater(() -> {
                        Throwable cause = rootCause(exception);

                        if (!(cause instanceof CancellationException)) {
                            downloadFailure.set(cause);
                        }

                        dialog.close();
                    });
                    return null;
                });

        dialog.showAndWait();

        if (completedInstaller.get() != null) {
            confirmAndLaunchInstaller(updateInfo, completedInstaller.get());
            return;
        }

        if (downloadFailure.get() != null) {
            showDownloadFailure(updateInfo, downloadFailure.get());
        }
    }

    private void confirmAndLaunchInstaller(
            UpdateInfo updateInfo,
            Path installerPath
    ) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(primaryStage);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle("Update Ready");
        dialog.setHeaderText(null);

        ButtonType installButton = new ButtonType(
                "Install Now",
                ButtonBar.ButtonData.OK_DONE
        );
        ButtonType laterButton = new ButtonType(
                "Install Later",
                ButtonBar.ButtonData.CANCEL_CLOSE
        );

        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().setAll(installButton, laterButton);

        Label versionLabel = new Label(
                "Current version: " + updateInfo.installedVersion()
                        + System.lineSeparator()
                        + "New version: " + updateInfo.latestVersion()
        );
        versionLabel.setStyle(
                "-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #202020;"
        );

        Label messageLabel = new Label(
                "The application will close and the Windows installer "
                        + "will open to complete the update."
        );
        messageLabel.setWrapText(true);
        messageLabel.setStyle("-fx-text-fill: #202020;");

        VBox content = new VBox(
                12,
                createDialogHeading(
                        "ESM Operations Manager "
                                + updateInfo.latestVersion()
                                + " is ready to install."
                ),
                versionLabel,
                messageLabel
        );
        content.setPadding(new Insets(10));
        content.setPrefWidth(470);
        content.setMinWidth(430);

        pane.setContent(content);
        configureDialogPane(pane, 540);
        styleDialogButton(pane, installButton, true);
        styleDialogButton(pane, laterButton, false);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != installButton) {
            return;
        }

        if (updateService.launchInstaller(installerPath)) {
            Platform.exit();
        } else {
            showError(
                    "Unable to Launch Installer",
                    "The update was downloaded, but Windows could not "
                            + "launch the installer. It is located at:"
                            + System.lineSeparator()
                            + installerPath.toAbsolutePath()
            );
        }
    }

    private void showDownloadFailure(
            UpdateInfo updateInfo,
            Throwable exception
    ) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(primaryStage);
        alert.setTitle("Update Download Failed");
        alert.setHeaderText("The update could not be downloaded.");
        alert.setContentText(
                safeMessage(exception)
                        + System.lineSeparator()
                        + System.lineSeparator()
                        + "You can retry the next time the application starts, "
                        + "or open the GitHub release page."
        );

        ButtonType releasePageButton = new ButtonType(
                "Open Release Page",
                ButtonBar.ButtonData.OK_DONE
        );
        ButtonType closeButton = new ButtonType(
                "Close",
                ButtonBar.ButtonData.CANCEL_CLOSE
        );
        alert.getButtonTypes().setAll(releasePageButton, closeButton);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == releasePageButton) {
            updateService.openReleasePage(updateInfo);
        }
    }

    private void showError(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(primaryStage);
        alert.setTitle("Update Error");
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void configureDialogPane(DialogPane pane, double preferredWidth) {
        String stylesheet = getClass().getResource("/style.css").toExternalForm();
        if (!pane.getStylesheets().contains(stylesheet)) {
            pane.getStylesheets().add(stylesheet);
        }

        if (!pane.getStyleClass().contains("update-dialog")) {
            pane.getStyleClass().add("update-dialog");
        }

        pane.setPrefWidth(preferredWidth);
        pane.setMinWidth(preferredWidth);
        pane.setMinHeight(Region.USE_PREF_SIZE);
    }

    private Label createDialogHeading(String text) {
        Label heading = new Label(text);
        heading.getStyleClass().add("update-dialog-heading");
        heading.setWrapText(true);
        heading.setMaxWidth(Double.MAX_VALUE);
        return heading;
    }

    private void styleDialogButton(
            DialogPane pane,
            ButtonType buttonType,
            boolean primary
    ) {
        Button button = (Button) pane.lookupButton(buttonType);
        if (button == null) {
            return;
        }

        button.setMinWidth(120);
        button.setMinHeight(34);

        if (primary) {
            button.setStyle(
                    "-fx-background-color: #2f6fad;"
                            + "-fx-text-fill: white;"
                            + "-fx-font-weight: bold;"
                            + "-fx-background-radius: 4px;"
                            + "-fx-padding: 7px 14px;"
            );
        } else {
            button.setStyle(
                    "-fx-background-color: #e6e6e6;"
                            + "-fx-text-fill: #202020;"
                            + "-fx-font-weight: bold;"
                            + "-fx-background-radius: 4px;"
                            + "-fx-padding: 7px 14px;"
            );
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 0) {
            return "Unknown";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }

        double kilobytes = bytes / 1024.0;
        if (kilobytes < 1024) {
            return String.format("%.1f KB", kilobytes);
        }

        double megabytes = kilobytes / 1024.0;
        if (megabytes < 1024) {
            return String.format("%.1f MB", megabytes);
        }

        return String.format("%.2f GB", megabytes / 1024.0);
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank()
                ? throwable.getClass().getSimpleName()
                : message;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
