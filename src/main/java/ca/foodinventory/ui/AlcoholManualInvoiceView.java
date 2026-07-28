package ca.foodinventory.ui;

import ca.foodinventory.dao.InvoiceDao;
import ca.foodinventory.dao.ProductDao;
import ca.foodinventory.model.InvoiceAdjustment;
import ca.foodinventory.model.InvoiceLine;
import ca.foodinventory.model.Product;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class AlcoholManualInvoiceView {

    private static final BigDecimal HST_DIVISOR = new BigDecimal("1.13");

    private final InvoiceDao invoiceDao = new InvoiceDao();
    private final ProductDao productDao = new ProductDao();

    private final List<AlcoholInvoiceEntryLine> lines = new ArrayList<>();
    private final TableView<AlcoholInvoiceEntryLine> table = new TableView<>();

    private final TextField supplierField = new TextField();
    private final TextField invoiceNumberField = new TextField();
    private final DatePicker invoiceDatePicker = new DatePicker(LocalDate.now());

    private final ComboBox<Product> productComboBox = new ComboBox<>();
    private final TextField quantityField = new TextField("1");
    private final TextField paperLineTotalField = new TextField("0.00");
    private final TextField bottleDepositField = new TextField("0.00");
    private final CheckBox hstIncludedCheckBox = new CheckBox("HST Included");
    private final CheckBox bottleDepositIncludedCheckBox = new CheckBox("Bottle Deposit Included");
    private final CheckBox creditLineCheckBox = new CheckBox("Credit / Return");

    private final Label totalLabel = new Label("$0.00");
    private final Label merchandiseSubtotalLabel = new Label("$0.00");
    private final Label adjustmentTotalLabel = new Label("$0.00");
    private final TextField paperInvoiceTotalField = new TextField("0.00");
    private final TextField exactHstField = new TextField("0.00");
    private final TextField exactBottleDepositField = new TextField("0.00");
    private final TextField kegDepositField = new TextField("0.00");
    private final TextField otherAdjustmentDescriptionField = new TextField();
    private final TextField otherAdjustmentAmountField = new TextField("0.00");
    private final Label discrepancyLabel = new Label("$0.00");

    public BorderPane getView() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-dark");

        Label title = new Label("Alcohol Manual Invoice Entry");
        title.getStyleClass().add("page-title");

        Label noteLabel = new Label(
                "Enter qty + paper line total. Check HST Included / Bottle Deposit Included only when those amounts are already inside that line subtotal."
        );
        noteLabel.setWrapText(true);
        noteLabel.setStyle(
                "-fx-text-fill: orange;" +
                        "-fx-font-size: 11px;" +
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
        invoiceGrid.setHgap(8);
        invoiceGrid.setVgap(6);

        supplierField.setPromptText("LCBO / Beer Store / Supplier");
        invoiceNumberField.setPromptText("Invoice Number");

        invoiceGrid.add(new Label("Supplier:"), 0, 0);
        invoiceGrid.add(supplierField, 1, 0);

        invoiceGrid.add(new Label("Invoice #:"), 0, 1);
        invoiceGrid.add(invoiceNumberField, 1, 1);

        invoiceGrid.add(new Label("Invoice Date:"), 0, 2);
        invoiceGrid.add(invoiceDatePicker, 1, 2);

        GridPane lineGrid = new GridPane();
        lineGrid.setHgap(8);
        lineGrid.setVgap(6);

        lineGrid.add(new Label("Product:"), 0, 0);
        lineGrid.add(productComboBox, 1, 0, 3, 1);

        lineGrid.add(new Label("Unit Qty:"), 0, 1);
        lineGrid.add(quantityField, 1, 1);

        lineGrid.add(new Label("Paper Line Total:"), 2, 1);
        lineGrid.add(paperLineTotalField, 3, 1);

        lineGrid.add(new Label("Bottle Deposit in Line:"), 0, 2);
        lineGrid.add(bottleDepositField, 1, 2);

        lineGrid.add(hstIncludedCheckBox, 2, 2);
        lineGrid.add(bottleDepositIncludedCheckBox, 3, 2);

        lineGrid.add(creditLineCheckBox, 1, 3);

        Label creditNote = new Label("Credit lines save quantity and merchandise value as negative values.");
        creditNote.setStyle("-fx-text-fill: orange; -fx-font-size: 11px;");
        lineGrid.add(creditNote, 2, 3, 2, 1);

        Label lineHelp = new Label(
                "If the paper line already includes HST or deposit, check the matching box and the app will strip it automatically."
        );
        lineHelp.setWrapText(true);
        lineHelp.setStyle("-fx-text-fill: orange; -fx-font-size: 11px;");
        lineGrid.add(lineHelp, 0, 4, 4, 1);

        lineGrid.add(addLineButton, 1, 5);

        VBox adjustmentBox = buildAdjustmentBox();
        adjustmentBox.setSpacing(6);

        HBox formRow = new HBox(16, invoiceGrid, lineGrid);
        HBox.setHgrow(invoiceGrid, Priority.NEVER);
        HBox.setHgrow(lineGrid, Priority.ALWAYS);

        HBox totalBox = new HBox(
                8,
                new Label("Inventory Subtotal:"),
                merchandiseSubtotalLabel,
                new Label("Adjustments:"),
                adjustmentTotalLabel,
                new Label("Calculated Total:"),
                totalLabel,
                removeLineButton,
                saveButton
        );

        totalBox.setStyle("-fx-alignment: center-left;");

        VBox top = new VBox(10, title, noteLabel, formRow, adjustmentBox, totalBox);
        top.getStyleClass().add("top-bar");

        root.setTop(top);
        root.setCenter(table);

        return root;
    }

    private VBox buildAdjustmentBox() {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);

        paperInvoiceTotalField.setPrefWidth(100);
        exactHstField.setPrefWidth(100);
        exactBottleDepositField.setPrefWidth(100);
        kegDepositField.setPrefWidth(100);
        otherAdjustmentDescriptionField.setPromptText("Description");
        otherAdjustmentAmountField.setPrefWidth(100);

        grid.add(new Label("Paper Invoice Total:"), 0, 0);
        grid.add(paperInvoiceTotalField, 1, 0);
        grid.add(new Label("Exact HST:"), 2, 0);
        grid.add(exactHstField, 3, 0);
        grid.add(new Label("Exact Bottle Deposit:"), 0, 1);
        grid.add(exactBottleDepositField, 1, 1);
        grid.add(new Label("Keg Deposit:"), 2, 1);
        grid.add(kegDepositField, 3, 1);
        grid.add(new Label("Other Adjustment:"), 0, 2);
        grid.add(otherAdjustmentDescriptionField, 1, 2);
        grid.add(otherAdjustmentAmountField, 2, 2);
        grid.add(new Label("Difference:"), 3, 2);
        discrepancyLabel.setStyle("-fx-text-fill: orange; -fx-font-size: 11px; -fx-font-weight: bold;");
        grid.add(discrepancyLabel, 4, 2);

        Label note = new Label(
                "Use the exact paper HST and deposit totals here. Any remaining difference is reconciled into merchandise categories, not into HST."
        );
        note.setStyle("-fx-text-fill: orange; -fx-font-size: 10px;");

        VBox box = new VBox(8, new Label("Invoice Adjustments"), grid, note);

        paperInvoiceTotalField.textProperty().addListener((obs, oldValue, newValue) -> updateTotal());
        exactHstField.textProperty().addListener((obs, oldValue, newValue) -> updateTotal());
        exactBottleDepositField.textProperty().addListener((obs, oldValue, newValue) -> updateTotal());
        kegDepositField.textProperty().addListener((obs, oldValue, newValue) -> updateTotal());
        otherAdjustmentAmountField.textProperty().addListener((obs, oldValue, newValue) -> updateTotal());

        return box;
    }

    private void setupProductComboBox() {
        productComboBox.setPrefWidth(500);

        SearchableComboBoxSupport.makeSearchable(productComboBox, loadAlcoholProducts(), new StringConverter<>() {
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

    private List<Product> loadAlcoholProducts() {
        List<Product> products = productDao.findAll();
        products.removeIf(product -> !isAlcoholReportingCategory(product.getReportingCategory()));
        return products;
    }

    private boolean isAlcoholReportingCategory(String reportingCategory) {
        String category = reportingCategory == null ? "" : reportingCategory.trim().toUpperCase();

        return switch (category) {
            case "LIQUOR", "WINE", "BEER", "DRAUGHT", "IMPORT DRAUGHT" -> true;
            default -> false;
        };
    }

    private void setupTable() {
        TableColumn<AlcoholInvoiceEntryLine, String> skuCol = new TableColumn<>("SKU");
        skuCol.setCellValueFactory(new PropertyValueFactory<>("sku"));

        TableColumn<AlcoholInvoiceEntryLine, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        descCol.setPrefWidth(220);

        TableColumn<AlcoholInvoiceEntryLine, Double> qtyCol = new TableColumn<>("Qty");
        qtyCol.setCellValueFactory(new PropertyValueFactory<>("quantity"));

        TableColumn<AlcoholInvoiceEntryLine, BigDecimal> paperTotalCol = new TableColumn<>("Paper Total");
        paperTotalCol.setCellValueFactory(new PropertyValueFactory<>("paperLineTotal"));
        paperTotalCol.setCellFactory(column -> moneyCell());

        TableColumn<AlcoholInvoiceEntryLine, String> hstIncludedCol = new TableColumn<>("HST");
        hstIncludedCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().isHstIncluded() ? "Included" : "Separate"));

        TableColumn<AlcoholInvoiceEntryLine, String> depositIncludedCol = new TableColumn<>("Deposit");
        depositIncludedCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().isBottleDepositIncluded() ? "Included" : "Separate"));

        TableColumn<AlcoholInvoiceEntryLine, BigDecimal> merchCol = new TableColumn<>("Inventory Merchandise");
        merchCol.setCellValueFactory(new PropertyValueFactory<>("merchandiseSubtotal"));
        merchCol.setCellFactory(column -> moneyCell());

        TableColumn<AlcoholInvoiceEntryLine, BigDecimal> unitCostCol = new TableColumn<>("Unit Cost");
        unitCostCol.setCellValueFactory(new PropertyValueFactory<>("unitCost"));
        unitCostCol.setCellFactory(column -> unitCostCell());

        table.getColumns().setAll(
                skuCol,
                descCol,
                qtyCol,
                paperTotalCol,
                hstIncludedCol,
                depositIncludedCol,
                merchCol,
                unitCostCol
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
            double quantity = parseDouble(quantityField.getText());
            if (quantity == 0) {
                showAlert(Alert.AlertType.WARNING, "Missing Quantity", "Please enter a unit quantity.");
                return;
            }

            BigDecimal paperLineTotal = parseMoney(paperLineTotalField.getText());
            if (paperLineTotal.compareTo(BigDecimal.ZERO) == 0) {
                showAlert(Alert.AlertType.WARNING, "Missing Line Total", "Please enter the paper line total.");
                return;
            }

            BigDecimal bottleDepositAmount = parseMoney(bottleDepositField.getText());

            if (!bottleDepositIncludedCheckBox.isSelected()) {
                bottleDepositAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }

            if (creditLineCheckBox.isSelected()) {
                quantity = -Math.abs(quantity);
                paperLineTotal = paperLineTotal.abs().negate();
                bottleDepositAmount = bottleDepositAmount.abs().negate();
            }

            AlcoholInvoiceEntryLine line = AlcoholInvoiceEntryLine.fromInputs(
                    product,
                    quantity,
                    paperLineTotal,
                    bottleDepositAmount,
                    hstIncludedCheckBox.isSelected(),
                    bottleDepositIncludedCheckBox.isSelected()
            );

            if (line.getMerchandiseSubtotal().compareTo(BigDecimal.ZERO) == 0) {
                showAlert(Alert.AlertType.WARNING, "Invalid Merchandise Total", "The calculated inventory merchandise total is zero. Please review the line inputs.");
                return;
            }

            lines.add(line);
            table.setItems(FXCollections.observableArrayList(lines));
            updateTotal();
            clearLineEntryFields();

        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "Invalid Entry", "Please check the quantity, line total, and deposit values.");
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
        BigDecimal paperInvoiceTotal = parseMoney(paperInvoiceTotalField.getText());

        if (paperInvoiceTotal.compareTo(BigDecimal.ZERO) == 0) {
            showAlert(Alert.AlertType.WARNING, "Missing Paper Invoice Total", "Please enter the exact paper invoice total.");
            return;
        }

        BigDecimal rawMerchandiseSubtotal = calculateMerchandiseSubtotal();
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

        BigDecimal discrepancy = calculateDiscrepancy(false);
        BigDecimal reconciledMerchandiseSubtotal = rawMerchandiseSubtotal
                .subtract(discrepancy)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal total = reconciledMerchandiseSubtotal
                .add(adjustmentTotal)
                .setScale(2, RoundingMode.HALF_UP);

        boolean confirmed = showConfirmSaveDialog(
                supplier,
                invoiceNumber,
                invoiceDate,
                lines.size(),
                reconciledMerchandiseSubtotal,
                adjustmentTotal,
                total,
                discrepancy
        );

        if (!confirmed) {
            return;
        }

        if (invoiceDao.invoiceExists(invoiceNumber)) {
            boolean overwrite = showDuplicateInvoiceDialog(invoiceNumber);

            if (!overwrite) {
                return;
            }

            invoiceDao.deleteInvoice(invoiceNumber);
        }

        invoiceDao.saveInvoice(
                supplier,
                invoiceNumber,
                invoiceDate,
                paperInvoiceTotal,
                reconciledMerchandiseSubtotal,
                paperInvoiceTotal,
                buildAdjustments(),
                buildInvoiceLines(discrepancy)
        );

        showAlert(Alert.AlertType.INFORMATION, "Invoice Saved", "Alcohol manual invoice saved successfully.");

        lines.clear();
        table.setItems(FXCollections.observableArrayList(lines));
        updateTotal();

        invoiceNumberField.clear();
        supplierField.clear();
        invoiceDatePicker.setValue(LocalDate.now());
        clearLineEntryFields();
        clearAdjustmentFields();
    }

    private void updateTotal() {
        merchandiseSubtotalLabel.setText(formatMoney(calculateMerchandiseSubtotal()));
        adjustmentTotalLabel.setText(formatMoney(calculateAdjustmentTotal()));
        totalLabel.setText(formatMoney(calculateTotal()));
        BigDecimal discrepancy = calculateDiscrepancy(false);
        discrepancyLabel.setText(formatMoney(discrepancy));

        if (discrepancy.compareTo(BigDecimal.ZERO) == 0) {
            discrepancyLabel.setStyle("-fx-text-fill: lightgreen; -fx-font-size: 11px; -fx-font-weight: bold;");
        } else {
            discrepancyLabel.setStyle("-fx-text-fill: orange; -fx-font-size: 11px; -fx-font-weight: bold;");
        }
    }

    private BigDecimal calculateTotal() {
        return calculateMerchandiseSubtotal()
                .add(calculateAdjustmentTotal(false))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateDiscrepancy(boolean strict) {
        BigDecimal paperInvoiceTotal = strict
                ? parseMoney(paperInvoiceTotalField.getText())
                : parseMoneyOrZero(paperInvoiceTotalField.getText());

        return calculateTotal()
                .subtract(paperInvoiceTotal)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateMerchandiseSubtotal() {
        return lines.stream()
                .map(AlcoholInvoiceEntryLine::getMerchandiseSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateAdjustmentTotal() {
        return calculateAdjustmentTotal(false);
    }

    private BigDecimal calculateAdjustmentTotal(boolean strict) {
        return buildAdjustments(strict).stream()
                .map(InvoiceAdjustment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private List<InvoiceLine> buildInvoiceLines(BigDecimal discrepancy) {
        List<AlcoholInvoiceEntryLine> reconciledEntries = applyReconciliation(discrepancy);
        return reconciledEntries.stream()
                .map(AlcoholInvoiceEntryLine::toInvoiceLine)
                .toList();
    }

    private List<InvoiceAdjustment> buildAdjustments() {
        return buildAdjustments(true);
    }

    private List<InvoiceAdjustment> buildAdjustments(boolean strict) {
        List<InvoiceAdjustment> adjustments = new ArrayList<>();
        int displayOrder = 10;

        BigDecimal exactHst = strict
                ? parseMoney(exactHstField.getText())
                : parseMoneyOrZero(exactHstField.getText());
        displayOrder = addAdjustment(adjustments, "HST", exactHst, displayOrder);

        BigDecimal exactBottleDeposit = strict
                ? parseMoney(exactBottleDepositField.getText())
                : parseMoneyOrZero(exactBottleDepositField.getText());
        displayOrder = addAdjustment(adjustments, "Bottle Deposit", exactBottleDeposit, displayOrder);

        BigDecimal kegDeposit = strict
                ? parseMoney(kegDepositField.getText())
                : parseMoneyOrZero(kegDepositField.getText());
        displayOrder = addAdjustment(adjustments, "Keg Deposit", kegDeposit, displayOrder);

        String otherDescription = otherAdjustmentDescriptionField.getText() == null
                ? ""
                : otherAdjustmentDescriptionField.getText().trim();
        BigDecimal otherAmount = strict
                ? parseMoney(otherAdjustmentAmountField.getText())
                : parseMoneyOrZero(otherAdjustmentAmountField.getText());

        if (!otherDescription.isBlank()) {
            addAdjustment(adjustments, otherDescription, otherAmount, displayOrder);
        }

        return adjustments;
    }

    private int addAdjustment(
            List<InvoiceAdjustment> adjustments,
            String description,
            BigDecimal amount,
            int displayOrder
    ) {
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }

        amount = amount.setScale(2, RoundingMode.HALF_UP);

        if (amount.compareTo(BigDecimal.ZERO) != 0) {
            adjustments.add(new InvoiceAdjustment(description, amount, displayOrder));
            displayOrder += 10;
        }

        return displayOrder;
    }

    private void clearLineEntryFields() {
        productComboBox.setValue(null);
        quantityField.setText("1");
        paperLineTotalField.setText("0.00");
        bottleDepositField.setText("0.00");
        hstIncludedCheckBox.setSelected(false);
        bottleDepositIncludedCheckBox.setSelected(false);
        creditLineCheckBox.setSelected(false);
    }

    private void clearAdjustmentFields() {
        paperInvoiceTotalField.setText("0.00");
        exactHstField.setText("0.00");
        exactBottleDepositField.setText("0.00");
        kegDepositField.setText("0.00");
        otherAdjustmentDescriptionField.clear();
        otherAdjustmentAmountField.setText("0.00");
    }

    private double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }

        return Double.parseDouble(value.trim());
    }

    private BigDecimal parseMoney(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return new BigDecimal(
                value.replace("$", "")
                        .replace(",", "")
                        .trim()
        ).setScale(2, RoundingMode.HALF_UP);
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

    private <T> TableCell<T, BigDecimal> moneyCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : formatMoney(value));
            }
        };
    }

    private <T> TableCell<T, BigDecimal> unitCostCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : "$" + value.setScale(4, RoundingMode.HALF_UP));
            }
        };
    }

    private boolean showConfirmSaveDialog(
            String supplier,
            String invoiceNumber,
            String invoiceDate,
            int lineCount,
            BigDecimal merchandiseSubtotal,
            BigDecimal adjustmentTotal,
            BigDecimal total,
            BigDecimal discrepancy
    ) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Alcohol Manual Invoice");
        alert.setHeaderText("Review alcohol invoice before saving.");

        alert.setContentText(
                "Supplier: " + supplier + "\n" +
                        "Invoice #: " + invoiceNumber + "\n" +
                        "Date: " + invoiceDate + "\n" +
                        "Lines: " + lineCount + "\n" +
                        "Inventory Cost Subtotal: $" + merchandiseSubtotal.setScale(2, RoundingMode.HALF_UP) + "\n" +
                        "Adjustments: $" + adjustmentTotal.setScale(2, RoundingMode.HALF_UP) + "\n" +
                        "Reconciliation Applied: $" + discrepancy.setScale(2, RoundingMode.HALF_UP) + "\n" +
                        "Invoice Total: $" + total.setScale(2, RoundingMode.HALF_UP) + "\n\n" +
                        "Exact paper HST and deposit are saved as adjustments. Any remaining difference is absorbed into merchandise categories."
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

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void removeSelectedLine() {
        AlcoholInvoiceEntryLine selectedLine = table.getSelectionModel().getSelectedItem();

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

    private List<AlcoholInvoiceEntryLine> applyReconciliation(BigDecimal discrepancy) {
        List<AlcoholInvoiceEntryLine> reconciled = new ArrayList<>(lines);

        if (discrepancy == null || discrepancy.compareTo(BigDecimal.ZERO) == 0 || reconciled.isEmpty()) {
            return reconciled;
        }

        String targetCategory = findTargetCategory(discrepancy);
        if (targetCategory == null) {
            return reconciled;
        }

        int targetIndex = findTargetLineIndex(reconciled, targetCategory);
        if (targetIndex < 0) {
            return reconciled;
        }

        AlcoholInvoiceEntryLine target = reconciled.get(targetIndex);
        BigDecimal adjustedMerchandise = target.getMerchandiseSubtotal()
                .subtract(discrepancy)
                .setScale(2, RoundingMode.HALF_UP);

        if (target.getQuantity() == 0) {
            return reconciled;
        }

        reconciled.set(targetIndex, target.withMerchandiseSubtotal(adjustedMerchandise));
        return reconciled;
    }

    private String findTargetCategory(BigDecimal discrepancy) {
        java.util.Map<String, BigDecimal> totalsByCategory = new java.util.LinkedHashMap<>();

        for (AlcoholInvoiceEntryLine line : lines) {
            String category = normalizeCategory(line.getReportingCategory());
            totalsByCategory.put(
                    category,
                    totalsByCategory.getOrDefault(category, BigDecimal.ZERO).add(line.getMerchandiseSubtotal())
            );
        }

        return totalsByCategory.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .filter(entry -> entry.getValue().compareTo(BigDecimal.ZERO) > 0)
                .sorted((a, b) -> discrepancy.compareTo(BigDecimal.ZERO) > 0
                        ? b.getValue().compareTo(a.getValue())
                        : a.getValue().compareTo(b.getValue()))
                .map(java.util.Map.Entry::getKey)
                .findFirst()
                .orElseGet(() -> totalsByCategory.keySet().stream().findFirst().orElse(null));
    }

    private int findTargetLineIndex(List<AlcoholInvoiceEntryLine> reconciled, String targetCategory) {
        int index = -1;
        BigDecimal largestLine = null;

        for (int i = 0; i < reconciled.size(); i++) {
            AlcoholInvoiceEntryLine line = reconciled.get(i);
            if (!normalizeCategory(line.getReportingCategory()).equals(targetCategory)) {
                continue;
            }

            BigDecimal magnitude = line.getMerchandiseSubtotal().abs();
            if (largestLine == null || magnitude.compareTo(largestLine) > 0) {
                largestLine = magnitude;
                index = i;
            }
        }

        return index;
    }

    private String normalizeCategory(String value) {
        if (value == null || value.isBlank()) {
            return "OTHER";
        }

        return value.trim().toUpperCase();
    }

    public static class AlcoholInvoiceEntryLine {
        private final Product product;
        private final double quantity;
        private final BigDecimal paperLineTotal;
        private final BigDecimal includedBottleDepositAmount;
        private final boolean hstIncluded;
        private final boolean bottleDepositIncluded;
        private final BigDecimal includedHstAmount;
        private final BigDecimal merchandiseSubtotal;
        private final BigDecimal unitCost;

        private AlcoholInvoiceEntryLine(
                Product product,
                double quantity,
                BigDecimal paperLineTotal,
                BigDecimal includedBottleDepositAmount,
                boolean hstIncluded,
                boolean bottleDepositIncluded,
                BigDecimal includedHstAmount,
                BigDecimal merchandiseSubtotal,
                BigDecimal unitCost
        ) {
            this.product = product;
            this.quantity = quantity;
            this.paperLineTotal = paperLineTotal;
            this.includedBottleDepositAmount = includedBottleDepositAmount;
            this.hstIncluded = hstIncluded;
            this.bottleDepositIncluded = bottleDepositIncluded;
            this.includedHstAmount = includedHstAmount;
            this.merchandiseSubtotal = merchandiseSubtotal;
            this.unitCost = unitCost;
        }

        public static AlcoholInvoiceEntryLine fromInputs(
                Product product,
                double quantity,
                BigDecimal paperLineTotal,
                BigDecimal bottleDepositAmount,
                boolean hstIncluded,
                boolean bottleDepositIncluded
        ) {
            BigDecimal depositIncludedAmount = bottleDepositIncluded
                    ? bottleDepositAmount.setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

            BigDecimal subtotalAfterDeposit = paperLineTotal.subtract(depositIncludedAmount);

            BigDecimal includedHstAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            BigDecimal merchandiseSubtotal;

            if (hstIncluded) {
                merchandiseSubtotal = subtotalAfterDeposit.divide(HST_DIVISOR, 2, RoundingMode.HALF_UP);
                includedHstAmount = subtotalAfterDeposit.subtract(merchandiseSubtotal).setScale(2, RoundingMode.HALF_UP);
            } else {
                merchandiseSubtotal = subtotalAfterDeposit.setScale(2, RoundingMode.HALF_UP);
            }

            BigDecimal absoluteQuantity = BigDecimal.valueOf(Math.abs(quantity));
            BigDecimal unitCost = absoluteQuantity.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
                    : merchandiseSubtotal.abs().divide(absoluteQuantity, 4, RoundingMode.HALF_UP);

            if (quantity < 0 || merchandiseSubtotal.compareTo(BigDecimal.ZERO) < 0) {
                unitCost = unitCost.negate();
            }

            return new AlcoholInvoiceEntryLine(
                    product,
                    quantity,
                    paperLineTotal.setScale(2, RoundingMode.HALF_UP),
                    depositIncludedAmount,
                    hstIncluded,
                    bottleDepositIncluded,
                    includedHstAmount,
                    merchandiseSubtotal.setScale(2, RoundingMode.HALF_UP),
                    unitCost.setScale(4, RoundingMode.HALF_UP)
            );
        }

        public InvoiceLine toInvoiceLine() {
            return new InvoiceLine(
                    product.getSku(),
                    product.getDescription(),
                    0,
                    quantity,
                    product.getPackSize(),
                    BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP),
                    unitCost,
                    merchandiseSubtotal
            );
        }

        public String getSku() {
            return product.getSku();
        }

        public String getDescription() {
            return product.getDescription();
        }

        public double getQuantity() {
            return quantity;
        }

        public BigDecimal getPaperLineTotal() {
            return paperLineTotal;
        }

        public boolean isHstIncluded() {
            return hstIncluded;
        }

        public boolean isBottleDepositIncluded() {
            return bottleDepositIncluded;
        }

        public BigDecimal getIncludedBottleDepositAmount() {
            return includedBottleDepositAmount;
        }

        public BigDecimal getIncludedHstAmount() {
            return includedHstAmount;
        }

        public BigDecimal getMerchandiseSubtotal() {
            return merchandiseSubtotal;
        }

        public BigDecimal getUnitCost() {
            return unitCost;
        }

        public String getReportingCategory() {
            return product.getReportingCategory();
        }

        public AlcoholInvoiceEntryLine withMerchandiseSubtotal(BigDecimal updatedMerchandiseSubtotal) {
            BigDecimal absoluteQuantity = BigDecimal.valueOf(Math.abs(quantity));
            BigDecimal recalculatedUnitCost = absoluteQuantity.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
                    : updatedMerchandiseSubtotal.abs().divide(absoluteQuantity, 4, RoundingMode.HALF_UP);

            if (quantity < 0 || updatedMerchandiseSubtotal.compareTo(BigDecimal.ZERO) < 0) {
                recalculatedUnitCost = recalculatedUnitCost.negate();
            }

            return new AlcoholInvoiceEntryLine(
                    product,
                    quantity,
                    paperLineTotal,
                    includedBottleDepositAmount,
                    hstIncluded,
                    bottleDepositIncluded,
                    includedHstAmount,
                    updatedMerchandiseSubtotal.setScale(2, RoundingMode.HALF_UP),
                    recalculatedUnitCost.setScale(4, RoundingMode.HALF_UP)
            );
        }
    }
}
