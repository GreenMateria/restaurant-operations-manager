package ca.foodinventory.ui;

import ca.foodinventory.service.DatabaseBackupService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;

public class SystemView {

    private final DatabaseBackupService backupService =
            new DatabaseBackupService();

    public VBox getView() {

        VBox root = new VBox(20);
        root.setPadding(new Insets(40));
        root.setAlignment(Pos.TOP_CENTER);

        Label title = new Label("System");
        title.getStyleClass().add("page-title");

        Button backupButton = new Button("Database Backup");
        backupButton.getStyleClass().add("dashboard-button");

        Button restoreButton = new Button("Database Restore");
        restoreButton.getStyleClass().add("dashboard-button");

        Button changePasswordButton = new Button("Change Password");
        changePasswordButton.getStyleClass().add("dashboard-button");

        backupButton.setOnAction(e -> backupDatabase());

        restoreButton.setOnAction(e -> restoreDatabase());

        changePasswordButton.setDisable(true);

        root.getChildren().addAll(
                title,
                backupButton,
                restoreButton,
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
}