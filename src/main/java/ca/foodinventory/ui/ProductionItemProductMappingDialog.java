package ca.foodinventory.ui;

import ca.foodinventory.dao.ProductDao;
import ca.foodinventory.dao.ProductionItemDao;
import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.Product;
import ca.foodinventory.model.ProductionItem;
import ca.foodinventory.model.ProductionItemProductMapping;
import ca.foodinventory.service.ProductApiClient;
import ca.foodinventory.service.ProductionApiClient;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;

public class ProductionItemProductMappingDialog extends Dialog<ProductionItemProductMapping> {

    private final ProductionItemDao productionItemDao = new ProductionItemDao();
    private final ProductDao productDao = new ProductDao();
    private final ProductionApiClient productionApiClient = new ProductionApiClient();
    private final ProductApiClient productApiClient = new ProductApiClient();

    private final ComboBox<ProductionItem> productionItemComboBox = new ComboBox<>();
    private final ComboBox<Product> productComboBox = new ComboBox<>();
    private final TextField quantityField = new TextField();
    private final TextField unitField = new TextField();
    private final CheckBox activeCheckBox = new CheckBox("Active");

    private final ProductionItemProductMapping existingMapping;

    public ProductionItemProductMappingDialog(ProductionItemProductMapping existingMapping) {
        this.existingMapping = existingMapping;

        setTitle(existingMapping == null ? "Add Product Mapping" : "Edit Product Mapping");
        setHeaderText(existingMapping == null
                ? "Map a production item to an inventory product"
                : "Update product mapping");

        buildDialog();
        loadProductionItems();
        loadProducts();
        populateFields();

        setResultConverter(button -> {
            if (button.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                return buildMapping();
            }

            return null;
        });
    }

    private void buildDialog() {
        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        productionItemComboBox.setPrefWidth(300);
        productComboBox.setPrefWidth(420);
        quantityField.setPromptText("Example: 2.5");
        unitField.setPromptText("Example: LB");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));

        grid.add(new Label("Production Item:"), 0, 0);
        grid.add(productionItemComboBox, 1, 0);

        grid.add(new Label("Inventory Product:"), 0, 1);
        grid.add(productComboBox, 1, 1);

        grid.add(new Label("Quantity Per Unit:"), 0, 2);
        grid.add(quantityField, 1, 2);

        grid.add(new Label("Unit:"), 0, 3);
        grid.add(unitField, 1, 3);

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

    private void loadProductionItems() {
        java.util.List<ProductionItem> productionItems = DatabaseManager.isApiDatabase()
                ? productionApiClient.findActiveProductionItems()
                : productionItemDao.findActive();

        SearchableComboBoxSupport.makeSearchable(productionItemComboBox, productionItems, new StringConverter<>() {
            @Override
            public String toString(ProductionItem item) {
                return item == null ? "" : item.getName();
            }

            @Override
            public ProductionItem fromString(String string) {
                return null;
            }
        });
    }

    private void loadProducts() {
        java.util.List<Product> products = DatabaseManager.isApiDatabase()
                ? productApiClient.findAllActiveProducts()
                : productDao.getAllActiveProducts();

        SearchableComboBoxSupport.makeSearchable(productComboBox, products, new StringConverter<>() {
            @Override
            public String toString(Product product) {
                if (product == null) {
                    return "";
                }

                return product.getDescription() + " (" + product.getSku() + ")";
            }

            @Override
            public Product fromString(String string) {
                return null;
            }
        });

        productComboBox.valueProperty().addListener((obs, oldProduct, newProduct) -> {
            if (newProduct != null && (unitField.getText() == null || unitField.getText().isBlank())) {
                unitField.setText(newProduct.getUnit());
            }
        });
    }

    private void populateFields() {
        if (existingMapping == null) {
            activeCheckBox.setSelected(true);
            return;
        }

        selectProductionItem(existingMapping.getProductionItemId());
        selectProduct(existingMapping.getProductId());
        quantityField.setText(formatNumber(existingMapping.getQuantityPerUnit()));
        unitField.setText(existingMapping.getUnit());
        activeCheckBox.setSelected(existingMapping.isActive());
    }

    private void selectProductionItem(int productionItemId) {
        for (ProductionItem item : productionItemComboBox.getItems()) {
            if (item.getId() == productionItemId) {
                productionItemComboBox.setValue(item);
                return;
            }
        }
    }

    private void selectProduct(int productId) {
        for (Product product : productComboBox.getItems()) {
            if (product.getId() == productId) {
                productComboBox.setValue(product);
                return;
            }
        }
    }

    private boolean validate() {
        if (productionItemComboBox.getValue() == null) {
            showValidationError("Production item is required.");
            return false;
        }

        if (productComboBox.getValue() == null) {
            showValidationError("Inventory product is required.");
            return false;
        }

        if (quantityField.getText() == null || quantityField.getText().trim().isEmpty()) {
            showValidationError("Quantity per unit is required.");
            return false;
        }

        try {
            Double.parseDouble(quantityField.getText().trim());
        } catch (NumberFormatException e) {
            showValidationError("Quantity per unit must be a number.");
            return false;
        }

        if (unitField.getText() == null || unitField.getText().trim().isEmpty()) {
            showValidationError("Unit is required.");
            return false;
        }

        return true;
    }

    private ProductionItemProductMapping buildMapping() {
        ProductionItemProductMapping mapping = existingMapping == null
                ? new ProductionItemProductMapping()
                : existingMapping;

        ProductionItem selectedItem = productionItemComboBox.getValue();
        Product selectedProduct = productComboBox.getValue();

        mapping.setProductionItemId(selectedItem.getId());
        mapping.setProductionItemName(selectedItem.getName());
        mapping.setProductId(selectedProduct.getId());
        mapping.setProductSku(selectedProduct.getSku());
        mapping.setProductDescription(selectedProduct.getDescription());
        mapping.setQuantityPerUnit(Double.parseDouble(quantityField.getText().trim()));
        mapping.setUnit(unitField.getText().trim().toUpperCase());
        mapping.setActive(activeCheckBox.isSelected());

        return mapping;
    }

    private String formatNumber(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }

        return String.valueOf(value);
    }

    private void showValidationError(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Invalid Product Mapping");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
