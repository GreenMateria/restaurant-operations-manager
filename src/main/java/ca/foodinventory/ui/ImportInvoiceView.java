package ca.foodinventory.ui;

import ca.foodinventory.dao.InvoiceDao;
import ca.foodinventory.dao.ProductDao;
import ca.foodinventory.model.InvoiceLine;
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
            selected.recalculateExtendedCost();

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

        grid.add(new Label("Case Cost:"), 0, 5);
        grid.add(caseCostField, 1, 5);

        grid.add(new Label("Each Cost:"), 0, 6);
        grid.add(eachCostField, 1, 6);

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
                InvoiceLine line = new InvoiceLine(
                        skuField.getText().trim(),
                        descriptionField.getText().trim(),
                        parseDouble(caseQtyField.getText()),
                        parseDouble(splitQtyField.getText()),
                        packSizeField.getText().trim(),
                        parseMoney(caseCostField.getText()),
                        parseMoney(eachCostField.getText()),
                        null
                );

                line.recalculateExtendedCost();
                return line;

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
                        details.invoiceTotal(),
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
        dialog.setTitle("Confirm Invoice");
        dialog.setHeaderText("Verify invoice details before saving.");

        ButtonType saveButtonType = new ButtonType("Save Invoice", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        TextField invoiceNumberField = new TextField(currentInvoice.getInvoiceNumber());
        TextField invoiceDateField = new TextField(currentInvoice.getInvoiceDate());

        BigDecimal importedTotal = currentInvoice.getInvoiceTotal();
        BigDecimal calculatedTotal = calculateTotal();
        BigDecimal difference = calculatedTotal.subtract(importedTotal).setScale(2, RoundingMode.HALF_UP);

        Label importedTotalLabel = new Label(formatMoney(importedTotal));
        Label calculatedTotalLabel = new Label(formatMoney(calculatedTotal));
        Label differenceLabel = new Label(formatMoney(difference));

        if (difference.compareTo(BigDecimal.ZERO) != 0) {
            differenceLabel.setStyle("-fx-text-fill: orange; -fx-font-weight: bold;");
        }

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);

        grid.add(new Label("Invoice Number:"), 0, 0);
        grid.add(invoiceNumberField, 1, 0);

        grid.add(new Label("Invoice Date:"), 0, 1);
        grid.add(invoiceDateField, 1, 1);

        grid.add(new Label("Imported Total:"), 0, 2);
        grid.add(importedTotalLabel, 1, 2);

        grid.add(new Label("Preview Total:"), 0, 3);
        grid.add(calculatedTotalLabel, 1, 3);

        grid.add(new Label("Difference:"), 0, 4);
        grid.add(differenceLabel, 1, 4);

        dialog.getDialogPane().setContent(grid);

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.disableProperty().bind(
                invoiceNumberField.textProperty().isEmpty()
                        .or(invoiceDateField.textProperty().isEmpty())
        );

        dialog.setResultConverter(button -> {
            if (button == saveButtonType) {
                return new ConfirmedInvoiceDetails(
                        invoiceNumberField.getText().trim(),
                        invoiceDateField.getText().trim(),
                        importedTotal
                );
            }

            return null;
        });

        return dialog.showAndWait();
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

    private record ConfirmedInvoiceDetails(
            String invoiceNumber,
            String invoiceDate,
            BigDecimal invoiceTotal
    ) {
    }
}