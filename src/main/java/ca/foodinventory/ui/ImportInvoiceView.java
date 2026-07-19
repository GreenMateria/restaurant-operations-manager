package ca.foodinventory.ui;

import ca.foodinventory.dao.InvoiceDao;
import ca.foodinventory.dao.ProductDao;
import ca.foodinventory.model.InvoiceLine;
import ca.foodinventory.model.InvoiceAdjustment;
import ca.foodinventory.model.Product;
import ca.foodinventory.service.GfsCsvImportService;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ImportInvoiceView {

    private final GfsCsvImportService importService = new GfsCsvImportService();
    private final InvoiceDao invoiceDao = new InvoiceDao();
    private final ProductDao productDao = new ProductDao();

    private final TableView<InvoiceLine> table = new TableView<>();

    private final Label importedTotalValue = new Label("$0.00");
    private final Label calculatedTotalValue = new Label("$0.00");
    private final Label differenceValue = new Label("$0.00");

    private GfsCsvImportService.InvoiceImportResult currentInvoice;
    private List<InvoiceLine> currentLines;

    public BorderPane getView() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-dark");

        Label title = new Label("Import Invoice");
        title.getStyleClass().add("page-title");

        Button chooseFileButton = new Button("Choose GFS CSV");
        chooseFileButton.getStyleClass().add("primary-button");
        chooseFileButton.setOnAction(e -> chooseCsvFile());

        Button addLineButton = new Button("Add Manual Line");
        addLineButton.setOnAction(e -> addManualLine());

        Button editLineButton = new Button("Edit Selected Line");
        editLineButton.setOnAction(e -> editSelectedLine());

        Button removeLineButton = new Button("Remove Selected Line");
        removeLineButton.setOnAction(e -> removeSelectedLine());

        Button saveButton = new Button("Save Invoice");
        saveButton.getStyleClass().add("primary-button");
        saveButton.setOnAction(e -> saveInvoice());

        HBox topBar = new HBox(15, title, chooseFileButton, addLineButton, editLineButton, removeLineButton, saveButton);
        topBar.getStyleClass().add("top-bar");

        setupTable();

        root.setTop(new VBox(topBar, buildSummaryBar()));
        root.setCenter(table);

        return root;
    }

    private HBox buildSummaryBar() {
        HBox summaryBar = new HBox(20);

        summaryBar.getChildren().addAll(
                new Label("Imported Total:"),
                importedTotalValue,
                new Label("Preview Total:"),
                calculatedTotalValue,
                new Label("Difference:"),
                differenceValue
        );

        summaryBar.setStyle("-fx-padding: 10;");
        return summaryBar;
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
        caseCostCol.setCellFactory(column -> moneyCell());

        TableColumn<InvoiceLine, BigDecimal> eachCostCol = new TableColumn<>("Each Cost");
        eachCostCol.setCellValueFactory(new PropertyValueFactory<>("eachCost"));
        eachCostCol.setCellFactory(column -> moneyCell());

        TableColumn<InvoiceLine, BigDecimal> extCostCol = new TableColumn<>("Extended Cost");
        extCostCol.setCellValueFactory(new PropertyValueFactory<>("extendedCost"));
        extCostCol.setCellFactory(column -> moneyCell());

        table.getColumns().setAll(
                skuCol,
                descCol,
                caseQtyCol,
                splitQtyCol,
                packCol,
                caseCostCol,
                eachCostCol,
                extCostCol
        );

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    private TableCell<InvoiceLine, BigDecimal> moneyCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : formatMoney(value));
            }
        };
    }

    private void chooseCsvFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choose GFS CSV Invoice");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );

        File file = fileChooser.showOpenDialog(null);

        if (file == null) {
            return;
        }

        currentInvoice = importService.readInvoice(file);
        currentLines = currentInvoice.getLines();

        table.setItems(FXCollections.observableArrayList(currentLines));
        updateSummary();
    }

    private void addManualLine() {
        if (currentLines == null) {
            showAlert(Alert.AlertType.WARNING, "No Invoice Loaded", "Please choose a CSV before adding a manual line.");
            return;
        }

        Optional<InvoiceLine> result = showInvoiceLineDialog(null);

        result.ifPresent(line -> {
            currentLines.add(line);
            table.setItems(FXCollections.observableArrayList(currentLines));
            updateSummary();
        });
    }

    private void editSelectedLine() {
        InvoiceLine selected = table.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Line Selected", "Please select a line to edit.");
            return;
        }

        Optional<InvoiceLine> result = showInvoiceLineDialog(selected);

        result.ifPresent(updated -> {
            selected.setSku(updated.getSku());
            selected.setDescription(updated.getDescription());
            selected.setCaseQty(updated.getCaseQty());
            selected.setSplitQty(updated.getSplitQty());
            selected.setPackSize(updated.getPackSize());
            selected.setCaseCost(updated.getCaseCost());
            selected.setEachCost(updated.getEachCost());
            selected.setExtendedCost(updated.getExtendedCost());

            table.refresh();
            updateSummary();
        });
    }

    private void removeSelectedLine() {
        InvoiceLine selected = table.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Line Selected", "Please select a line to remove.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Remove Line");
        confirm.setHeaderText("Remove selected invoice line?");
        confirm.setContentText(selected.getSku() + " - " + selected.getDescription());

        Optional<ButtonType> result = confirm.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {
            currentLines.remove(selected);
            table.setItems(FXCollections.observableArrayList(currentLines));
            updateSummary();
        }
    }

    private Optional<InvoiceLine> showInvoiceLineDialog(InvoiceLine existingLine) {
        boolean editing = existingLine != null;

        Dialog<InvoiceLine> dialog = new Dialog<>();
        dialog.setTitle(editing ? "Edit Invoice Line" : "Add Manual Invoice Line");
        dialog.setHeaderText(editing ? "Edit the selected invoice line." : "Add a missing countable product line.");

        ButtonType saveButtonType = new ButtonType(editing ? "Save Changes" : "Add Line", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        TextField skuField = new TextField(editing ? existingLine.getSku() : "");
        TextField descriptionField = new TextField(editing ? existingLine.getDescription() : "");
        TextField caseQtyField = new TextField(editing ? String.valueOf(existingLine.getCaseQty()) : "0");
        TextField splitQtyField = new TextField(editing ? String.valueOf(existingLine.getSplitQty()) : "0");
        TextField packSizeField = new TextField(editing ? existingLine.getPackSize() : "");
        TextField caseCostField = new TextField(editing ? existingLine.getCaseCost().toPlainString() : "0.00");
        TextField eachCostField = new TextField(editing ? existingLine.getEachCost().toPlainString() : "0.00");
        TextField extendedCostField = new TextField(editing ? existingLine.getExtendedCost().toPlainString() : "0.00");

        CheckBox manualExtendedCostCheckBox = new CheckBox("Use entered extended cost");
        manualExtendedCostCheckBox.setSelected(editing && existingLine.hasManualExtendedCost());
        extendedCostField.setDisable(!manualExtendedCostCheckBox.isSelected());

        Label extendedCostNote = new Label(
                "Use this only when the supplier bills by actual weight or otherwise provides an extended amount " +
                        "that cannot be calculated from case and split quantities."
        );
        extendedCostNote.setWrapText(true);
        extendedCostNote.setMaxWidth(500);

        Runnable refreshCalculatedExtendedCost = () -> {
            if (!manualExtendedCostCheckBox.isSelected()) {
                BigDecimal calculated = InvoiceLine.calculateExtendedCost(
                        parseDouble(caseQtyField.getText()),
                        parseDouble(splitQtyField.getText()),
                        parseUnitCost(caseCostField.getText()),
                        parseUnitCost(eachCostField.getText())
                );
                extendedCostField.setText(calculated.toPlainString());
            }
        };

        manualExtendedCostCheckBox.selectedProperty().addListener((obs, oldValue, selected) -> {
            extendedCostField.setDisable(!selected);
            if (!selected) {
                refreshCalculatedExtendedCost.run();
            }
        });

        caseQtyField.textProperty().addListener((obs, oldValue, newValue) -> refreshCalculatedExtendedCost.run());
        splitQtyField.textProperty().addListener((obs, oldValue, newValue) -> refreshCalculatedExtendedCost.run());
        caseCostField.textProperty().addListener((obs, oldValue, newValue) -> refreshCalculatedExtendedCost.run());
        eachCostField.textProperty().addListener((obs, oldValue, newValue) -> refreshCalculatedExtendedCost.run());

        if (!manualExtendedCostCheckBox.isSelected()) {
            refreshCalculatedExtendedCost.run();
        }

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);

        grid.add(new Label("SKU:"), 0, 0);
        grid.add(skuField, 1, 0);

        grid.add(new Label("Description:"), 0, 1);
        grid.add(descriptionField, 1, 1);

        grid.add(new Label("Case Qty:"), 0, 2);
        grid.add(caseQtyField, 1, 2);

        grid.add(new Label("Split Qty:"), 0, 3);
        grid.add(splitQtyField, 1, 3);

        grid.add(new Label("Pack/Size:"), 0, 4);
        grid.add(packSizeField, 1, 4);

        grid.add(new Label("Case/Unit Cost:"), 0, 5);
        grid.add(caseCostField, 1, 5);

        grid.add(new Label("Each Cost:"), 0, 6);
        grid.add(eachCostField, 1, 6);

        grid.add(manualExtendedCostCheckBox, 1, 7);

        grid.add(new Label("Extended Cost:"), 0, 8);
        grid.add(extendedCostField, 1, 8);
        grid.add(extendedCostNote, 1, 9);

        dialog.getDialogPane().setContent(grid);

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.disableProperty().bind(
                skuField.textProperty().isEmpty()
                        .or(descriptionField.textProperty().isEmpty())
        );

        dialog.setResultConverter(button -> {
            if (button != saveButtonType) {
                return null;
            }

            try {
                BigDecimal extendedCost = manualExtendedCostCheckBox.isSelected()
                        ? parseMoney(extendedCostField.getText())
                        : null;

                return new InvoiceLine(
                        skuField.getText().trim(),
                        descriptionField.getText().trim(),
                        parseDouble(caseQtyField.getText()),
                        parseDouble(splitQtyField.getText()),
                        packSizeField.getText().trim(),
                        parseUnitCost(caseCostField.getText()),
                        parseUnitCost(eachCostField.getText()),
                        extendedCost
                );

            } catch (Exception ex) {
                showAlert(Alert.AlertType.ERROR, "Invalid Line", "Please check the quantities and costs.");
                return null;
            }
        });

        return dialog.showAndWait();
    }

    private void saveInvoice() {
        if (currentInvoice == null || currentLines == null || currentLines.isEmpty()) {
            showAlert(
                    Alert.AlertType.WARNING,
                    "No Invoice Loaded",
                    "Please choose a GFS CSV file before saving."
            );
            return;
        }

        List<InvoiceLine> unknownLines = findUnknownSkus();

        if (!unknownLines.isEmpty()) {
            boolean mappedAll = showUnknownSkuMappingDialog(unknownLines);

            if (!mappedAll) {
                return;
            }

            unknownLines = findUnknownSkus();

            if (!unknownLines.isEmpty()) {
                showAlert(
                        Alert.AlertType.WARNING,
                        "Unmapped SKUs Remaining",
                        "Some SKUs are still unmapped. Please map them before saving."
                );
                return;
            }
        }

        Optional<ConfirmedInvoiceDetails> confirmedDetails = showConfirmInvoiceDialog();

        confirmedDetails.ifPresent(details -> {
            try {
                if (invoiceDao.invoiceExists(details.invoiceNumber())) {

                    boolean overwrite =
                            showDuplicateInvoiceWarning(details.invoiceNumber());

                    if (!overwrite) {
                        return;
                    }

                    invoiceDao.deleteInvoice(details.invoiceNumber());
                }

                invoiceDao.saveInvoice(
                        "GFS",
                        details.invoiceNumber(),
                        details.invoiceDate(),
                        details.importedTotal(),
                        details.merchandiseSubtotal(),
                        details.invoiceTotal(),
                        details.adjustments(),
                        currentLines
                );

                showAlert(
                        Alert.AlertType.INFORMATION,
                        "Invoice Saved",
                        "Invoice saved successfully."
                );

            } catch (Exception ex) {
                ex.printStackTrace();

                showAlert(
                        Alert.AlertType.ERROR,
                        "Invoice Save Failed",
                        ex.getMessage()
                );
            }
        });
    }

    private boolean showUnknownSkuMappingDialog(List<InvoiceLine> unknownLines) {
        List<Product> products = loadSortedProducts();

        for (InvoiceLine line : unknownLines) {
            Optional<Product> selectedProduct = showSingleSkuMappingDialog(line, products);

            if (selectedProduct.isEmpty()) {
                return false;
            }

            Product product = selectedProduct.get();

            if (!product.getSku().equals(line.getSku())) {
                productDao.addSkuAlias(
                        product.getId(),
                        "GFS",
                        line.getSku(),
                        line.getDescription(),
                        line.getPackSize()
                );
            }

            products = loadSortedProducts();
        }

        return true;
    }

    private List<Product> loadSortedProducts() {
        return productDao.findAll()
                .stream()
                .sorted((a, b) -> {
                    int categoryCompare = a.getCategory().compareToIgnoreCase(b.getCategory());

                    if (categoryCompare != 0) {
                        return categoryCompare;
                    }

                    return a.getDescription().compareToIgnoreCase(b.getDescription());
                })
                .toList();
    }

    private Optional<Product> showSingleSkuMappingDialog(InvoiceLine line, List<Product> products) {
        Dialog<Product> dialog = new Dialog<>();
        dialog.setTitle("Map Unknown SKU");
        dialog.setHeaderText("Map this supplier SKU or create it as a new product.");

        ButtonType mapButtonType = new ButtonType("Map SKU", ButtonBar.ButtonData.OK_DONE);
        ButtonType createButtonType = new ButtonType("Create New Product", ButtonBar.ButtonData.APPLY);
        dialog.getDialogPane().getButtonTypes().addAll(mapButtonType, createButtonType, ButtonType.CANCEL);

        Label skuLabel = new Label(line.getSku());
        Label descriptionLabel = new Label(line.getDescription());
        Label packSizeLabel = new Label(line.getPackSize());

        ComboBox<Product> productComboBox = new ComboBox<>();
        productComboBox.setItems(FXCollections.observableArrayList(products));
        productComboBox.setPrefWidth(500);

        productComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Product product) {
                if (product == null) {
                    return "";
                }

                return "[" + product.getCategory() + "] " +
                        product.getDescription() +
                        " (" + product.getSku() + ")";
            }

            @Override
            public Product fromString(String string) {
                return null;
            }
        });

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);

        grid.add(new Label("Unknown SKU:"), 0, 0);
        grid.add(skuLabel, 1, 0);

        grid.add(new Label("Invoice Description:"), 0, 1);
        grid.add(descriptionLabel, 1, 1);

        grid.add(new Label("Pack Size:"), 0, 2);
        grid.add(packSizeLabel, 1, 2);

        grid.add(new Label("Map To Product:"), 0, 3);
        grid.add(productComboBox, 1, 3);

        Label createNote = new Label("Use Create New Product only if this is truly a new countable inventory item.");
        createNote.setWrapText(true);
        grid.add(createNote, 1, 4);

        dialog.getDialogPane().setContent(grid);

        Button mapButton = (Button) dialog.getDialogPane().lookupButton(mapButtonType);
        mapButton.disableProperty().bind(productComboBox.valueProperty().isNull());

        dialog.setResultConverter(button -> {
            if (button == mapButtonType) {
                return productComboBox.getValue();
            }

            if (button == createButtonType) {
                return productDao.createProductFromInvoiceLine(line);
            }

            return null;
        });

        return dialog.showAndWait();
    }

    private Optional<ConfirmedInvoiceDetails> showConfirmInvoiceDialog() {
        Dialog<ConfirmedInvoiceDetails> dialog = new Dialog<>();
        dialog.setTitle("Invoice Reconciliation");
        dialog.setHeaderText("Reconcile the imported merchandise with the paper invoice total.");

        ButtonType saveButtonType = new ButtonType("Save Invoice", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(650);

        TextField invoiceNumberField = new TextField(currentInvoice.getInvoiceNumber());
        TextField invoiceDateField = new TextField(currentInvoice.getInvoiceDate());
        TextField paperInvoiceTotalField = new TextField();
        paperInvoiceTotalField.setPromptText("0.00");

        BigDecimal importedTotal = currentInvoice.getInvoiceTotal()
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal merchandiseSubtotal = calculateTotal();

        Label importedTotalLabel = new Label(formatMoney(importedTotal));
        Label merchandiseSubtotalLabel = new Label(formatMoney(merchandiseSubtotal));
        Label calculatedGrandTotalLabel = new Label(formatMoney(merchandiseSubtotal));
        Label differenceLabel = new Label(formatMoney(BigDecimal.ZERO));
        differenceLabel.setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");

        VBox adjustmentsBox = new VBox(8);
        List<AdjustmentRow> adjustmentRows = new ArrayList<>();

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.setDisable(true);

        Runnable refreshTotals = () -> {
            BigDecimal adjustmentTotal = adjustmentRows.stream()
                    .map(row -> parseMoney(row.amountField().getText()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal calculatedGrandTotal = merchandiseSubtotal
                    .add(adjustmentTotal)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal paperInvoiceTotal = parseMoney(paperInvoiceTotalField.getText());
            BigDecimal difference = paperInvoiceTotal
                    .subtract(calculatedGrandTotal)
                    .setScale(2, RoundingMode.HALF_UP);

            calculatedGrandTotalLabel.setText(formatMoney(calculatedGrandTotal));
            differenceLabel.setText(formatMoney(difference));

            boolean balanced = !paperInvoiceTotalField.getText().isBlank()
                    && difference.compareTo(BigDecimal.ZERO) == 0;
            boolean requiredFieldsPresent = !invoiceNumberField.getText().isBlank()
                    && !invoiceDateField.getText().isBlank();

            if (balanced) {
                differenceLabel.setStyle("-fx-text-fill: lightgreen; -fx-font-weight: bold;");
            } else {
                differenceLabel.setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
            }

            saveButton.setDisable(!(balanced && requiredFieldsPresent));
        };

        addAdjustmentRow(adjustmentsBox, adjustmentRows, "Freight", BigDecimal.ZERO, refreshTotals);
        addAdjustmentRow(adjustmentsBox, adjustmentRows, "HST", BigDecimal.ZERO, refreshTotals);

        Button addAdjustmentButton = new Button("Add Adjustment");
        addAdjustmentButton.setOnAction(e ->
                addAdjustmentRow(adjustmentsBox, adjustmentRows, "", BigDecimal.ZERO, refreshTotals));

        paperInvoiceTotalField.textProperty().addListener((obs, oldValue, newValue) -> refreshTotals.run());
        invoiceNumberField.textProperty().addListener((obs, oldValue, newValue) -> refreshTotals.run());
        invoiceDateField.textProperty().addListener((obs, oldValue, newValue) -> refreshTotals.run());

        GridPane invoiceGrid = new GridPane();
        invoiceGrid.setHgap(12);
        invoiceGrid.setVgap(10);
        invoiceGrid.add(new Label("Invoice Number:"), 0, 0);
        invoiceGrid.add(invoiceNumberField, 1, 0);
        invoiceGrid.add(new Label("Invoice Date:"), 0, 1);
        invoiceGrid.add(invoiceDateField, 1, 1);
        invoiceGrid.add(new Label("Supplier:"), 0, 2);
        invoiceGrid.add(new Label("GFS"), 1, 2);

        GridPane totalsGrid = new GridPane();
        totalsGrid.setHgap(12);
        totalsGrid.setVgap(10);
        totalsGrid.add(new Label("CSV Imported Total:"), 0, 0);
        totalsGrid.add(importedTotalLabel, 1, 0);
        totalsGrid.add(new Label("Merchandise Subtotal:"), 0, 1);
        totalsGrid.add(merchandiseSubtotalLabel, 1, 1);
        totalsGrid.add(new Label("Calculated Grand Total:"), 0, 2);
        totalsGrid.add(calculatedGrandTotalLabel, 1, 2);
        totalsGrid.add(new Label("Paper Invoice Total:"), 0, 3);
        totalsGrid.add(paperInvoiceTotalField, 1, 3);
        totalsGrid.add(new Label("Difference:"), 0, 4);
        totalsGrid.add(differenceLabel, 1, 4);

        Label adjustmentsHeading = new Label("Adjustments");
        adjustmentsHeading.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        VBox content = new VBox(14,
                invoiceGrid,
                new Separator(),
                new Label("Merchandise"),
                new Label("The merchandise subtotal is the only amount used for inventory valuation and cost reporting."),
                new Separator(),
                adjustmentsHeading,
                adjustmentsBox,
                addAdjustmentButton,
                new Separator(),
                totalsGrid
        );
        content.setStyle("-fx-padding: 5;");
        dialog.getDialogPane().setContent(content);

        refreshTotals.run();

        dialog.setResultConverter(button -> {
            if (button != saveButtonType) {
                return null;
            }

            List<InvoiceAdjustment> adjustments = new ArrayList<>();
            int displayOrder = 10;
            for (AdjustmentRow row : adjustmentRows) {
                String description = row.descriptionField().getText().trim();
                BigDecimal amount = parseMoney(row.amountField().getText());
                if (!description.isBlank()) {
                    adjustments.add(new InvoiceAdjustment(description, amount, displayOrder));
                    displayOrder += 10;
                }
            }

            BigDecimal adjustmentTotal = adjustments.stream()
                    .map(InvoiceAdjustment::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal grandTotal = merchandiseSubtotal.add(adjustmentTotal)
                    .setScale(2, RoundingMode.HALF_UP);

            return new ConfirmedInvoiceDetails(
                    invoiceNumberField.getText().trim(),
                    invoiceDateField.getText().trim(),
                    importedTotal,
                    merchandiseSubtotal,
                    grandTotal,
                    adjustments
            );
        });

        return dialog.showAndWait();
    }

    private void addAdjustmentRow(
            VBox adjustmentsBox,
            List<AdjustmentRow> adjustmentRows,
            String description,
            BigDecimal amount,
            Runnable refreshTotals
    ) {
        TextField descriptionField = new TextField(description);
        descriptionField.setPromptText("Description");
        descriptionField.setPrefWidth(260);

        TextField amountField = new TextField(amount.setScale(2, RoundingMode.HALF_UP).toPlainString());
        amountField.setPromptText("0.00");
        amountField.setPrefWidth(120);

        Button removeButton = new Button("Remove");
        HBox rowBox = new HBox(10, descriptionField, amountField, removeButton);
        AdjustmentRow row = new AdjustmentRow(descriptionField, amountField, rowBox);
        adjustmentRows.add(row);
        adjustmentsBox.getChildren().add(rowBox);

        amountField.textProperty().addListener((obs, oldValue, newValue) -> refreshTotals.run());
        descriptionField.textProperty().addListener((obs, oldValue, newValue) -> refreshTotals.run());
        removeButton.setOnAction(e -> {
            adjustmentRows.remove(row);
            adjustmentsBox.getChildren().remove(rowBox);
            refreshTotals.run();
        });

        refreshTotals.run();
    }

    private boolean showDuplicateInvoiceWarning(String invoiceNumber) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Duplicate Invoice");
        alert.setHeaderText("This invoice number already exists.");
        alert.setContentText(
                "Invoice Number: " + invoiceNumber + "\n\n" +
                        "Do you want to overwrite the existing invoice?"
        );

        ButtonType saveAnywayButton = new ButtonType("Overwrite", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(saveAnywayButton, cancelButton);

        Optional<ButtonType> result = alert.showAndWait();

        return result.isPresent() && result.get() == saveAnywayButton;
    }

    private void updateSummary() {
        BigDecimal importedTotal = currentInvoice == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : currentInvoice.getInvoiceTotal();

        BigDecimal calculatedTotal = calculateTotal();
        BigDecimal difference = calculatedTotal.subtract(importedTotal).setScale(2, RoundingMode.HALF_UP);

        importedTotalValue.setText(formatMoney(importedTotal));
        calculatedTotalValue.setText(formatMoney(calculatedTotal));
        differenceValue.setText(formatMoney(difference));

        if (difference.compareTo(BigDecimal.ZERO) == 0) {
            differenceValue.setStyle("-fx-text-fill: lightgreen; -fx-font-weight: bold;");
        } else {
            differenceValue.setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
        }
    }

    private BigDecimal calculateTotal() {
        if (currentLines == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return currentLines.stream()
                .map(InvoiceLine::getExtendedCost)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private List<InvoiceLine> findUnknownSkus() {
        return currentLines.stream()
                .filter(line -> productDao.findIdBySkuOrAlias(line.getSku()) == null)
                .toList();
    }

    private double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }

        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }


    private BigDecimal parseUnitCost(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }

        try {
            return new BigDecimal(
                    value.replace("$", "")
                            .replace(",", "")
                            .trim()
            ).setScale(4, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
    }

    private BigDecimal parseMoney(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        try {
            return new BigDecimal(
                    value.replace("$", "")
                            .replace(",", "")
                            .trim()
            ).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private String formatMoney(BigDecimal value) {
        if (value == null) {
            value = BigDecimal.ZERO;
        }

        return "$" + value.setScale(2, RoundingMode.HALF_UP);
    }

    private record AdjustmentRow(
            TextField descriptionField,
            TextField amountField,
            HBox container
    ) {
    }

    private record ConfirmedInvoiceDetails(
            String invoiceNumber,
            String invoiceDate,
            BigDecimal importedTotal,
            BigDecimal merchandiseSubtotal,
            BigDecimal invoiceTotal,
            List<InvoiceAdjustment> adjustments
    ) {
    }
}