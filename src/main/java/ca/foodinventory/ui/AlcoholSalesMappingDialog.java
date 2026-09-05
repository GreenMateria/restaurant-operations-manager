package ca.foodinventory.ui;

import ca.foodinventory.dao.ProductDao;
import ca.foodinventory.dao.PosMenuItemDao;
import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.AlcoholSalesMapping;
import ca.foodinventory.model.PosMenuItem;
import ca.foodinventory.model.Product;
import ca.foodinventory.service.PosMenuItemApiClient;
import ca.foodinventory.service.ProductApiClient;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;

import java.util.List;

public class AlcoholSalesMappingDialog extends Dialog<AlcoholSalesMapping> {

    private static final List<String> ALCOHOL_CATEGORIES = List.of(
            "BEER",
            "WINE",
            "DRAUGHT",
            "IMPORT DRAUGHT",
            "LIQUOR"
    );

    private static final List<String> USAGE_UNITS = List.of(
            "EACH",
            "OZ",
            "ML",
            "L",
            "LB"
    );

    private final PosMenuItemDao posMenuItemDao = new PosMenuItemDao();
    private final ProductDao productDao = new ProductDao();
    private final PosMenuItemApiClient posMenuItemApiClient = new PosMenuItemApiClient();
    private final ProductApiClient productApiClient = new ProductApiClient();
    private final AlcoholSalesMapping existingMapping;

    private final ComboBox<PosMenuItem> posMenuItemComboBox = new ComboBox<>();
    private final TextField posSkuField = new TextField();
    private final TextField posItemNameField = new TextField();
    private final ComboBox<String> reportingCategoryBox = new ComboBox<>();
    private final ComboBox<Product> productComboBox = new ComboBox<>();
    private final TextField quantityPerSaleField = new TextField();
    private final ComboBox<String> unitBox = new ComboBox<>();
    private final CheckBox activeCheckBox = new CheckBox("Active");

    public AlcoholSalesMappingDialog(AlcoholSalesMapping existingMapping) {
        this.existingMapping = existingMapping;

        setTitle(existingMapping == null ? "Add Alcohol Sales Mapping" : "Edit Alcohol Sales Mapping");
        setHeaderText("Map a POS item to the alcohol inventory product it consumes.");

        buildDialog();
        loadPosMenuItems();
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

        posSkuField.setPromptText("POS SKU / PLU");
        posItemNameField.setPromptText("POS item name");
        posMenuItemComboBox.setPrefWidth(440);
        reportingCategoryBox.getItems().addAll(ALCOHOL_CATEGORIES);
        productComboBox.setPrefWidth(440);
        quantityPerSaleField.setPromptText("Example: 20");
        unitBox.getItems().addAll(USAGE_UNITS);
        activeCheckBox.setSelected(true);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));

        grid.add(new Label("POS Menu Item:"), 0, 0);
        grid.add(posMenuItemComboBox, 1, 0);
        grid.add(new Label("POS SKU / PLU:"), 0, 1);
        grid.add(posSkuField, 1, 1);
        grid.add(new Label("POS Item Name:"), 0, 2);
        grid.add(posItemNameField, 1, 2);
        grid.add(new Label("Category:"), 0, 3);
        grid.add(reportingCategoryBox, 1, 3);
        grid.add(new Label("Inventory Product:"), 0, 4);
        grid.add(productComboBox, 1, 4);
        grid.add(new Label("Qty Used Per Sale:"), 0, 5);
        grid.add(quantityPerSaleField, 1, 5);
        grid.add(new Label("Usage Unit:"), 0, 6);
        grid.add(unitBox, 1, 6);
        grid.add(new Label("Status:"), 0, 7);
        grid.add(activeCheckBox, 1, 7);

        getDialogPane().setContent(grid);

        Node saveButton = getDialogPane().lookupButton(saveButtonType);
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (!validate()) {
                event.consume();
            }
        });
    }

    private void loadPosMenuItems() {
        List<PosMenuItem> activeItems = DatabaseManager.isApiDatabase()
                ? posMenuItemApiClient.findAll().stream()
                .filter(PosMenuItem::isActive)
                .toList()
                : posMenuItemDao.findActive();

        SearchableComboBoxSupport.makeSearchable(posMenuItemComboBox, activeItems, new StringConverter<>() {
            @Override
            public String toString(PosMenuItem item) {
                if (item == null) {
                    return "";
                }

                String category = item.getCategory() == null || item.getCategory().isBlank()
                        ? "Uncategorized"
                        : item.getCategory();
                return "[" + category + "] " + item.getName() + " (" + item.getPosSku() + ")";
            }

            @Override
            public PosMenuItem fromString(String string) {
                return null;
            }
        });

        posMenuItemComboBox.valueProperty().addListener((obs, oldItem, newItem) -> {
            if (newItem == null) {
                return;
            }

            posSkuField.setText(newItem.getPosSku());
            posItemNameField.setText(newItem.getName());
        });
    }

    private void loadProducts() {
        List<Product> sourceProducts = DatabaseManager.isApiDatabase()
                ? productApiClient.findAllActiveProducts()
                : productDao.getAllActiveProducts();
        List<Product> alcoholProducts = sourceProducts.stream()
                .filter(Product::isAlcoholProduct)
                .toList();

        SearchableComboBoxSupport.makeSearchable(productComboBox, alcoholProducts, new StringConverter<>() {
            @Override
            public String toString(Product product) {
                if (product == null) {
                    return "";
                }

                return "[" + product.getReportingCategory() + "] "
                        + product.getDescription()
                        + " (" + product.getSku() + ")";
            }

            @Override
            public Product fromString(String string) {
                return null;
            }
        });

        productComboBox.valueProperty().addListener((obs, oldProduct, newProduct) -> {
            if (newProduct == null) {
                return;
            }

            reportingCategoryBox.setValue(newProduct.getReportingCategory());
            if (unitBox.getValue() == null) {
                unitBox.setValue(suggestUsageUnit(newProduct));
            }
        });
    }

    private void populateFields() {
        if (existingMapping == null) {
            reportingCategoryBox.setValue("BEER");
            unitBox.setValue("EACH");
            return;
        }

        posSkuField.setText(existingMapping.getPosSku());
        posItemNameField.setText(existingMapping.getPosItemName());
        selectPosMenuItem(existingMapping.getPosSku());
        reportingCategoryBox.setValue(existingMapping.getReportingCategory());
        selectProduct(existingMapping.getProductId());
        quantityPerSaleField.setText(formatNumber(existingMapping.getQuantityPerSale()));
        unitBox.setValue(existingMapping.getUnit());
        activeCheckBox.setSelected(existingMapping.isActive());
    }

    private void selectPosMenuItem(String posSku) {
        if (posSku == null || posSku.isBlank()) {
            return;
        }

        for (PosMenuItem item : posMenuItemComboBox.getItems()) {
            if (posSku.equalsIgnoreCase(item.getPosSku())) {
                posMenuItemComboBox.setValue(item);
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
        if (isBlank(posSkuField.getText())) {
            showValidationError("POS SKU / PLU is required.");
            return false;
        }

        if (reportingCategoryBox.getValue() == null) {
            showValidationError("Category is required.");
            return false;
        }

        if (productComboBox.getValue() == null) {
            showValidationError("Inventory product is required.");
            return false;
        }

        double quantity;
        try {
            quantity = Double.parseDouble(quantityPerSaleField.getText().trim());
        } catch (Exception e) {
            showValidationError("Quantity used per sale must be a number.");
            return false;
        }

        if (quantity <= 0) {
            showValidationError("Quantity used per sale must be greater than zero.");
            return false;
        }

        if (unitBox.getValue() == null || unitBox.getValue().isBlank()) {
            showValidationError("Usage unit is required.");
            return false;
        }

        return true;
    }

    private AlcoholSalesMapping buildMapping() {
        AlcoholSalesMapping mapping = existingMapping == null
                ? new AlcoholSalesMapping()
                : existingMapping;
        Product product = productComboBox.getValue();

        mapping.setPosSku(posSkuField.getText().trim());
        mapping.setPosItemName(blankToNull(posItemNameField.getText()));
        mapping.setReportingCategory(reportingCategoryBox.getValue());
        mapping.setProductId(product.getId());
        mapping.setProductSku(product.getSku());
        mapping.setProductDescription(product.getDescription());
        mapping.setQuantityPerSale(Double.parseDouble(quantityPerSaleField.getText().trim()));
        mapping.setUnit(unitBox.getValue().trim().toUpperCase());
        mapping.setActive(activeCheckBox.isSelected());

        return mapping;
    }

    private String suggestUsageUnit(Product product) {
        String category = product.getReportingCategory();
        return switch (category) {
            case "DRAUGHT", "IMPORT DRAUGHT", "WINE", "LIQUOR" -> "OZ";
            default -> "EACH";
        };
    }

    private String formatNumber(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }

        return String.valueOf(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private void showValidationError(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Invalid Alcohol Sales Mapping");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
