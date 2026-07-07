package ca.foodinventory.ui;

import ca.foodinventory.model.ProductionStation;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

public class ProductionStationDialog extends Dialog<ProductionStation> {

    private final TextField nameField = new TextField();
    private final TextField sortOrderField = new TextField();
    private final CheckBox activeCheckBox = new CheckBox("Active");

    private final ProductionStation existingStation;

    public ProductionStationDialog(ProductionStation existingStation) {
        this.existingStation = existingStation;

        setTitle(existingStation == null ? "Add Production Station" : "Edit Production Station");
        setHeaderText(existingStation == null ? "Create a production station" : "Update production station");

        buildDialog();
        populateFields();

        setResultConverter(button -> {
            if (button.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                return buildStation();
            }

            return null;
        });
    }

    private void buildDialog() {
        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        nameField.setPromptText("Example: Line");
        sortOrderField.setPromptText("Example: 10");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));

        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);

        grid.add(new Label("Sort Order:"), 0, 1);
        grid.add(sortOrderField, 1, 1);

        grid.add(new Label("Status:"), 0, 2);
        grid.add(activeCheckBox, 1, 2);

        getDialogPane().setContent(grid);

        Node saveButton = getDialogPane().lookupButton(saveButtonType);
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (!validate()) {
                event.consume();
            }
        });
    }

    private void populateFields() {
        if (existingStation == null) {
            sortOrderField.setText("0");
            activeCheckBox.setSelected(true);
            return;
        }

        nameField.setText(existingStation.getName());
        sortOrderField.setText(String.valueOf(existingStation.getSortOrder()));
        activeCheckBox.setSelected(existingStation.isActive());
    }

    private boolean validate() {
        if (nameField.getText() == null || nameField.getText().trim().isEmpty()) {
            showValidationError("Name is required.");
            return false;
        }

        if (sortOrderField.getText() == null || sortOrderField.getText().trim().isEmpty()) {
            showValidationError("Sort order is required.");
            return false;
        }

        try {
            Integer.parseInt(sortOrderField.getText().trim());
        } catch (NumberFormatException e) {
            showValidationError("Sort order must be a whole number.");
            return false;
        }

        return true;
    }

    private ProductionStation buildStation() {
        ProductionStation station =
                existingStation == null ? new ProductionStation() : existingStation;

        station.setName(nameField.getText().trim());
        station.setSortOrder(Integer.parseInt(sortOrderField.getText().trim()));
        station.setActive(activeCheckBox.isSelected());

        return station;
    }

    private void showValidationError(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Invalid Production Station");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}