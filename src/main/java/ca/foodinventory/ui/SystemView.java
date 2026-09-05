package ca.foodinventory.ui;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.database.DatabaseSyncService;
import ca.foodinventory.service.ApiHealthClient;
import ca.foodinventory.service.DatabaseBackupService;
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
    private final ApiHealthClient apiHealthClient =
            new ApiHealthClient();
    private final Label databaseModeLabel = new Label();
    private final Label preferredModeLabel = new Label();
    private final Label sqlitePathLabel = new Label();
    private final Label cloudStatusLabel = new Label("Cloud Connection: Not checked");
    private final Label apiStatusLabel = new Label("API Connection: Not checked");
    private final Label syncStatusLabel = new Label();
    private Button backupButton;
    private Button restoreButton;
    private Button uploadButton;
    private Button downloadButton;
    private Button testCloudButton;
    private Button testApiButton;
    private Button switchToSqliteButton;
    private Button switchToCloudButton;
    private Button switchToApiButton;

    public VBox getView() {

        VBox root = new VBox(20);
        root.setPadding(new Insets(40));
        root.setAlignment(Pos.TOP_CENTER);

        Label title = new Label("System");
        title.getStyleClass().add("page-title");

        databaseModeLabel.getStyleClass().add("section-title");
        preferredModeLabel.getStyleClass().add("section-title");
        sqlitePathLabel.getStyleClass().add("section-title");
        cloudStatusLabel.getStyleClass().add("section-title");
        apiStatusLabel.getStyleClass().add("section-title");
        syncStatusLabel.getStyleClass().add("section-title");

        backupButton = new Button("Database Backup");
        backupButton.getStyleClass().add("dashboard-button");

        restoreButton = new Button("Database Restore");
        restoreButton.getStyleClass().add("dashboard-button");

        Button changePasswordButton = new Button("Change Password");
        changePasswordButton.getStyleClass().add("dashboard-button");

        testCloudButton = new Button("Test Cloud Connection");
        testCloudButton.getStyleClass().add("dashboard-button");

        testApiButton = new Button("Test API Connection");
        testApiButton.getStyleClass().add("dashboard-button");

        uploadButton = new Button("Upload This PC to Cloud");
        uploadButton.getStyleClass().add("dashboard-button");

        downloadButton = new Button("Download Cloud to This PC");
        downloadButton.getStyleClass().add("dashboard-button");

        switchToSqliteButton = new Button("Use SQLite Mode");
        switchToSqliteButton.getStyleClass().add("dashboard-button");

        switchToCloudButton = new Button("Use Cloud Mode");
        switchToCloudButton.getStyleClass().add("dashboard-button");

        switchToApiButton = new Button("Use API Mode");
        switchToApiButton.getStyleClass().add("dashboard-button");

        backupButton.setOnAction(e -> backupDatabase());

        restoreButton.setOnAction(e -> restoreDatabase());

        testCloudButton.setOnAction(e -> testCloudConnection());

        testApiButton.setOnAction(e -> testApiConnection());

        uploadButton.setOnAction(e -> uploadToCloud());

        downloadButton.setOnAction(e -> downloadFromCloud());

        switchToSqliteButton.setOnAction(e -> switchDatabaseMode("sqlite"));

        switchToCloudButton.setOnAction(e -> switchDatabaseMode("postgres"));

        switchToApiButton.setOnAction(e -> switchDatabaseMode("api"));

        changePasswordButton.setDisable(true);

        HBox cloudButtons = new HBox(15, testCloudButton, uploadButton, downloadButton);
        cloudButtons.setAlignment(Pos.CENTER);

        HBox apiButtons = new HBox(15, testApiButton);
        apiButtons.setAlignment(Pos.CENTER);

        HBox modeButtons = new HBox(15, switchToSqliteButton, switchToCloudButton, switchToApiButton);
        modeButtons.setAlignment(Pos.CENTER);

        refreshDatabaseInfo();

        root.getChildren().addAll(
                title,
                databaseModeLabel,
                preferredModeLabel,
                sqlitePathLabel,
                cloudStatusLabel,
                apiStatusLabel,
                modeButtons,
                backupButton,
                restoreButton,
                cloudButtons,
                apiButtons,
                syncStatusLabel,
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

    private void testCloudConnection() {
        runBackgroundAction(
                "Testing cloud connection...",
                "Cloud connection successful.",
                "Cloud connection failed. Please contact the administrator.",
                () -> {
                    syncService.testCloudConnection();
                    return null;
                }
        );
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

    private void uploadToCloud() {
        if (!confirmTypedAction(
                "Upload This PC to Cloud",
                "This will replace the cloud database with this PC's local data.",
                "UPLOAD TO CLOUD"
        )) {
            return;
        }

        runBackgroundAction(
                "Migrating data...",
                "Data migration complete.",
                "Data migration failed. Please contact the administrator.",
                () -> syncService.uploadSqliteToCloud()
        );
    }

    private void downloadFromCloud() {
        if (!confirmTypedAction(
                "Download Cloud to This PC",
                "This will replace this PC's local database with the cloud data.",
                "DOWNLOAD FROM CLOUD"
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

    private void switchDatabaseMode(String mode) {
        boolean switchingToCloud = "postgres".equals(mode);
        boolean switchingToApi = "api".equals(mode);

        if (switchingToCloud && !DatabaseManager.hasConfiguredPostgresConnection()) {
            showAlert(
                    Alert.AlertType.ERROR,
                    "Cloud Not Configured",
                    "Cloud database settings are not configured."
            );
            return;
        }

        if (switchingToApi && !DatabaseManager.hasConfiguredApiConnection()) {
            showAlert(
                    Alert.AlertType.ERROR,
                    "API Not Configured",
                    "API settings are not configured."
            );
            return;
        }

        String targetLabel = switchingToCloud
                ? "Cloud PostgreSQL"
                : switchingToApi ? "Cloud API" : "Local SQLite";
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Switch Database Mode");
        confirm.setHeaderText("Use " + targetLabel + " after restart?");
        confirm.setContentText("The current session will keep using "
                + DatabaseManager.getActiveDatabaseModeLabel()
                + ". Restart the application after changing modes.");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        try {
            DatabaseManager.setPreferredDatabaseMode(mode);
            refreshDatabaseInfo();
            showAlert(
                    Alert.AlertType.INFORMATION,
                    "Database Mode Updated",
                    "Database mode updated. Please restart the application."
            );
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            showAlert(
                    Alert.AlertType.ERROR,
                    "Mode Switch Failed",
                    ex.getMessage()
            );
        }
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
        uploadButton.setDisable(disabled);
        downloadButton.setDisable(disabled);
        testCloudButton.setDisable(disabled);
        testApiButton.setDisable(disabled);
        switchToSqliteButton.setDisable(disabled);
        switchToCloudButton.setDisable(disabled);
        switchToApiButton.setDisable(disabled);
    }

    private void refreshDatabaseInfo() {
        databaseModeLabel.setText(
                "Current Mode: " + DatabaseManager.getActiveDatabaseModeLabel()
        );
        preferredModeLabel.setText(
                "Next Startup Mode: " + DatabaseManager.getPreferredDatabaseModeLabel()
        );
        sqlitePathLabel.setText(
                "Local Database: " + DatabaseManager.getSqliteDatabaseFile().getAbsolutePath()
        );
        cloudStatusLabel.setText(
                DatabaseManager.hasConfiguredPostgresConnection()
                        ? "Cloud Connection: Configured"
                        : "Cloud Connection: Not configured"
        );
        apiStatusLabel.setText(
                DatabaseManager.hasConfiguredApiConnection()
                        ? "API Connection: Configured"
                        : "API Connection: Not configured"
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
