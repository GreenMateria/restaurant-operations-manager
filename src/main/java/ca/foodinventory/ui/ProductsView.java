package ca.foodinventory.ui;

import ca.foodinventory.dao.InvoiceDao;
import ca.foodinventory.dao.ProductDao;
import ca.foodinventory.model.Product;
import ca.foodinventory.model.PurchaseHistory;
import ca.foodinventory.service.GfsProductImportService;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class ProductsView {

    private final ProductDao productDao = new ProductDao();
    private final InvoiceDao invoiceDao = new InvoiceDao();
    private final GfsProductImportService importService = new GfsProductImportService();

    private final TableView<Product> table = new TableView<>();
    private final TableView<PurchaseHistory> historyTable = new TableView<>();

    public BorderPane getView() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-dark");

        String titleText = switch (department) {
            case "FOOD" -> "Food Products";
            case "ALCOHOL" -> "Alcohol Products";
            case "SUPPLIES" -> "Supplies Products";
            default -> "Products";
        };

        Label title = new Label(titleText);
        title.getStyleClass().add("page-title");

        Button addButton = new Button("Add Product");
        addButton.getStyleClass().add("primary-button");
        addButton.setOnAction(e -> openAddProductDialog());

        Button editButton = new Button("Edit Product");
        editButton.getStyleClass().add("primary-button");
        editButton.setOnAction(e -> editSelectedProduct());

        Button importButton = new Button("Import GFS Order Guide");
        importButton.getStyleClass().add("primary-button");
        importButton.setOnAction(e -> importGfsOrderGuide());

        Button deactivateButton = new Button("Deactivate");
        deactivateButton.getStyleClass().add("primary-button");
        deactivateButton.setOnAction(e -> deactivateSelectedProduct());

        HBox topBar = new HBox(15, title, addButton, editButton, importButton, deactivateButton);

        setupTable();
        setupHistoryTable();
        loadProducts();

        table.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldProduct, selectedProduct) -> {
                    if (selectedProduct != null) {
                        historyTable.setItems(FXCollections.observableArrayList(
                                invoiceDao.findPurchaseHistory(selectedProduct.getId())
                        ));
                    }
                });

        Label historyTitle = new Label("Purchase History");
        historyTitle.getStyleClass().add("section-title");

        VBox centerContent = new VBox(10, table, historyTitle, historyTable);
        centerContent.getStyleClass().add("content-area");

        root.setTop(topBar);
        root.setCenter(centerContent);

        return root;
    }
    private final String department;

    public ProductsView() {
        this.department = null;
    }

    public ProductsView(String department) {
        this.department = department;
    }
    private void setupTable() {
        TableColumn<Product, String> skuCol = new TableColumn<>("SKU");
        skuCol.setCellValueFactory(new PropertyValueFactory<>("sku"));

        TableColumn<Product, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        descCol.setPrefWidth(250);

        TableColumn<Product, String> catCol = new TableColumn<>("Category");
        catCol.setCellValueFactory(new PropertyValueFactory<>("category"));

        TableColumn<Product, String> reportingCatCol = new TableColumn<>("Reporting Category");
        reportingCatCol.setCellValueFactory(new PropertyValueFactory<>("reportingCategory"));

        TableColumn<Product, String> alcoholSetupCol = new TableColumn<>("Alcohol Setup");
        alcoholSetupCol.setCellValueFactory(cellData -> {
            Product product = cellData.getValue();

            if (!product.isAlcoholProduct()) {
                return new javafx.beans.property.SimpleStringProperty("—");
            }

            if (product.hasAlcoholProfile()) {
                return new javafx.beans.property.SimpleStringProperty("✓ Complete");
            }

            return new javafx.beans.property.SimpleStringProperty("⚠ Needs Setup");
        });

        TableColumn<Product, String> unitCol = new TableColumn<>("Unit");
        unitCol.setCellValueFactory(new PropertyValueFactory<>("unit"));

        TableColumn<Product, String> packCountCol = new TableColumn<>("Pack Count");
        packCountCol.setCellValueFactory(new PropertyValueFactory<>("packCount"));

        TableColumn<Product, String> packSizeCol = new TableColumn<>("Pack Size");
        packSizeCol.setCellValueFactory(new PropertyValueFactory<>("packSize"));

        TableColumn<Product, BigDecimal> lastCaseCostCol = new TableColumn<>("Last Case Cost");
        lastCaseCostCol.setCellValueFactory(new PropertyValueFactory<>("lastCaseCost"));
        lastCaseCostCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : formatMoney(value));
            }
        });

        TableColumn<Product, String> lastPurchasedCol = new TableColumn<>("Last Purchased");
        lastPurchasedCol.setCellValueFactory(new PropertyValueFactory<>("lastPurchasedDate"));

        TableColumn<Product, Double> convCol = new TableColumn<>("Units Per Purchased Unit");
        convCol.setCellValueFactory(new PropertyValueFactory<>("conversionFactor"));

        TableColumn<Product, String> activeCol = new TableColumn<>("Active");
        activeCol.setCellValueFactory(new PropertyValueFactory<>("activeText"));

        table.getColumns().setAll(
                skuCol,
                descCol,
                catCol,
                reportingCatCol,
                alcoholSetupCol,
                unitCol,
                packCountCol,
                packSizeCol,
                lastCaseCostCol,
                lastPurchasedCol,
                convCol,
                activeCol
        );

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        table.setRowFactory(tv -> {
            TableRow<Product> row = new TableRow<>();

            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    new ProductEditorView(row.getItem(), this::loadProducts).show();
                }
            });

            return row;
        });
    }

    private void setupHistoryTable() {
        TableColumn<PurchaseHistory, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("invoiceDate"));

        TableColumn<PurchaseHistory, String> invoiceCol = new TableColumn<>("Invoice #");
        invoiceCol.setCellValueFactory(new PropertyValueFactory<>("invoiceNumber"));

        TableColumn<PurchaseHistory, Double> qtyCol = new TableColumn<>("Qty");
        qtyCol.setCellValueFactory(new PropertyValueFactory<>("quantity"));

        TableColumn<PurchaseHistory, BigDecimal> caseCostCol = new TableColumn<>("Case Cost");
        caseCostCol.setCellValueFactory(new PropertyValueFactory<>("caseCost"));
        caseCostCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : formatMoney(value));
            }
        });

        TableColumn<PurchaseHistory, BigDecimal> extendedCostCol = new TableColumn<>("Extended Cost");
        extendedCostCol.setCellValueFactory(new PropertyValueFactory<>("extendedCost"));
        extendedCostCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : formatMoney(value));
            }
        });

        historyTable.getColumns().setAll(
                dateCol,
                invoiceCol,
                qtyCol,
                caseCostCol,
                extendedCostCol
        );

        historyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    private void loadProducts() {

        var products = productDao.findAll();

        if (department != null) {

            products.removeIf(product -> {

                String category =
                        product.getReportingCategory() == null
                                ? ""
                                : product.getReportingCategory().trim().toUpperCase();

                return switch (department) {

                    case "FOOD" ->
                            !category.equals("FOOD");

                    case "ALCOHOL" ->
                            !(category.equals("BEER")
                                    || category.equals("WINE")
                                    || category.equals("DRAUGHT")
                                    || category.equals("IMPORT DRAUGHT")
                                    || category.equals("LIQUOR"));

                    case "SUPPLIES" ->
                            !(category.equals("PAPER")
                                    || category.equals("TAKE OUT")
                                    || category.equals("CLEANING")
                                    || category.equals("DISHWASHING")
                                    || category.equals("GUEST SUPPLIES")
                                    || category.equals("OTHER"));

                    default -> false;
                };
            });
        }

        table.setItems(FXCollections.observableArrayList(products));
    }

    private void openAddProductDialog() {
        new ProductEditorView(null, this::loadProducts).show();
    }

    private void editSelectedProduct() {
        Product selected = table.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showAlert(
                    Alert.AlertType.WARNING,
                    "No Product Selected",
                    "Please select a product to edit."
            );
            return;
        }

        new ProductEditorView(selected, this::loadProducts).show();
    }

    private void showEditProductDialog(Product product) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Edit Product");
        dialog.setHeaderText("Edit product details");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        TextField categoryField = new TextField(product.getCategory());

        ComboBox<String> reportingCategoryBox = new ComboBox<>();
        reportingCategoryBox.getItems().addAll(ProductDialog.REPORTING_CATEGORIES);
        reportingCategoryBox.setValue(
                product.getReportingCategory() == null || product.getReportingCategory().isBlank()
                        ? "OTHER"
                        : product.getReportingCategory()
        );

        TextField unitField = new TextField(product.getUnit());

        TextField conversionFactorField =
                new TextField(String.valueOf(product.getConversionFactor()));

        TextField packSizeField =
                new TextField(product.getPackSize() == null ? "" : product.getPackSize());

        TextField packCountField =
                new TextField(product.getPackCount() == null ? "" : product.getPackCount());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        grid.add(new Label("Category:"), 0, 0);
        grid.add(categoryField, 1, 0);

        grid.add(new Label("Reporting Category:"), 0, 1);
        grid.add(reportingCategoryBox, 1, 1);

        grid.add(new Label("Unit:"), 0, 2);
        grid.add(unitField, 1, 2);

        grid.add(new Label("Units Per Purchased Unit:"), 0, 3);
        grid.add(conversionFactorField, 1, 3);

        grid.add(new Label("Pack Size:"), 0, 4);
        grid.add(packSizeField, 1, 4);

        grid.add(new Label("Pack Count:"), 0, 5);
        grid.add(packCountField, 1, 5);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> {
            if (button == saveButtonType) {
                try {
                    double conversionFactor =
                            Double.parseDouble(conversionFactorField.getText().trim());

                    productDao.updateProductDetails(
                            product.getId(),
                            categoryField.getText().trim(),
                            reportingCategoryBox.getValue(),
                            unitField.getText().trim(),
                            conversionFactor,
                            packSizeField.getText().trim(),
                            packCountField.getText().trim()
                    );

                    loadProducts();
                    return true;

                } catch (NumberFormatException ex) {
                    showAlert(
                            Alert.AlertType.ERROR,
                            "Invalid Number",
                            "Conversion Factor must be a valid number."
                    );
                }
            }

            return false;
        });

        dialog.showAndWait();
    }

    private void importGfsOrderGuide() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import GFS Order Guide");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );

        File file = fileChooser.showOpenDialog(null);

        if (file == null) {
            return;
        }

        try {
            int importedCount = importService.importProducts(file);
            loadProducts();

            showAlert(
                    Alert.AlertType.INFORMATION,
                    "Import Complete",
                    importedCount + " products imported or updated."
            );

        } catch (Exception ex) {
            showAlert(
                    Alert.AlertType.ERROR,
                    "Import Failed",
                    ex.getMessage()
            );
        }
    }

    private void deactivateSelectedProduct() {
        Product selected = table.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showAlert(
                    Alert.AlertType.WARNING,
                    "No Product Selected",
                    "Please select a product first."
            );
            return;
        }

        productDao.deactivate(selected);
        loadProducts();
        historyTable.getItems().clear();
    }

    private String formatMoney(BigDecimal value) {
        if (value == null) {
            value = BigDecimal.ZERO;
        }

        return "$" + value.setScale(2, RoundingMode.HALF_UP);
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}