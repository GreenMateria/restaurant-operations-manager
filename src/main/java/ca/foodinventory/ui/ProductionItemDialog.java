package ca.foodinventory.ui;

import ca.foodinventory.dao.ProductionStationDao;
import ca.foodinventory.model.ProductionItem;
import ca.foodinventory.model.ProductionStation;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;

import java.util.List;

public class ProductionItemDialog extends Dialog<ProductionItem> {

    private final ProductionStationDao productionStationDao = new ProductionStationDao();

    private final TextField nameField = new TextField();
    private final ComboBox<ProductionStation> stationComboBox = new ComboBox<>();
    private final ComboBox<String> unitComboBox = new ComboBox<>();
    private final TextField printOrderField = new TextField();
    private final CheckBox activeCheckBox = new CheckBox("Active");

    private final ProductionItem existingItem;

    public ProductionItemDialog(ProductionItem existingItem) {
        this.existingItem = existingItem;

        setTitle(existingItem == null ? "Add Production Item" : "Edit Production Item");
        setHeaderText(existingItem == null ? "Create a new production item" : "Update production item");

        buildDialog();
        loadStations();
        loadUnits();
        populateFields();

        setResultConverter(buttonType -> {
            if (buttonType.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                return buildProductionItem();
            }
            return null;
        });
    }

    private void buildDialog() {
        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        nameField.setPromptText("Example: Alfredo Sauce");
        printOrderField.setPromptText("Example: 10");

        stationComboBox.setPrefWidth(260);
        unitComboBox.setPrefWidth(260);

        activeCheckBox.setSelected(true);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));

        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);

        grid.add(new Label("Station:"), 0, 1);
        grid.add(stationComboBox, 1, 1);

        grid.add(new Label("Unit:"), 0, 2);
        grid.add(unitComboBox, 1, 2);

        grid.add(new Label("Print Order:"), 0, 3);
        grid.add(printOrderField, 1, 3);

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

    private void loadStations() {
        List<ProductionStation> stations = productionStationDao.findActive();

        stationComboBox.setItems(FXCollections.observableArrayList(stations));

        stationComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(ProductionStation station) {
                return station == null ? "" : station.getName();
            }

            @Override
            public ProductionStation fromString(String string) {
                return null;
            }
        });

        stationComboBox.setCellFactory(comboBox -> new ListCell<>() {
            @Override
            protected void updateItem(ProductionStation station, boolean empty) {
                super.updateItem(station, empty);
                setText(empty || station == null ? null : station.getName());
            }
        });

        stationComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(ProductionStation station, boolean empty) {
                super.updateItem(station, empty);
                setText(empty || station == null ? null : station.getName());
            }
        });
    }

    private void loadUnits() {
        unitComboBox.setItems(FXCollections.observableArrayList(
                "EA",
                "KG",
                "G",
                "L",
                "ML",
                "PORTION",
                "PAN",
                "TRAY",
                "BAG",
                "BOTTLE"
        ));

        unitComboBox.setEditable(true);
    }

    private void populateFields() {
        if (existingItem == null) {
            printOrderField.setText("0");
            activeCheckBox.setSelected(true);
            return;
        }

        nameField.setText(existingItem.getName());
        unitComboBox.setValue(existingItem.getUnit());
        printOrderField.setText(String.valueOf(existingItem.getPrintOrder()));
        activeCheckBox.setSelected(existingItem.isActive());

        if (existingItem.getStationId() > 0) {
            for (ProductionStation station : stationComboBox.getItems()) {
                if (station.getId() == existingItem.getStationId()) {
                    stationComboBox.setValue(station);
                    break;
                }
            }
        }
    }

    private boolean validate() {
        String name = nameField.getText();
        String unit = getUnitText();
        String printOrder = printOrderField.getText();

        if (name == null || name.trim().isEmpty()) {
            showValidationError("Name is required.");
            return false;
        }

        if (unit == null || unit.trim().isEmpty()) {
            showValidationError("Unit is required.");
            return false;
        }

        if (printOrder == null || printOrder.trim().isEmpty()) {
            showValidationError("Print order is required.");
            return false;
        }

        try {
            Integer.parseInt(printOrder.trim());
        } catch (NumberFormatException e) {
            showValidationError("Print order must be a whole number.");
            return false;
        }

        return true;
    }

    private ProductionItem buildProductionItem() {
        ProductionItem item = existingItem == null ? new ProductionItem() : existingItem;

        ProductionStation selectedStation = stationComboBox.getValue();

        item.setName(nameField.getText().trim());
        item.setUnit(getUnitText().trim().toUpperCase());
        item.setStationId(selectedStation == null ? 0 : selectedStation.getId());
        item.setStationName(selectedStation == null ? null : selectedStation.getName());
        item.setPrintOrder(Integer.parseInt(printOrderField.getText().trim()));
        item.setActive(activeCheckBox.isSelected());

        return item;
    }

    private String getUnitText() {
        String editorText = unitComboBox.getEditor().getText();

        if (editorText != null && !editorText.trim().isEmpty()) {
            return editorText;
        }

        return unitComboBox.getValue();
    }

    private void showValidationError(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Invalid Production Item");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
