package ca.foodinventory.ui;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.database.DatabaseSyncService;
import ca.foodinventory.dao.SettingsDao;
import ca.foodinventory.service.ApiHealthClient;
import ca.foodinventory.service.DatabaseBackupService;
import ca.foodinventory.service.GitHubUpdateService;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.Optional;

public class SystemView {

    private final DatabaseBackupService backupService =
            new DatabaseBackupService();
    private final DatabaseSyncService syncService =
            new DatabaseSyncService();
    private final SettingsDao settingsDao = new SettingsDao();
    private final ApiHealthClient apiHealthClient =
            new ApiHealthClient();
    private final UpdateDialogService updateDialogService =
            new UpdateDialogService(new GitHubUpdateService());
    private final Label databaseModeLabel = new Label();
    private final Label preferredModeLabel = new Label();
    private final Label sqlitePathLabel = new Label();
    private final Label apiStatusLabel = new Label("API Connection: Not checked");
    private final Label syncStatusLabel = new Label();
    private Button backupButton;
    private Button restoreButton;
    private Button downloadButton;
    private Button testApiButton;
    private Button changePasswordButton;
    private Button checkUpdatesButton;

    public VBox getView() {

        VBox root = new VBox(20);
        root.setPadding(new Insets(40));
        root.setAlignment(Pos.TOP_CENTER);

        Label title = new Label("System");
        title.getStyleClass().add("page-title");

        databaseModeLabel.getStyleClass().add("section-title");
        preferredModeLabel.getStyleClass().add("section-title");
        sqlitePathLabel.getStyleClass().add("section-title");
        apiStatusLabel.getStyleClass().add("section-title");
        syncStatusLabel.getStyleClass().add("section-title");

        backupButton = new Button("Create Backup File");
        backupButton.getStyleClass().add("dashboard-button");

        restoreButton = new Button("Restore Local SQLite Backup");
        restoreButton.getStyleClass().add("dashboard-button");

        changePasswordButton = new Button("Change Password");
        changePasswordButton.getStyleClass().add("dashboard-button");

        checkUpdatesButton = new Button("Check for Updates");
        checkUpdatesButton.getStyleClass().add("dashboard-button");

        testApiButton = new Button("Test API Connection");
        testApiButton.getStyleClass().add("dashboard-button");

        downloadButton = new Button("Download Cloud Snapshot");
        downloadButton.getStyleClass().add("dashboard-button");

        backupButton.setOnAction(e -> backupDatabase());

        restoreButton.setOnAction(e -> restoreDatabase());

        testApiButton.setOnAction(e -> testApiConnection());

        downloadButton.setOnAction(e -> downloadFromCloud());

        changePasswordButton.setOnAction(e -> changePassword());

        checkUpdatesButton.setOnAction(e -> checkForUpdates());

        HBox apiButtons = new HBox(15, testApiButton, downloadButton);
        apiButtons.setAlignment(Pos.CENTER);

        refreshDatabaseInfo();

        root.getChildren().addAll(
                title,
                databaseModeLabel,
                preferredModeLabel,
                sqlitePathLabel,
                apiStatusLabel,
                backupButton,
                restoreButton,
                apiButtons,
                syncStatusLabel,
                checkUpdatesButton,
                changePasswordButton
        );

        return root;
    }

    private void backupDatabase() {

        try {

            DirectoryChooser chooser = new DirectoryChooser();

            chooser.setTitle("Select Backup Folder");

            File folder = chooser.showDialog(null);

            if (folder == null) {
                return;
            }

            File backupFile =
                    backupService.backupDatabase(folder);

            Alert alert =
                    new Alert(Alert.AlertType.INFORMATION);

            alert.setTitle("Backup Complete");

            alert.setHeaderText("Database backed up successfully");

            alert.setContentText(
                    backupFile.getAbsolutePath()
            );

            alert.showAndWait();

        } catch (Exception ex) {

            ex.printStackTrace();

            Alert alert =
                    new Alert(Alert.AlertType.ERROR);

            alert.setHeaderText("Backup Failed");

            alert.setContentText(ex.getMessage());

            alert.showAndWait();
        }
    }

    private void restoreDatabase() {

        try {

            FileChooser chooser = new FileChooser();

            chooser.setTitle("Select Database Backup");

            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter(
                            "Database Files",
                            "*.db"
                    )
            );

            File backupFile =
                    chooser.showOpenDialog(null);

            if (backupFile == null) {
                return;
            }

            Alert confirm =
                    new Alert(
                            Alert.AlertType.CONFIRMATION
                    );

            confirm.setTitle("Restore Database");

            confirm.setHeaderText(
                    "Restore database from backup?"
            );

            confirm.setContentText(
                    "This will overwrite the current database."
            );

            if (confirm.showAndWait().orElse(ButtonType.CANCEL)
                    != ButtonType.OK) {

                return;
            }

            backupService.restoreDatabase(backupFile);

            Alert success =
                    new Alert(Alert.AlertType.INFORMATION);

            success.setTitle("Restore Complete");

            success.setHeaderText(
                    "Database restored successfully"
            );

            success.setContentText(
                    "Please restart the application."
            );

            success.showAndWait();

        } catch (Exception ex) {

            ex.printStackTrace();

            Alert alert =
                    new Alert(Alert.AlertType.ERROR);

            alert.setHeaderText("Restore Failed");

            alert.setContentText(ex.getMessage());

            alert.showAndWait();
        }
    }

    private void testApiConnection() {
        runBackgroundAction(
                "Testing API connection...",
                "API connection successful.",
                "API connection failed. Please contact the administrator.",
                () -> {
                    apiHealthClient.testConnection();
                    return null;
                }
        );
    }

    private void downloadFromCloud() {
        if (!confirmTypedAction(
                "Download Cloud Snapshot",
                "This will download the current cloud database into this PC's local SQLite backup file.",
                "DOWNLOAD CLOUD SNAPSHOT"
        )) {
            return;
        }

        runBackgroundAction(
                "Migrating data...",
                "Data migration complete. Please restart the application.",
                "Data migration failed. Please contact the administrator.",
                () -> syncService.downloadCloudToSqlite()
        );
    }

    private boolean confirmTypedAction(
            String title,
            String message,
            String confirmationText
    ) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(title);
        dialog.setHeaderText(message);
        dialog.setContentText("Type " + confirmationText + " to continue:");

        Optional<String> result = dialog.showAndWait();
        return result
                .map(value -> confirmationText.equals(value.trim()))
                .orElse(false);
    }

    private void runBackgroundAction(
            String workingMessage,
            String successMessage,
            String failureMessage,
            MigrationAction action
    ) {
        syncStatusLabel.setText(workingMessage);
        setDatabaseButtonsDisabled(true);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                action.run();
                return null;
            }
        };

        task.setOnSucceeded(event -> {
            setDatabaseButtonsDisabled(false);
            syncStatusLabel.setText(successMessage);
            refreshDatabaseInfo();
            showAlert(Alert.AlertType.INFORMATION, "Complete", successMessage);
        });

        task.setOnFailed(event -> {
            setDatabaseButtonsDisabled(false);
            syncStatusLabel.setText(failureMessage);
            Throwable exception = task.getException();
            if (exception != null) {
                exception.printStackTrace();
            }
            showAlert(
                    Alert.AlertType.ERROR,
                    "Failed",
                    failureMessage + formatFailureDetails(exception)
            );
        });

        Thread thread = new Thread(task, "database-sync");
        thread.setDaemon(true);
        thread.start();
    }

    private String formatFailureDetails(Throwable exception) {
        if (exception == null) {
            return "";
        }

        Throwable rootCause = exception;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }

        String message = rootCause.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getMessage();
        }

        if (message == null || message.isBlank()) {
            return "";
        }

        return "\n\nDetails: " + message;
    }

    private void setDatabaseButtonsDisabled(boolean disabled) {
        backupButton.setDisable(disabled);
        restoreButton.setDisable(disabled);
        downloadButton.setDisable(disabled);
        testApiButton.setDisable(disabled);
        changePasswordButton.setDisable(disabled);
        checkUpdatesButton.setDisable(disabled);
    }

    private void refreshDatabaseInfo() {
        databaseModeLabel.setText(
                "Current Mode: " + DatabaseManager.getActiveDatabaseModeLabel()
        );
        preferredModeLabel.setText(
                "Startup Mode: " + DatabaseManager.getPreferredDatabaseModeLabel()
        );
        sqlitePathLabel.setText(
                "Local Backup Snapshot: " + DatabaseManager.getSqliteDatabaseFile().getAbsolutePath()
        );
        apiStatusLabel.setText(
                DatabaseManager.hasConfiguredApiConnection()
                        ? "API Connection: Configured"
                        : "API Connection: Not configured"
        );
    }

    private void changePassword() {
        PasswordField newPasswordField = new PasswordField();
        PasswordField confirmPasswordField = new PasswordField();

        VBox fields = new VBox(
                10,
                new Label("New Password"),
                newPasswordField,
                new Label("Confirm Password"),
                confirmPasswordField
        );

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Change Password");
        dialog.setHeaderText("Set a new administrator password for this PC.");
        dialog.getDialogPane().setContent(fields);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        String newPassword = newPasswordField.getText();
        if (newPassword == null || newPassword.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Invalid Password", "Password cannot be blank.");
            return;
        }

        if (!newPassword.equals(confirmPasswordField.getText())) {
            showAlert(Alert.AlertType.WARNING, "Invalid Password", "Passwords do not match.");
            return;
        }

        try {
            settingsDao.setPassword(newPassword);
            showAlert(Alert.AlertType.INFORMATION, "Password Updated", "Administrator password updated.");
        } catch (RuntimeException ex) {
            showAlert(Alert.AlertType.ERROR, "Password Update Failed", ex.getMessage());
        }
    }

    private void checkForUpdates() {
        updateDialogService.checkForUpdates(
                checkUpdatesButton.getScene() == null
                        ? null
                        : checkUpdatesButton.getScene().getWindow(),
                true,
                true
        );
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @FunctionalInterface
    private interface MigrationAction {
        Object run();
    }
}
