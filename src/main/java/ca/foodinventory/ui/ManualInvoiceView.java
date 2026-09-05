package ca.foodinventory.ui;

import ca.foodinventory.dao.InvoiceDao;
import ca.foodinventory.dao.ProductDao;
import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.InvoiceAdjustment;
import ca.foodinventory.model.InvoiceLine;
import ca.foodinventory.model.Product;
import ca.foodinventory.service.InvoiceApiClient;
import ca.foodinventory.service.ProductApiClient;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ManualInvoiceView {

    private final InvoiceDao invoiceDao = new InvoiceDao();
    private final ProductDao productDao = new ProductDao();
    private final InvoiceApiClient invoiceApiClient = new InvoiceApiClient();
    private final ProductApiClient productApiClient = new ProductApiClient();

    private final List<InvoiceLine> lines = new ArrayList<>();
    private final TableView<InvoiceLine> table = new TableView<>();

    private final TextField supplierField = new TextField();
    private final TextField invoiceNumberField = new TextField();
    private final DatePicker invoiceDatePicker = new DatePicker(LocalDate.now());

    private final ComboBox<Product> productComboBox = new ComboBox<>();
    private final TextField caseQtyField = new TextField("0");
    private final TextField splitQtyField = new TextField("0");
    private final TextField caseCostField = new TextField("0.0000");
    private final TextField eachCostField = new TextField("0.0000");
    private final CheckBox creditLineCheckBox = new CheckBox("Credit / Return");

    private final Label totalLabel = new Label("$0.00");
    private final Label merchandiseSubtotalLabel = new Label("$0.00");
    private final Label adjustmentTotalLabel = new Label("$0.00");
    private final TextField hstField = new TextField("0.00");
    private final TextField bottleDepositField = new TextField("0.00");
    private final TextField kegDepositField = new TextField("0.00");
    private final TextField otherAdjustmentDescriptionField = new TextField();
    private final TextField otherAdjustmentAmountField = new TextField("0.00");
    private final String department;

    public ManualInvoiceView() {
        this(null);
    }

    public ManualInvoiceView(String department) {
        this.department = department;
    }

    private boolean showConfirmSaveDialog(
            String supplier,
            String invoiceNumber,
            String invoiceDate,
            int lineCount,
            BigDecimal merchandiseSubtotal,
            BigDecimal adjustmentTotal,
            BigDecimal total
    ) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Manual Invoice");
        alert.setHeaderText("Review invoice before saving.");

        alert.setContentText(
                "Supplier: " + supplier + "\n" +
                        "Invoice #: " + invoiceNumber + "\n" +
                        "Date: " + invoiceDate + "\n" +
                        "Lines: " + lineCount + "\n" +
                        "Inventory Cost Subtotal: $" + merchandiseSubtotal.setScale(2, RoundingMode.HALF_UP) + "\n" +
                        "Adjustments: $" + adjustmentTotal.setScale(2, RoundingMode.HALF_UP) + "\n" +
                        "Invoice Total: $" + total.setScale(2, RoundingMode.HALF_UP) + "\n\n" +
                        "Adjustments are saved for invoice balancing and are not included in inventory costs."
        );

        ButtonType saveButton = new ButtonType("Save Invoice", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(saveButton, cancelButton);

        return alert.showAndWait()
                .filter(button -> button == saveButton)
                .isPresent();
    }
    private boolean showDuplicateInvoiceDialog(String invoiceNumber) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Duplicate Invoice");
        alert.setHeaderText("This invoice number already exists.");

        alert.setContentText(
                "Invoice #: " + invoiceNumber + "\n\n" +
                        "Do you want to overwrite the existing invoice?"
        );

        ButtonType overwriteButton = new ButtonType("Overwrite Existing", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(overwriteButton, cancelButton);

        return alert.showAndWait()
                .filter(button -> button == overwriteButton)
                .isPresent();
    }

    public BorderPane getView() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-dark");

        Label title = new Label("Manual Invoice Entry");
        title.getStyleClass().add("page-title");
        Label noteLabel = new Label(
                "Enter product costs before HST and deposits. " +
                        "Do not include taxes, bottle deposits, keg deposits, " +
                        "or environmental fees in inventory costs. " +
                        "Use Credit / Return for returned or poor-quality products."
        );

        noteLabel.setWrapText(true);
        noteLabel.setStyle(
                "-fx-text-fill: orange;" +
                        "-fx-font-size: 12px;" +
                        "-fx-font-weight: bold;"
        );

        setupProductComboBox();
        setupTable();

        Button addLineButton = new Button("Add Line");
        addLineButton.getStyleClass().add("primary-button");
        addLineButton.setOnAction(e -> addLine());

        Button removeLineButton = new Button("Remove Selected Line");
        removeLineButton.getStyleClass().add("primary-button");
        removeLineButton.setOnAction(e -> removeSelectedLine());

        Button saveButton = new Button("Save Invoice");
        saveButton.getStyleClass().add("primary-button");
        saveButton.setOnAction(e -> saveInvoice());

        GridPane invoiceGrid = new GridPane();
        invoiceGrid.setHgap(12);
        invoiceGrid.setVgap(10);

        supplierField.setPromptText("LCBO / Beer Store / Supplier");
        invoiceNumberField.setPromptText("Invoice Number");

        invoiceGrid.add(new Label("Supplier:"), 0, 0);
        invoiceGrid.add(supplierField, 1, 0);

        invoiceGrid.add(new Label("Invoice #:"), 0, 1);
        invoiceGrid.add(invoiceNumberField, 1, 1);

        invoiceGrid.add(new Label("Invoice Date:"), 0, 2);
        invoiceGrid.add(invoiceDatePicker, 1, 2);

        GridPane lineGrid = new GridPane();
        lineGrid.setHgap(12);
        lineGrid.setVgap(10);

        lineGrid.add(new Label("Product:"), 0, 0);
        lineGrid.add(productComboBox, 1, 0, 3, 1);

        lineGrid.add(new Label("Case Qty:"), 0, 1);
        lineGrid.add(caseQtyField, 1, 1);

        lineGrid.add(new Label("Split Qty:"), 2, 1);
        lineGrid.add(splitQtyField, 3, 1);

        lineGrid.add(new Label("Case Cost:"), 0, 2);
        lineGrid.add(caseCostField, 1, 2);

        lineGrid.add(new Label("Each Cost:"), 2, 2);
        lineGrid.add(eachCostField, 3, 2);

        lineGrid.add(creditLineCheckBox, 1, 3);

        Label creditNote = new Label("Credit lines save received quantities as negative values.");
        creditNote.setStyle("-fx-text-fill: orange; -fx-font-size: 11px;");
        lineGrid.add(creditNote, 2, 3, 2, 1);

        lineGrid.add(addLineButton, 1, 4);

        VBox adjustmentBox = buildAdjustmentBox();

        HBox totalBox = new HBox(
                10,
                new Label("Inventory Subtotal:"),
                merchandiseSubtotalLabel,
                new Label("Adjustments:"),
                adjustmentTotalLabel,
                new Label("Invoice Total:"),
                totalLabel,
                removeLineButton,
                saveButton
        );

        VBox top = new VBox(20, title,noteLabel, invoiceGrid, lineGrid, adjustmentBox, totalBox);
        top.getStyleClass().add("top-bar");

        root.setTop(top);
        root.setCenter(table);

        return root;
    }

    private VBox buildAdjustmentBox() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);

        hstField.setPrefWidth(100);
        bottleDepositField.setPrefWidth(100);
        kegDepositField.setPrefWidth(100);
        otherAdjustmentDescriptionField.setPromptText("Description");
        otherAdjustmentAmountField.setPrefWidth(100);

        grid.add(new Label("HST:"), 0, 0);
        grid.add(hstField, 1, 0);
        grid.add(new Label("Bottle Deposit:"), 2, 0);
        grid.add(bottleDepositField, 3, 0);
        grid.add(new Label("Keg Deposit:"), 0, 1);
        grid.add(kegDepositField, 1, 1);
        grid.add(new Label("Other Adjustment:"), 2, 1);
        grid.add(otherAdjustmentDescriptionField, 3, 1);
        grid.add(otherAdjustmentAmountField, 4, 1);

        Label note = new Label("Alcohol adjustments balance the paper invoice without affecting inventory valuation.");
        note.setStyle("-fx-text-fill: orange; -fx-font-size: 11px;");

        VBox box = new VBox(8, new Label("Invoice Adjustments"), grid, note);
        box.setManaged(isAlcoholInvoice());
        box.setVisible(isAlcoholInvoice());

        hstField.textProperty().addListener((obs, oldValue, newValue) -> updateTotal());
        bottleDepositField.textProperty().addListener((obs, oldValue, newValue) -> updateTotal());
        kegDepositField.textProperty().addListener((obs, oldValue, newValue) -> updateTotal());
        otherAdjustmentAmountField.textProperty().addListener((obs, oldValue, newValue) -> updateTotal());

        return box;
    }

    private void setupProductComboBox() {
        productComboBox.setPrefWidth(500);

        SearchableComboBoxSupport.makeSearchable(productComboBox, loadProductsForInvoice(), new StringConverter<>() {
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
    }

    private List<Product> loadProductsForInvoice() {
        List<Product> products = isDepartmentApiMode()
                ? productApiClient.findAllActiveProducts()
                : productDao.findAll();

        if (department != null) {
            products.removeIf(product -> !matchesDepartment(product.getReportingCategory()));
        }

        return products;
    }

    private boolean matchesDepartment(String reportingCategory) {
        String category = reportingCategory == null ? "" : reportingCategory.trim().toUpperCase();

        return switch (department) {
            case "FOOD" -> category.equals("FOOD");
            case "ALCOHOL" -> isAlcoholReportingCategory(category);
            case "SUPPLIES" -> isSuppliesReportingCategory(category);
            default -> true;
        };
    }

    private boolean isAlcoholReportingCategory(String reportingCategory) {
        String category = reportingCategory == null ? "" : reportingCategory.trim().toUpperCase();

        return switch (category) {
            case "LIQUOR", "WINE", "BEER", "DRAUGHT", "IMPORT DRAUGHT" -> true;
            default -> false;
        };
    }

    private boolean isSuppliesReportingCategory(String reportingCategory) {
        String category = reportingCategory == null ? "" : reportingCategory.trim().toUpperCase();

        return switch (category) {
            case "PAPER", "TAKE OUT", "CLEANING", "DISHWASHING", "GUEST SUPPLIES", "OTHER" -> true;
            default -> false;
        };
    }

    private void setupTable() {
        TableColumn<InvoiceLine, String> skuCol = new TableColumn<>("SKU");
        skuCol.setCellValueFactory(new PropertyValueFactory<>("sku"));

        TableColumn<InvoiceLine, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        descCol.setPrefWidth(300);

        TableColumn<InvoiceLine, Double> caseQtyCol = new TableColumn<>("Case Qty");
        caseQtyCol.setCellValueFactory(new PropertyValueFactory<>("caseQty"));

        TableColumn<InvoiceLine, Double> splitQtyCol = new TableColumn<>("Split Qty");
        splitQtyCol.setCellValueFactory(new PropertyValueFactory<>("splitQty"));

        TableColumn<InvoiceLine, String> packCol = new TableColumn<>("Pack/Size");
        packCol.setCellValueFactory(new PropertyValueFactory<>("packSize"));

        TableColumn<InvoiceLine, BigDecimal> caseCostCol = new TableColumn<>("Case Cost");
        caseCostCol.setCellValueFactory(new PropertyValueFactory<>("caseCost"));

        TableColumn<InvoiceLine, BigDecimal> eachCostCol = new TableColumn<>("Each Cost");
        eachCostCol.setCellValueFactory(new PropertyValueFactory<>("eachCost"));

        TableColumn<InvoiceLine, BigDecimal> extendedCol = new TableColumn<>("Extended Cost");
        extendedCol.setCellValueFactory(new PropertyValueFactory<>("extendedCost"));

        table.getColumns().setAll(
                skuCol,
                descCol,
                caseQtyCol,
                splitQtyCol,
                packCol,
                caseCostCol,
                eachCostCol,
                extendedCol
        );

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    private void addLine() {
        Product product = productComboBox.getValue();

        if (product == null) {
            showAlert(Alert.AlertType.WARNING, "No Product Selected", "Please select a product.");
            return;
        }

        try {
            double caseQty = parseDouble(caseQtyField.getText());
            double splitQty = parseDouble(splitQtyField.getText());

            if (creditLineCheckBox.isSelected()) {
                caseQty = -Math.abs(caseQty);
                splitQty = -Math.abs(splitQty);
            }

            if (caseQty == 0 && splitQty == 0) {
                showAlert(
                        Alert.AlertType.WARNING,
                        "Missing Quantity",
                        "Enter a case or split quantity. Use Credit / Return to save it as a negative credit."
                );
                return;
            }

            BigDecimal caseCost = parseUnitCost(caseCostField.getText());
            BigDecimal eachCost = parseUnitCost(eachCostField.getText());

            BigDecimal extendedCost =
                    caseCost.multiply(BigDecimal.valueOf(caseQty))
                            .add(eachCost.multiply(BigDecimal.valueOf(splitQty)))
                            .setScale(2, RoundingMode.HALF_UP);

            InvoiceLine line = new InvoiceLine(
                    product.getSku(),
                    product.getDescription(),
                    caseQty,
                    splitQty,
                    product.getPackSize(),
                    caseCost,
                    eachCost,
                    extendedCost
            );

            lines.add(line);
            table.setItems(FXCollections.observableArrayList(lines));
            updateTotal();

            productComboBox.setValue(null);
            caseQtyField.setText("0");
            splitQtyField.setText("0");
            caseCostField.setText("0.0000");
            eachCostField.setText("0.0000");
            creditLineCheckBox.setSelected(false);

        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "Invalid Entry", "Please check quantities and costs.");
        }
    }

    private void saveInvoice() {
        if (supplierField.getText().isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Missing Supplier", "Please enter a supplier.");
            return;
        }

        if (invoiceNumberField.getText().isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Missing Invoice Number", "Please enter an invoice number.");
            return;
        }

        if (lines.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Lines", "Please add at least one product line.");
            return;
        }

        String supplier = supplierField.getText().trim();
        String invoiceNumber = invoiceNumberField.getText().trim();
        String invoiceDate = invoiceDatePicker.getValue().toString();
        BigDecimal merchandiseSubtotal = calculateMerchandiseSubtotal();
        BigDecimal adjustmentTotal;

        try {
            adjustmentTotal = calculateAdjustmentTotal(true);
        } catch (NumberFormatException ex) {
            showAlert(
                    Alert.AlertType.ERROR,
                    "Invalid Adjustment",
                    "Please check the HST, deposit, and adjustment amounts."
            );
            return;
        }

        BigDecimal total = merchandiseSubtotal
                .add(adjustmentTotal)
                .setScale(2, RoundingMode.HALF_UP);

        boolean confirmed = showConfirmSaveDialog(
                supplier,
                invoiceNumber,
                invoiceDate,
                lines.size(),
                merchandiseSubtotal,
                adjustmentTotal,
                total
        );

        if (!confirmed) {
            return;
        }

        if (invoiceExists(invoiceNumber)) {
            boolean overwrite = showDuplicateInvoiceDialog(invoiceNumber);

            if (!overwrite) {
                return;
            }

            deleteInvoice(invoiceNumber);
        }

        if (isAlcoholInvoice()) {
            invoiceDao.saveInvoice(
                    supplier,
                    invoiceNumber,
                    invoiceDate,
                    total,
                    merchandiseSubtotal,
                    total,
                    buildAdjustments(),
                    lines
            );
        } else if (isDepartmentApiMode()) {
            invoiceApiClient.saveInvoice(
                    department,
                    supplier,
                    invoiceNumber,
                    invoiceDate,
                    total,
                    lines
            );
        } else {
            invoiceDao.saveInvoice(
                    supplier,
                    invoiceNumber,
                    invoiceDate,
                    total,
                    lines
            );
        }

        showAlert(Alert.AlertType.INFORMATION, "Invoice Saved", "Manual invoice saved successfully.");

        lines.clear();
        table.setItems(FXCollections.observableArrayList(lines));
        updateTotal();

        invoiceNumberField.clear();
        supplierField.clear();
        invoiceDatePicker.setValue(LocalDate.now());
        clearAdjustmentFields();
    }

    private void updateTotal() {
        merchandiseSubtotalLabel.setText(formatMoney(calculateMerchandiseSubtotal()));
        adjustmentTotalLabel.setText(formatMoney(calculateAdjustmentTotal()));
        totalLabel.setText(formatMoney(calculateTotal()));
    }

    private BigDecimal calculateTotal() {
        return calculateMerchandiseSubtotal()
                .add(calculateAdjustmentTotal(false))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateMerchandiseSubtotal() {
        return lines.stream()
                .map(InvoiceLine::getExtendedCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateAdjustmentTotal() {
        return calculateAdjustmentTotal(false);
    }

    private BigDecimal calculateAdjustmentTotal(boolean strict) {
        if (!isAlcoholInvoice()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return buildAdjustments(strict).stream()
                .map(InvoiceAdjustment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private List<InvoiceAdjustment> buildAdjustments() {
        return buildAdjustments(true);
    }

    private List<InvoiceAdjustment> buildAdjustments(boolean strict) {
        if (!isAlcoholInvoice()) {
            return List.of();
        }

        List<InvoiceAdjustment> adjustments = new ArrayList<>();
        int displayOrder = 10;

        displayOrder = addAdjustment(adjustments, "HST", hstField.getText(), displayOrder, strict);
        displayOrder = addAdjustment(adjustments, "Bottle Deposit", bottleDepositField.getText(), displayOrder, strict);
        displayOrder = addAdjustment(adjustments, "Keg Deposit", kegDepositField.getText(), displayOrder, strict);

        String otherDescription = otherAdjustmentDescriptionField.getText() == null
                ? ""
                : otherAdjustmentDescriptionField.getText().trim();

        if (!otherDescription.isBlank()) {
            addAdjustment(adjustments, otherDescription, otherAdjustmentAmountField.getText(), displayOrder, strict);
        }

        return adjustments;
    }

    private int addAdjustment(
            List<InvoiceAdjustment> adjustments,
            String description,
            String amountText,
            int displayOrder,
            boolean strict
    ) {
        BigDecimal amount = strict ? parseMoney(amountText) : parseMoneyOrZero(amountText);

        if (amount.compareTo(BigDecimal.ZERO) != 0) {
            adjustments.add(new InvoiceAdjustment(description, amount, displayOrder));
            displayOrder += 10;
        }

        return displayOrder;
    }

    private void clearAdjustmentFields() {
        hstField.setText("0.00");
        bottleDepositField.setText("0.00");
        kegDepositField.setText("0.00");
        otherAdjustmentDescriptionField.clear();
        otherAdjustmentAmountField.setText("0.00");
    }

    private boolean isAlcoholInvoice() {
        return "ALCOHOL".equals(department);
    }

    private boolean isDepartmentApiMode() {
        return DatabaseManager.isApiDatabase()
                && ("FOOD".equals(department) || "SUPPLIES".equals(department));
    }

    private boolean invoiceExists(String invoiceNumber) {
        return isDepartmentApiMode()
                ? invoiceApiClient.invoiceExists(invoiceNumber)
                : invoiceDao.invoiceExists(invoiceNumber);
    }

    private void deleteInvoice(String invoiceNumber) {
        if (isDepartmentApiMode()) {
            invoiceApiClient.deleteInvoice(invoiceNumber);
        } else {
            invoiceDao.deleteInvoice(invoiceNumber);
        }
    }

    private double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }

        return Double.parseDouble(value.trim());
    }

    private BigDecimal parseMoney(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }

        return new BigDecimal(
                value.replace("$", "")
                        .replace(",", "")
                        .trim()
        ).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal parseUnitCost(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }

        return new BigDecimal(
                value.replace("$", "")
                        .replace(",", "")
                        .trim()
        ).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal parseMoneyOrZero(String value) {
        try {
            return parseMoney(value);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
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
    private void removeSelectedLine() {
        InvoiceLine selectedLine = table.getSelectionModel().getSelectedItem();

        if (selectedLine == null) {
            showAlert(
                    Alert.AlertType.WARNING,
                    "No Line Selected",
                    "Please select a line to remove."
            );
            return;
        }

        lines.remove(selectedLine);
        table.setItems(FXCollections.observableArrayList(lines));
        updateTotal();
    }
}
