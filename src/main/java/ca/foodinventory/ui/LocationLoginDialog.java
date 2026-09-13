package ca.foodinventory.ui;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.LocationLoginSession;
import ca.foodinventory.service.LocationAuthApiClient;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.util.Optional;

public class LocationLoginDialog {

    private final Stage owner;
    private final LocationAuthApiClient authClient = new LocationAuthApiClient();

    public LocationLoginDialog(Stage owner) {
        this.owner = owner;
    }

    public boolean showAndLogin() {
        while (true) {
            Optional<LoginInput> input = showDialog();
            if (input.isEmpty()) {
                return false;
            }

            try {
                LocationLoginSession session = authClient.login(
                        input.get().username(),
                        input.get().password()
                );
                DatabaseManager.saveLocationSession(
                        session.token(),
                        session.locationCode(),
                        session.locationName()
                );
                return true;
            } catch (Exception e) {
                showLoginError(rootCauseMessage(e));
            }
        }
    }

    private Optional<LoginInput> showDialog() {
        Dialog<LoginInput> dialog = new Dialog<>();
        initOwnerIfReady(dialog);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Store Login");
        dialog.setHeaderText(null);

        ButtonType loginButtonType =
                new ButtonType("Log In", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(loginButtonType, ButtonType.CANCEL);

        Label title = new Label("Store Login");
        title.getStyleClass().add("page-title");

        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");
        usernameField.setPrefColumnCount(22);

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.setPrefColumnCount(22);

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.add(new Label("Username"), 0, 0);
        form.add(usernameField, 1, 0);
        form.add(new Label("Password"), 0, 1);
        form.add(passwordField, 1, 1);

        VBox content = new VBox(14, title, form);
        content.setPadding(new Insets(16));
        content.setAlignment(Pos.CENTER_LEFT);
        content.getStyleClass().add("root-dark");

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(WindowSizing.width(430));
        String stylesheet = getClass().getResource("/style.css") == null
                ? null
                : getClass().getResource("/style.css").toExternalForm();
        if (stylesheet != null) {
            dialog.getDialogPane().getStylesheets().add(stylesheet);
        }

        Button loginButton = (Button) dialog.getDialogPane().lookupButton(loginButtonType);
        loginButton.getStyleClass().add("primary-button");
        loginButton.disableProperty().bind(
                usernameField.textProperty().isEmpty()
                        .or(passwordField.textProperty().isEmpty())
        );

        dialog.setResultConverter(button -> {
            if (button == loginButtonType) {
                return new LoginInput(
                        usernameField.getText().trim(),
                        passwordField.getText()
                );
            }
            return null;
        });

        usernameField.requestFocus();
        return dialog.showAndWait();
    }

    private void showLoginError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        initOwnerIfReady(alert);
        alert.setTitle("Store Login Failed");
        alert.setHeaderText("The store could not be signed in.");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void initOwnerIfReady(Dialog<?> dialog) {
        if (owner != null && owner.getScene() != null) {
            dialog.initOwner(owner);
        }
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }

        if (current instanceof IllegalArgumentException) {
            return current.getMessage();
        }

        if (current instanceof HttpTimeoutException || current instanceof ConnectException) {
            return "The login server did not respond. Please check the internet connection and try again.";
        }

        if (current instanceof IOException) {
            return "The app could not reach the login server. Please check the internet connection and try again.";
        }

        String detail = current == null || current.getMessage() == null || current.getMessage().isBlank()
                ? "Store login failed."
                : current.getMessage();
        return detail + "\n\nIf this keeps happening, contact the administrator.";
    }

    private record LoginInput(String username, String password) {
    }
}
