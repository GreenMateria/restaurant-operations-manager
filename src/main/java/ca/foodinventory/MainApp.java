package ca.foodinventory;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.LocationLoginSession;
import ca.foodinventory.service.AppVersionService;
import ca.foodinventory.service.GitHubUpdateService;
import ca.foodinventory.service.GitHubUpdateService.UpdateInfo;
import ca.foodinventory.service.LocationAuthApiClient;
import ca.foodinventory.ui.MainView;
import ca.foodinventory.ui.WindowSizing;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class MainApp extends Application {

    private static final String APP_NAME = "StoreOps Manager";

    private final GitHubUpdateService updateService =
            new GitHubUpdateService();
    private final LocationAuthApiClient locationAuthClient =
            new LocationAuthApiClient();

    private Stage primaryStage;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;

        Scene scene = WindowSizing.scene(
                buildStartupView("Starting " + APP_NAME + "..."),
                1200,
                700
        );

        scene.getStylesheets().add(
                getClass().getResource("/style.css").toExternalForm()
        );

        configurePrimaryStage(stage, scene);
        stage.show();

        initializeApplication();
    }

    private void configurePrimaryStage(Stage stage, Scene scene) {
        InputStream iconStream =
                getClass().getResourceAsStream("/images/logo.png");

        System.out.println("Icon found: " + (iconStream != null));

        if (iconStream != null) {
            stage.getIcons().add(new Image(iconStream));
        } else {
            System.out.println("Logo not found at: /images/logo.png");
        }

        stage.setTitle(
                APP_NAME + " "
                        + AppVersionService.getDisplayVersion()
        );
        stage.setScene(scene);
        stage.setMaximized(true);
    }

    private void initializeApplication() {
        Task<Void> startupTask = new Task<>() {
            @Override
            protected Void call() {
                DatabaseManager.initializeDatabase();
                return null;
            }
        };

        startupTask.setOnSucceeded(event -> {
            if (DatabaseManager.isApiDatabase()
                    && DatabaseManager.isLocationLoginRequired()) {
                showStoreLoginScreen();
                return;
            }

            showMainApplication();
        });

        startupTask.setOnFailed(event -> showStartupFailure(startupTask.getException()));

        Thread thread = new Thread(startupTask, "app-startup");
        thread.setDaemon(true);
        thread.start();
    }

    private BorderPane buildStartupView(String message) {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-dark");

        ProgressIndicator progress = new ProgressIndicator();
        progress.setMaxSize(72, 72);

        Label title = new Label(APP_NAME);
        title.getStyleClass().add("page-title");

        Label status = new Label(message);
        status.getStyleClass().add("section-title");

        VBox content = new VBox(18, title, progress, status);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(40));

        root.setCenter(content);
        return root;
    }

    private void showStoreLoginScreen() {
        primaryStage.getScene().setRoot(buildStoreLoginView());
    }

    private BorderPane buildStoreLoginView() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-dark");

        Label title = new Label("Store Login");
        title.getStyleClass().add("page-title");

        Label subtitle = new Label("Sign in to this location to continue.");
        subtitle.getStyleClass().add("section-title");

        TextField usernameField = new TextField();
        usernameField.setPromptText("Store username");
        usernameField.setMaxWidth(340);

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Store password");
        passwordField.setMaxWidth(340);

        Label statusLabel = new Label("");
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(430);

        Button loginButton = new Button("Log In");
        loginButton.getStyleClass().add("primary-button");
        Runnable updateLoginButton = () -> loginButton.setDisable(
                usernameField.getText().trim().isEmpty()
                        || passwordField.getText().isEmpty()
        );
        usernameField.textProperty().addListener((obs, oldText, newText) -> updateLoginButton.run());
        passwordField.textProperty().addListener((obs, oldText, newText) -> updateLoginButton.run());
        updateLoginButton.run();

        Button closeButton = new Button("Close");
        closeButton.setOnAction(event -> Platform.exit());

        HBox buttons = new HBox(10, closeButton, loginButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox panel = new VBox(
                14,
                title,
                subtitle,
                new Label("Username"),
                usernameField,
                new Label("Password"),
                passwordField,
                statusLabel,
                buttons
        );
        panel.setPadding(new Insets(28));
        panel.setMaxWidth(480);
        panel.setStyle(
                "-fx-background-color: #252525;"
                        + "-fx-border-color: #404040;"
                        + "-fx-border-width: 1;"
                        + "-fx-background-radius: 8;"
                        + "-fx-border-radius: 8;"
        );

        VBox wrapper = new VBox(panel);
        wrapper.setAlignment(Pos.CENTER);
        wrapper.setPadding(new Insets(40));
        root.setCenter(wrapper);

        loginButton.setOnAction(event -> attemptStoreLogin(
                usernameField,
                passwordField,
                loginButton,
                closeButton,
                statusLabel
        ));
        passwordField.setOnAction(event -> {
            if (!loginButton.isDisabled()) {
                loginButton.fire();
            }
        });

        Platform.runLater(usernameField::requestFocus);
        return root;
    }

    private void attemptStoreLogin(
            TextField usernameField,
            PasswordField passwordField,
            Button loginButton,
            Button closeButton,
            Label statusLabel
    ) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        statusLabel.setText("Signing in...");
        usernameField.setDisable(true);
        passwordField.setDisable(true);
        loginButton.setDisable(true);
        closeButton.setDisable(true);

        Task<LocationLoginSession> loginTask = new Task<>() {
            @Override
            protected LocationLoginSession call() {
                return locationAuthClient.login(username, password);
            }
        };

        loginTask.setOnSucceeded(event -> {
            LocationLoginSession session = loginTask.getValue();
            DatabaseManager.saveLocationSession(
                    session.token(),
                    session.locationCode(),
                    session.locationName()
            );
            showMainApplication();
        });

        loginTask.setOnFailed(event -> {
            statusLabel.setText(storeLoginFailureMessage(loginTask.getException()));
            usernameField.setDisable(false);
            passwordField.setDisable(false);
            loginButton.setDisable(false);
            closeButton.setDisable(false);
            passwordField.clear();
            passwordField.requestFocus();
        });

        Thread thread = new Thread(loginTask, "store-login");
        thread.setDaemon(true);
        thread.start();
    }

    private void showMainApplication() {
        MainView mainView = new MainView(this::switchStore);
        primaryStage.getScene().setRoot(mainView.getView());
        checkForUpdates();
    }

    private void switchStore() {
        DatabaseManager.clearLocationSession();
        showStoreLoginScreen();
    }

    private void showStartupFailure(Throwable exception) {
        primaryStage.getScene().setRoot(buildStartupView("Startup failed."));
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(primaryStage);
        alert.setTitle("Startup Failed");
        alert.setHeaderText(APP_NAME + " could not start.");
        alert.setContentText(safeMessage(rootCause(exception)));
        alert.showAndWait();
        Platform.exit();
    }

    private String storeLoginFailureMessage(Throwable throwable) {
        Throwable current = rootCause(throwable);

        if (current instanceof IllegalArgumentException) {
            return current.getMessage();
        }

        if (current instanceof HttpTimeoutException || current instanceof ConnectException) {
            return "The login server did not respond. Check the internet connection and try again.";
        }

        if (current instanceof IOException) {
            return "The app could not reach the login server. Check the internet connection and try again.";
        }

        return safeMessage(current) + "\n\nIf this keeps happening, contact the administrator.";
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
                        APP_NAME + " "
                                + updateInfo.latestVersion()
                                + " is ready to install."
                ),
                versionLabel,
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

    public static void main(String[] args) {
        launch(args);
    }
}
