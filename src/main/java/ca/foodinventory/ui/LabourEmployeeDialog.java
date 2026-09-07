package ca.foodinventory.ui;

import ca.foodinventory.model.LabourEmployee;
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
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.util.List;

public class LabourEmployeeDialog extends Dialog<LabourEmployee> {

    private final TextField nameField = new TextField();
    private final ComboBox<LabourPosition> positionComboBox = new ComboBox<>();
    private final TextField hourlyWageField = new TextField();
    private final CheckBox tipPoolEligibleCheckBox = new CheckBox("Tip-pool eligible");
    private final CheckBox uniformDeductionCheckBox = new CheckBox("Uniform deduction applicable");
    private final CheckBox activeCheckBox = new CheckBox("Active");

    private final LabourEmployee existingEmployee;
    private final List<LabourPosition> positions;

    public LabourEmployeeDialog(
            LabourEmployee existingEmployee,
            List<LabourPosition> positions
    ) {
        this.existingEmployee = existingEmployee;
        this.positions = positions;

        setTitle(existingEmployee == null ? "Add Labour Employee" : "Edit Labour Employee");
        setHeaderText(existingEmployee == null ? "Create an employee" : "Update employee");

        buildDialog();
        loadPositions();
        populateFields();

        setResultConverter(button -> {
            if (button.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                return buildEmployee();
            }
            return null;
        });
    }

    private void buildDialog() {
        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        nameField.setPromptText("Employee name");
        hourlyWageField.setPromptText("Example: 17.20");
        positionComboBox.setPrefWidth(280);
        activeCheckBox.setSelected(true);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));

        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Position:"), 0, 1);
        grid.add(positionComboBox, 1, 1);
        grid.add(new Label("Hourly Wage:"), 0, 2);
        grid.add(hourlyWageField, 1, 2);
        grid.add(new Label("Tip Pool:"), 0, 3);
        grid.add(tipPoolEligibleCheckBox, 1, 3);
        grid.add(new Label("Uniform:"), 0, 4);
        grid.add(uniformDeductionCheckBox, 1, 4);
        grid.add(new Label("Status:"), 0, 5);
        grid.add(activeCheckBox, 1, 5);

        getDialogPane().setContent(grid);

        Node saveButton = getDialogPane().lookupButton(saveButtonType);
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (!validate()) {
                event.consume();
            }
        });
    }

    private void loadPositions() {
        positionComboBox.setItems(FXCollections.observableArrayList(positions));
        positionComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(LabourPosition position) {
                return position == null ? "" : position.getName();
            }

            @Override
            public LabourPosition fromString(String string) {
                return null;
            }
        });
        positionComboBox.setCellFactory(comboBox -> positionCell());
        positionComboBox.setButtonCell(positionCell());
    }

    private ListCell<LabourPosition> positionCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(LabourPosition position, boolean empty) {
                super.updateItem(position, empty);
                if (empty || position == null) {
                    setText(null);
                } else {
                    setText(position.getName() + " (" + position.getLabourGroup() + ")");
                }
            }
        };
    }

    private void populateFields() {
        if (existingEmployee == null) {
            hourlyWageField.setText("0.00");
            return;
        }

        nameField.setText(existingEmployee.getName());
        hourlyWageField.setText(existingEmployee.getHourlyWage() == null
                ? "0.00"
                : existingEmployee.getHourlyWage().toPlainString());
        tipPoolEligibleCheckBox.setSelected(existingEmployee.isTipPoolEligible());
        uniformDeductionCheckBox.setSelected(existingEmployee.isUniformDeductionApplicable());
        activeCheckBox.setSelected(existingEmployee.isActive());

        for (LabourPosition position : positions) {
            if (position.getId() == existingEmployee.getPositionId()) {
                positionComboBox.setValue(position);
                break;
            }
        }
    }

    private boolean validate() {
        if (nameField.getText() == null || nameField.getText().trim().isEmpty()) {
            showValidationError("Name is required.");
            return false;
        }
        if (positionComboBox.getValue() == null) {
            showValidationError("Position is required.");
            return false;
        }
        try {
            BigDecimal wage = parseMoney(hourlyWageField.getText());
            if (wage.compareTo(BigDecimal.ZERO) < 0) {
                showValidationError("Hourly wage cannot be negative.");
                return false;
            }
        } catch (NumberFormatException e) {
            showValidationError("Hourly wage must be a number.");
            return false;
        }
        return true;
    }

    private LabourEmployee buildEmployee() {
        LabourEmployee employee = existingEmployee == null ? new LabourEmployee() : existingEmployee;
        LabourPosition position = positionComboBox.getValue();

        employee.setName(nameField.getText().trim());
        employee.setPositionId(position.getId());
        employee.setPositionName(position.getName());
        employee.setLabourGroup(position.getLabourGroup());
        employee.setHourlyWage(parseMoney(hourlyWageField.getText()));
        employee.setTipPoolEligible(tipPoolEligibleCheckBox.isSelected());
        employee.setUniformDeductionApplicable(uniformDeductionCheckBox.isSelected());
        employee.setActive(activeCheckBox.isSelected());
        return employee;
    }

    private BigDecimal parseMoney(String value) {
        if (value == null || value.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.trim());
    }

    private void showValidationError(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Invalid Labour Employee");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
