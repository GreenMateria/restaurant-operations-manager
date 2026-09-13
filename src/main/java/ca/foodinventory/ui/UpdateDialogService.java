package ca.foodinventory.ui;

import ca.foodinventory.service.GitHubUpdateService;
import ca.foodinventory.service.GitHubUpdateService.UpdateInfo;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class UpdateDialogService {

    private static final String APP_NAME = "StoreOps Manager";

    private final GitHubUpdateService updateService;

    public UpdateDialogService(GitHubUpdateService updateService) {
        this.updateService = updateService;
    }

    public void checkForUpdates(Window owner, boolean forceCheck, boolean showNoUpdateMessage) {
        updateService.checkForUpdate(forceCheck)
                .thenAccept(optionalUpdate -> Platform.runLater(() -> {
                    if (optionalUpdate.isPresent()) {
                        showUpdateDialog(owner, optionalUpdate.get());
                    } else if (showNoUpdateMessage) {
                        showAlert(
                                owner,
                                Alert.AlertType.INFORMATION,
                                "Check for Updates",
                                "No newer update is available."
                        );
                    }
                }))
                .exceptionally(exception -> {
                    Platform.runLater(() -> showAlert(
                            owner,
                            Alert.AlertType.ERROR,
                            "Update Check Failed",
                            safeMessage(rootCause(exception))
                    ));
                    return null;
                });
    }

    private void showUpdateDialog(Window owner, UpdateInfo updateInfo) {
        Dialog<ButtonType> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
            dialog.initModality(Modality.WINDOW_MODAL);
        }
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

        Label verification = new Label(
                updateInfo.sha256Url() == null || updateInfo.sha256Url().isBlank()
                        ? "Installer verification: checksum not published"
                        : "Installer verification: SHA-256 checksum available"
        );
        verification.setStyle("-fx-text-fill: #202020;");

        VBox versionDetails = new VBox(
                6,
                installedVersion,
                latestVersion,
                downloadSize,
                verification
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
                        APP_NAME + " "
                                + updateInfo.latestVersion()
                                + " is available."
                ),
                versionDetails,
                notesHeading,
                releaseNotes
        );
        content.setPadding(new Insets(8));
        content.setPrefWidth(WindowSizing.width(560));
        content.setMinWidth(Math.min(520, WindowSizing.width(560)));

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
                showAlert(
                        owner,
                        Alert.AlertType.ERROR,
                        "Unable to Open Release",
                        "The GitHub release page could not be opened."
                );
            }
            return;
        }

        showDownloadDialog(owner, updateInfo);
    }

    private void showDownloadDialog(Window owner, UpdateInfo updateInfo) {
        Dialog<Void> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
            dialog.initModality(Modality.WINDOW_MODAL);
        }
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
                        "Downloading " + APP_NAME + " v"
                                + updateInfo.latestVersion()
                ),
                progressBar,
                progressLabel
        );
        content.setPadding(new Insets(10));
        content.setPrefWidth(WindowSizing.width(480));
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
            confirmAndLaunchInstaller(owner, updateInfo, completedInstaller.get());
            return;
        }

        if (downloadFailure.get() != null) {
            showDownloadFailure(owner, updateInfo, downloadFailure.get());
        }
    }

    private void confirmAndLaunchInstaller(
            Window owner,
            UpdateInfo updateInfo,
            Path installerPath
    ) {
        Dialog<ButtonType> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
            dialog.initModality(Modality.WINDOW_MODAL);
        }
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

        Label verificationLabel = new Label(
                updateInfo.sha256Url() == null || updateInfo.sha256Url().isBlank()
                        ? "The installer was downloaded. No checksum was published for this release."
                        : "The installer was downloaded and verified."
        );
        verificationLabel.setWrapText(true);
        verificationLabel.setStyle("-fx-text-fill: #202020;");

        Label messageLabel = new Label(
                "The application will close and the Windows installer "
                        + "will open to complete the update."
        );
        messageLabel.setWrapText(true);
        messageLabel.setStyle("-fx-text-fill: #202020;");

        VBox content = new VBox(
                12,
                createDialogHeading(
                        APP_NAME + " "
                                + updateInfo.latestVersion()
                                + " is ready to install."
                ),
                versionLabel,
                verificationLabel,
                messageLabel
        );
        content.setPadding(new Insets(10));
        content.setPrefWidth(WindowSizing.width(470));
        content.setMinWidth(Math.min(430, WindowSizing.width(470)));

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
            showAlert(
                    owner,
                    Alert.AlertType.ERROR,
                    "Unable to Launch Installer",
                    "The update was downloaded, but Windows could not "
                            + "launch the installer. It is located at:"
                            + System.lineSeparator()
                            + installerPath.toAbsolutePath()
            );
        }
    }

    private void showDownloadFailure(
            Window owner,
            UpdateInfo updateInfo,
            Throwable exception
    ) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.setTitle("Update Download Failed");
        alert.setHeaderText("The update could not be downloaded or verified.");
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

    private void showAlert(Window owner, Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void configureDialogPane(DialogPane pane, double preferredWidth) {
        String stylesheet = getClass().getResource("/style.css") == null
                ? null
                : getClass().getResource("/style.css").toExternalForm();
        if (stylesheet != null && !pane.getStylesheets().contains(stylesheet)) {
            pane.getStylesheets().add(stylesheet);
        }

        if (!pane.getStyleClass().contains("update-dialog")) {
            pane.getStyleClass().add("update-dialog");
        }

        double width = WindowSizing.width(preferredWidth);
        pane.setPrefWidth(width);
        pane.setMinWidth(width);
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
}
