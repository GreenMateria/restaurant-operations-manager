package ca.foodinventory.ui;

import ca.foodinventory.dao.ProductionItemDao;
import ca.foodinventory.dao.ProductionProfileLineDao;
import ca.foodinventory.model.ProductionItem;
import ca.foodinventory.model.ProductionProfile;
import ca.foodinventory.model.ProductionProfileLine;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.List;

public class ProductionProfileDialog extends Dialog<ProductionProfileDialog.Result> {

    private static final String FREEZER_PULL_STATION = "Freezer Pull";

    public record Result(ProductionProfile profile, List<ProductionProfileLine> lines) {
    }

    private final ProductionItemDao productionItemDao = new ProductionItemDao();
    private final ProductionProfileLineDao lineDao = new ProductionProfileLineDao();

    private final TextField nameField = new TextField();
    private final TextField categoryField = new TextField();
    private final CheckBox activeCheckBox = new CheckBox("Active");

    private final TableView<ProductionProfileLine> linesTable = new TableView<>();
    private final ComboBox<ProductionItem> productionItemComboBox = new ComboBox<>();
    private final TextField quantityField = new TextField();
    private final TextField unitField = new TextField();
    private final TextField sortOrderField = new TextField();
    private final CheckBox lineActiveCheckBox = new CheckBox("Active");

    private final ObservableList<ProductionProfileLine> lines = FXCollections.observableArrayList();
    private final ProductionProfile existingProfile;

    public ProductionProfileDialog(ProductionProfile existingProfile) {
        this.existingProfile = existingProfile;

        setTitle(existingProfile == null ? "Add Production Profile" : "Edit Production Profile");
        setHeaderText(existingProfile == null ? "Create a production profile" : "Update production profile");

        buildDialog();
        loadProductionItems();
        populateFields();

        setResultConverter(button -> {
            if (button.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                return new Result(buildProfile(), List.copyOf(lines));
            }

            return null;
        });
    }

    private void buildDialog() {
        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        getDialogPane().setPrefWidth(820);

        nameField.setPromptText("Example: Chicken Parmigiana");
        categoryField.setPromptText("Example: Entrees");
        quantityField.setPromptText("Example: 1.5");
        unitField.setPromptText("Example: PORTION");
        sortOrderField.setPromptText("Example: 10");
        lineActiveCheckBox.setSelected(true);

        GridPane profileGrid = new GridPane();
        profileGrid.setHgap(12);
        profileGrid.setVgap(12);

        profileGrid.add(new Label("Name:"), 0, 0);
        profileGrid.add(nameField, 1, 0);

        profileGrid.add(new Label("Category:"), 0, 1);
        profileGrid.add(categoryField, 1, 1);

        profileGrid.add(new Label("Status:"), 0, 2);
        profileGrid.add(activeCheckBox, 1, 2);

        setupLinesTable();

        Button addLineButton = new Button("Add Line");
        addLineButton.getStyleClass().add("primary-button");
        addLineButton.setOnAction(e -> addLine());

        Button updateLineButton = new Button("Update Line");
        updateLineButton.getStyleClass().add("primary-button");
        updateLineButton.setOnAction(e -> updateSelectedLine());

        Button removeLineButton = new Button("Remove Line");
        removeLineButton.getStyleClass().add("primary-button");
        removeLineButton.setOnAction(e -> removeSelectedLine());

        GridPane lineGrid = new GridPane();
        lineGrid.setHgap(12);
        lineGrid.setVgap(12);

        productionItemComboBox.setPrefWidth(260);

        lineGrid.add(new Label("Production Item:"), 0, 0);
        lineGrid.add(productionItemComboBox, 1, 0);

        lineGrid.add(new Label("Quantity Per Sale:"), 0, 1);
        lineGrid.add(quantityField, 1, 1);

        lineGrid.add(new Label("Unit:"), 2, 1);
        lineGrid.add(unitField, 3, 1);

        lineGrid.add(new Label("Sort Order:"), 0, 2);
        lineGrid.add(sortOrderField, 1, 2);

        lineGrid.add(new Label("Status:"), 2, 2);
        lineGrid.add(lineActiveCheckBox, 3, 2);

        HBox lineButtons = new HBox(10, addLineButton, updateLineButton, removeLineButton);

        VBox content = new VBox(
                16,
                profileGrid,
                new Label("Profile Lines"),
                linesTable,
                lineGrid,
                lineButtons
        );
        content.setPadding(new Insets(20));

        getDialogPane().setContent(content);

        Node saveButton = getDialogPane().lookupButton(saveButtonType);
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (!validateProfile()) {
                event.consume();
            }
        });
    }

    private void setupLinesTable() {
        TableColumn<ProductionProfileLine, String> itemCol = new TableColumn<>("Production Item");
        itemCol.setCellValueFactory(new PropertyValueFactory<>("productionItemName"));

        TableColumn<ProductionProfileLine, Double> quantityCol = new TableColumn<>("Qty Per Sale");
        quantityCol.setCellValueFactory(new PropertyValueFactory<>("quantityPerSale"));

        TableColumn<ProductionProfileLine, String> unitCol = new TableColumn<>("Unit");
        unitCol.setCellValueFactory(new PropertyValueFactory<>("unit"));

        TableColumn<ProductionProfileLine, Integer> sortOrderCol = new TableColumn<>("Sort Order");
        sortOrderCol.setCellValueFactory(new PropertyValueFactory<>("sortOrder"));

        TableColumn<ProductionProfileLine, Boolean> activeCol = new TableColumn<>("Active");
        activeCol.setCellValueFactory(new PropertyValueFactory<>("active"));

        linesTable.getColumns().setAll(
                itemCol,
                quantityCol,
                unitCol,
                sortOrderCol,
                activeCol
        );

        linesTable.setItems(lines);
        linesTable.setPrefHeight(220);
        linesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        linesTable.getSelectionModel().selectedItemProperty().addListener((obs, oldLine, newLine) -> {
            if (newLine != null) {
                populateLineEditor(newLine);
            }
        });
    }

    private void loadProductionItems() {
        SearchableComboBoxSupport.makeSearchable(productionItemComboBox, productionItemDao.findActive(), new StringConverter<>() {
            @Override
            public String toString(ProductionItem item) {
                return item == null ? "" : item.getName();
            }

            @Override
            public ProductionItem fromString(String string) {
                return null;
            }
        });

        productionItemComboBox.valueProperty().addListener((obs, oldItem, newItem) -> {
            if (newItem == null) {
                return;
            }

            if (FREEZER_PULL_STATION.equalsIgnoreCase(newItem.getStationName())
                    || unitField.getText() == null
                    || unitField.getText().isBlank()) {
                unitField.setText(newItem.getUnit());
            }
        });
    }

    private void populateFields() {
        if (existingProfile == null) {
            activeCheckBox.setSelected(true);
            sortOrderField.setText("0");
            return;
        }

        nameField.setText(existingProfile.getName());
        categoryField.setText(existingProfile.getCategory());
        activeCheckBox.setSelected(existingProfile.isActive());

        lines.setAll(lineDao.findByProfileId(existingProfile.getId()));
        sortOrderField.setText(String.valueOf(lines.size() * 10 + 10));
    }

    private void populateLineEditor(ProductionProfileLine line) {
        for (ProductionItem item : productionItemComboBox.getItems()) {
            if (item.getId() == line.getProductionItemId()) {
                productionItemComboBox.setValue(item);
                break;
            }
        }

        quantityField.setText(formatNumber(line.getQuantityPerSale()));
        unitField.setText(line.getUnit());
        sortOrderField.setText(String.valueOf(line.getSortOrder()));
        lineActiveCheckBox.setSelected(line.isActive());
    }

    private void addLine() {
        ProductionProfileLine line = buildLineFromFields();
        if (line == null) {
            return;
        }

        lines.add(line);
        clearLineEditor();
    }

    private void updateSelectedLine() {
        ProductionProfileLine selected = linesTable.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showValidationError("Select a profile line to update.");
            return;
        }

        ProductionProfileLine updated = buildLineFromFields();
        if (updated == null) {
            return;
        }

        int selectedIndex = linesTable.getSelectionModel().getSelectedIndex();
        updated.setId(selected.getId());
        updated.setProfileId(selected.getProfileId());
        lines.set(selectedIndex, updated);
        linesTable.getSelectionModel().select(selectedIndex);
    }

    private void removeSelectedLine() {
        ProductionProfileLine selected = linesTable.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showValidationError("Select a profile line to remove.");
            return;
        }

        lines.remove(selected);
        clearLineEditor();
    }

    private ProductionProfileLine buildLineFromFields() {
        ProductionItem selectedItem = productionItemComboBox.getValue();

        if (selectedItem == null) {
            showValidationError("Production item is required.");
            return null;
        }

        if (quantityField.getText() == null || quantityField.getText().trim().isEmpty()) {
            showValidationError("Quantity per sale is required.");
            return null;
        }

        if (unitField.getText() == null || unitField.getText().trim().isEmpty()) {
            showValidationError("Unit is required.");
            return null;
        }

        if (sortOrderField.getText() == null || sortOrderField.getText().trim().isEmpty()) {
            showValidationError("Sort order is required.");
            return null;
        }

        double quantity;
        int sortOrder;

        try {
            quantity = Double.parseDouble(quantityField.getText().trim());
        } catch (NumberFormatException e) {
            showValidationError("Quantity per sale must be a number.");
            return null;
        }

        try {
            sortOrder = Integer.parseInt(sortOrderField.getText().trim());
        } catch (NumberFormatException e) {
            showValidationError("Sort order must be a whole number.");
            return null;
        }

        ProductionProfileLine line = new ProductionProfileLine();
        line.setProductionItemId(selectedItem.getId());
        line.setProductionItemName(selectedItem.getName());
        line.setQuantityPerSale(quantity);
        line.setUnit(unitField.getText().trim().toUpperCase());
        line.setSortOrder(sortOrder);
        line.setActive(lineActiveCheckBox.isSelected());

        return line;
    }

    private boolean validateProfile() {
        if (nameField.getText() == null || nameField.getText().trim().isEmpty()) {
            showValidationError("Name is required.");
            return false;
        }

        return true;
    }

    private ProductionProfile buildProfile() {
        ProductionProfile profile = existingProfile == null ? new ProductionProfile() : existingProfile;

        profile.setName(nameField.getText().trim());
        profile.setCategory(normalizeOptionalText(categoryField.getText()));
        profile.setActive(activeCheckBox.isSelected());

        return profile;
    }

    private void clearLineEditor() {
        productionItemComboBox.setValue(null);
        quantityField.clear();
        unitField.clear();
        sortOrderField.setText(String.valueOf(lines.size() * 10 + 10));
        lineActiveCheckBox.setSelected(true);
        linesTable.getSelectionModel().clearSelection();
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        return value.trim();
    }

    private String formatNumber(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }

        return String.valueOf(value);
    }

    private void showValidationError(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Invalid Production Profile");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
