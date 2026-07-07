package ca.foodinventory.ui;

import ca.foodinventory.dao.ProductionProfileDao;
import ca.foodinventory.model.PosMenuItem;
import ca.foodinventory.model.ProductionProfile;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;

public class PosMenuItemDialog extends Dialog<PosMenuItem> {

    private final ProductionProfileDao productionProfileDao = new ProductionProfileDao();

    private final TextField posNumberField = new TextField();
    private final TextField nameField = new TextField();
    private final TextField categoryField = new TextField();
    private final ComboBox<ProductionProfile> productionProfileComboBox = new ComboBox<>();
    private final CheckBox activeCheckBox = new CheckBox("Active");

    private final PosMenuItem existingItem;

    public PosMenuItemDialog(PosMenuItem existingItem) {
        this.existingItem = existingItem;

        setTitle(existingItem == null ? "Add POS Menu Item" : "Edit POS Menu Item");
        setHeaderText(existingItem == null ? "Create a POS menu item" : "Update POS menu item");

        buildDialog();
        loadProductionProfiles();
        populateFields();

        setResultConverter(button -> {
            if (button.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                return buildItem();
            }

            return null;
        });
    }

    private void buildDialog() {
        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        posNumberField.setPromptText("Example: 10001");
        nameField.setPromptText("Example: Chicken Parmigiana");
        categoryField.setPromptText("Example: Entrees");
        productionProfileComboBox.setPrefWidth(260);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));

        grid.add(new Label("POS Number:"), 0, 0);
        grid.add(posNumberField, 1, 0);

        grid.add(new Label("Menu Item Name:"), 0, 1);
        grid.add(nameField, 1, 1);

        grid.add(new Label("Category:"), 0, 2);
        grid.add(categoryField, 1, 2);

        Button clearProfileButton = new Button("Clear");
        clearProfileButton.setOnAction(e -> productionProfileComboBox.setValue(null));

        grid.add(new Label("Production Profile:"), 0, 3);
        grid.add(new HBox(8, productionProfileComboBox, clearProfileButton), 1, 3);

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

    private void loadProductionProfiles() {
        productionProfileComboBox.setItems(FXCollections.observableArrayList(productionProfileDao.findAll()));

        productionProfileComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(ProductionProfile profile) {
                return profile == null ? "" : profile.getName();
            }

            @Override
            public ProductionProfile fromString(String string) {
                return null;
            }
        });

        productionProfileComboBox.setCellFactory(comboBox -> new ListCell<>() {
            @Override
            protected void updateItem(ProductionProfile profile, boolean empty) {
                super.updateItem(profile, empty);
                setText(empty || profile == null ? null : profile.getName());
            }
        });

        productionProfileComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(ProductionProfile profile, boolean empty) {
                super.updateItem(profile, empty);
                setText(empty || profile == null ? null : profile.getName());
            }
        });
    }

    private void populateFields() {
        if (existingItem == null) {
            activeCheckBox.setSelected(true);
            return;
        }

        posNumberField.setText(existingItem.getPosSku());
        nameField.setText(existingItem.getName());
        categoryField.setText(existingItem.getCategory());
        activeCheckBox.setSelected(existingItem.isActive());

        for (ProductionProfile profile : productionProfileComboBox.getItems()) {
            if (profile.getId() == existingItem.getProductionProfileId()) {
                productionProfileComboBox.setValue(profile);
                break;
            }
        }
    }

    private boolean validate() {
        if (posNumberField.getText() == null || posNumberField.getText().trim().isEmpty()) {
            showValidationError("POS number is required.");
            return false;
        }

        if (nameField.getText() == null || nameField.getText().trim().isEmpty()) {
            showValidationError("Menu item name is required.");
            return false;
        }

        return true;
    }

    private PosMenuItem buildItem() {
        PosMenuItem item = existingItem == null ? new PosMenuItem() : existingItem;

        item.setPosSku(posNumberField.getText().trim());
        item.setName(nameField.getText().trim());
        item.setCategory(normalizeOptionalText(categoryField.getText()));
        ProductionProfile selectedProfile = productionProfileComboBox.getValue();
        item.setProductionProfileId(selectedProfile == null ? 0 : selectedProfile.getId());
        item.setProductionProfileName(selectedProfile == null ? null : selectedProfile.getName());
        item.setActive(activeCheckBox.isSelected());

        return item;
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        return value.trim();
    }

    private void showValidationError(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Invalid POS Menu Item");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
