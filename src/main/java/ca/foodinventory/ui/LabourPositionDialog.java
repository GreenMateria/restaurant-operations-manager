package ca.foodinventory.ui;

import ca.foodinventory.model.LabourPosition;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.math.BigDecimal;

public class LabourPositionDialog extends Dialog<LabourPosition> {

    private final TextField nameField = new TextField();
    private final ComboBox<String> labourGroupComboBox = new ComboBox<>();
    private final TextField sortOrderField = new TextField();
    private final TextField targetPercentageField = new TextField();
    private final CheckBox activeCheckBox = new CheckBox("Active");

    private final LabourPosition existingPosition;

    public LabourPositionDialog(LabourPosition existingPosition) {
        this.existingPosition = existingPosition;

        setTitle(existingPosition == null ? "Add Labour Position" : "Edit Labour Position");
        setHeaderText(existingPosition == null ? "Create a labour position" : "Update labour position");

        buildDialog();
        populateFields();

        setResultConverter(button -> {
            if (button.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                return buildPosition();
            }
            return null;
        });
    }

    private void buildDialog() {
        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        nameField.setPromptText("Example: Server");
        labourGroupComboBox.setItems(FXCollections.observableArrayList("FOH", "BOH"));
        labourGroupComboBox.setPrefWidth(220);
        sortOrderField.setPromptText("Example: 10");
        targetPercentageField.setPromptText("Blank if not used");
        activeCheckBox.setSelected(true);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));

        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Group:"), 0, 1);
        grid.add(labourGroupComboBox, 1, 1);
        grid.add(new Label("Sort Order:"), 0, 2);
        grid.add(sortOrderField, 1, 2);
        grid.add(new Label("Target %:"), 0, 3);
        grid.add(targetPercentageField, 1, 3);
        grid.add(new Label("Status:"), 0, 4);
        grid.add(activeCheckBox, 1, 4);

        getDialogPane().setContent(grid);

        Node saveButton = getDialogPane().lookupButton(saveButtonType);
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (!validate()) {
                event.consume();
            }
        });
    }

    private void populateFields() {
        if (existingPosition == null) {
            labourGroupComboBox.setValue("FOH");
            sortOrderField.setText("0");
            return;
        }

        nameField.setText(existingPosition.getName());
        labourGroupComboBox.setValue(existingPosition.getLabourGroup());
        sortOrderField.setText(String.valueOf(existingPosition.getSortOrder()));
        targetPercentageField.setText(existingPosition.getTargetLabourPercentage() == null
                ? ""
                : existingPosition.getTargetLabourPercentage().toPlainString());
        activeCheckBox.setSelected(existingPosition.isActive());
    }

    private boolean validate() {
        if (nameField.getText() == null || nameField.getText().trim().isEmpty()) {
            showValidationError("Name is required.");
            return false;
        }
        if (labourGroupComboBox.getValue() == null || labourGroupComboBox.getValue().isBlank()) {
            showValidationError("Group is required.");
            return false;
        }
        if (!"FOH".equals(labourGroupComboBox.getValue())
                && !"BOH".equals(labourGroupComboBox.getValue())) {
            showValidationError("Group must be FOH or BOH.");
            return false;
        }
        try {
            Integer.parseInt(sortOrderField.getText().trim());
        } catch (Exception e) {
            showValidationError("Sort order must be a whole number.");
            return false;
        }
        try {
            BigDecimal percentage = parseOptionalDecimal(targetPercentageField.getText());
            if (percentage != null
                    && (percentage.compareTo(BigDecimal.ZERO) < 0
                    || percentage.compareTo(BigDecimal.valueOf(100)) > 0)) {
                showValidationError("Target percentage must be between 0 and 100.");
                return false;
            }
        } catch (NumberFormatException e) {
            showValidationError("Target percentage must be a number.");
            return false;
        }
        return true;
    }

    private LabourPosition buildPosition() {
        LabourPosition position = existingPosition == null ? new LabourPosition() : existingPosition;
        position.setName(nameField.getText().trim());
        position.setLabourGroup(labourGroupComboBox.getValue());
        position.setSortOrder(Integer.parseInt(sortOrderField.getText().trim()));
        position.setTargetLabourPercentage(parseOptionalDecimal(targetPercentageField.getText()));
        position.setActive(activeCheckBox.isSelected());
        return position;
    }

    private BigDecimal parseOptionalDecimal(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return new BigDecimal(value.trim());
    }

    private void showValidationError(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Invalid Labour Position");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
