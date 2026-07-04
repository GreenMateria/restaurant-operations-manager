package ca.foodinventory.ui;

import ca.foodinventory.dao.InventoryCountTemplateLineDao;
import ca.foodinventory.dao.ProductDao;
import ca.foodinventory.model.InventoryCountTemplate;
import ca.foodinventory.model.InventoryCountTemplateLine;
import ca.foodinventory.model.Product;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.ArrayList;
import java.util.List;

public class InventoryCountTemplateEditorView {

    private static final int LINES_PER_PAGE = 30;

    private static final double PRODUCT_COL_WIDTH = 315;
    private static final double UNIT_COL_WIDTH = 65;
    private static final double COUNT_COL_WIDTH = 150;
    private static final double SHEET_WIDTH =
            PRODUCT_COL_WIDTH + UNIT_COL_WIDTH + COUNT_COL_WIDTH;

    private final InventoryCountTemplate template;
    private final InventoryCountTemplateLineDao lineDao = new InventoryCountTemplateLineDao();
    private final ProductDao productDao = new ProductDao();

    private final TableView<InventoryCountTemplateLine> table = new TableView<>();

    public InventoryCountTemplateEditorView(InventoryCountTemplate template) {
        this.template = template;
    }

    public BorderPane getView() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-dark");

        Label title = new Label("Edit Count Template: " + template.getName());
        title.getStyleClass().add("page-title");

        Button addButton = new Button("Add Product");
        Button editButton = new Button("Edit Product");
        Button removeButton = new Button("Remove Product");
        Button moveUpButton = new Button("Move Up");
        Button moveDownButton = new Button("Move Down");
        Button renumberButton = new Button("Renumber");
        Button printBlankButton = new Button("Print Blank Count Sheet");

        HBox buttons = new HBox(
                10,
                addButton,
                editButton,
                removeButton,
                moveUpButton,
                moveDownButton,
                renumberButton,
                printBlankButton
        );

        VBox top = new VBox(10, title, buttons);
        top.setStyle("-fx-padding: 15;");

        setupTable();

        addButton.setOnAction(e -> showAddProductDialog());
        removeButton.setOnAction(e -> removeSelectedLine());
        editButton.setOnAction(e -> editSelectedLine());
        moveUpButton.setOnAction(e -> moveSelectedLine(-1));
        moveDownButton.setOnAction(e -> moveSelectedLine(1));
        renumberButton.setOnAction(e -> renumberCurrentOrder());
        printBlankButton.setOnAction(e -> printBlankCountSheet());

        root.setTop(top);
        root.setCenter(table);

        refreshTable();

        return root;
    }

    private void setupTable() {
        TableColumn<InventoryCountTemplateLine, String> skuCol = new TableColumn<>("SKU");
        skuCol.setCellValueFactory(new PropertyValueFactory<>("sku"));
        skuCol.setPrefWidth(120);

        TableColumn<InventoryCountTemplateLine, String> productDescCol = new TableColumn<>("Product");
        productDescCol.setCellValueFactory(new PropertyValueFactory<>("productDescription"));
        productDescCol.setPrefWidth(250);

        TableColumn<InventoryCountTemplateLine, String> sectionCol = new TableColumn<>("Section");
        sectionCol.setCellValueFactory(new PropertyValueFactory<>("sectionName"));
        sectionCol.setPrefWidth(180);

        TableColumn<InventoryCountTemplateLine, String> displayCol = new TableColumn<>("Display Name");
        displayCol.setCellValueFactory(new PropertyValueFactory<>("displayName"));
        displayCol.setPrefWidth(260);

        TableColumn<InventoryCountTemplateLine, String> unitCol = new TableColumn<>("Count Unit");
        unitCol.setCellValueFactory(new PropertyValueFactory<>("countUnit"));
        unitCol.setPrefWidth(120);

        TableColumn<InventoryCountTemplateLine, Double> factorCol = new TableColumn<>("Conversion Factor");
        factorCol.setCellValueFactory(new PropertyValueFactory<>("conversionFactorToBase"));
        factorCol.setPrefWidth(150);

        TableColumn<InventoryCountTemplateLine, Integer> sortCol = new TableColumn<>("Sort Order");
        sortCol.setCellValueFactory(new PropertyValueFactory<>("sortOrder"));
        sortCol.setPrefWidth(100);

        table.getColumns().setAll(
                skuCol,
                productDescCol,
                sectionCol,
                displayCol,
                unitCol,
                factorCol,
                sortCol
        );
    }

    private void refreshTable() {
        List<InventoryCountTemplateLine> lines = lineDao.findByTemplate(template.getId());
        table.setItems(FXCollections.observableArrayList(lines));
    }

    private void showAddProductDialog() {
        Dialog<InventoryCountTemplateLine> dialog = new Dialog<>();
        dialog.setTitle("Add Product to Count Template");

        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        List<Product> allProducts = productDao.findAll();

        TextField productSearchField = new TextField();
        productSearchField.setPromptText("Search by SKU or description...");

        ComboBox<Product> productBox = new ComboBox<>();
        productBox.setItems(FXCollections.observableArrayList(allProducts));
        productBox.setPrefWidth(350);
        productBox.setVisibleRowCount(12);

        productSearchField.textProperty().addListener((obs, oldValue, newValue) -> {
            String search = newValue == null ? "" : newValue.toLowerCase().trim();

            List<Product> filteredProducts = allProducts.stream()
                    .filter(product ->
                            product.getSku().toLowerCase().contains(search)
                                    || product.getDescription().toLowerCase().contains(search)
                    )
                    .toList();

            productBox.setItems(FXCollections.observableArrayList(filteredProducts));

            if (!filteredProducts.isEmpty()) {
                productBox.show();
            }

            if (filteredProducts.size() == 1) {
                productBox.getSelectionModel().select(0);
            }
        });

        productBox.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(Product product, boolean empty) {
                super.updateItem(product, empty);
                setText(empty || product == null ? null : product.getSku() + " - " + product.getDescription());
            }
        });

        productBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Product product, boolean empty) {
                super.updateItem(product, empty);
                setText(empty || product == null ? null : product.getSku() + " - " + product.getDescription());
            }
        });

        ComboBox<String> sectionBox = new ComboBox<>();
        sectionBox.setItems(FXCollections.observableArrayList(
                "Walk-In Cooler",
                "Freezer",
                "Dry Storage",
                "Produce Cooler",
                "Bar",
                "Kitchen Line",
                "Prep Area",
                "Chemical Room",
                "Office",
                "Other"
        ));

        sectionBox.setEditable(true);
        sectionBox.setPrefWidth(350);
        sectionBox.setPromptText("Select or type section...");

        TextField displayNameField = new TextField();
        displayNameField.setPromptText("Display name on count sheet");

        TextField countUnitField = new TextField();
        countUnitField.setPromptText("EA, KG, LB, CASE, BAG");

        TextField conversionFactorField = new TextField("1");

        TextField sortOrderField = new TextField(String.valueOf(table.getItems().size() + 1));

        productBox.setOnAction(e -> {
            Product selected = productBox.getSelectionModel().getSelectedItem();

            if (selected != null) {
                displayNameField.setText(selected.getDescription());
                countUnitField.setText(selected.getUnit());
            }
        });

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setStyle("-fx-padding: 15;");

        grid.add(new Label("Search:"), 0, 0);
        grid.add(productSearchField, 1, 0);

        grid.add(new Label("Product:"), 0, 1);
        grid.add(productBox, 1, 1);

        grid.add(new Label("Section:"), 0, 2);
        grid.add(sectionBox, 1, 2);

        grid.add(new Label("Display Name:"), 0, 3);
        grid.add(displayNameField, 1, 3);

        grid.add(new Label("Count Unit:"), 0, 4);
        grid.add(countUnitField, 1, 4);

        grid.add(new Label("Conversion Factor:"), 0, 5);
        grid.add(conversionFactorField, 1, 5);

        grid.add(new Label("Sort Order:"), 0, 6);
        grid.add(sortOrderField, 1, 6);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> {
            if (button == addButtonType) {
                Product selectedProduct = productBox.getSelectionModel().getSelectedItem();

                if (selectedProduct == null) {
                    return null;
                }

                InventoryCountTemplateLine line = new InventoryCountTemplateLine();

                line.setTemplateId(template.getId());
                line.setProductId(selectedProduct.getId());
                line.setSectionName(sectionBox.getEditor().getText());
                line.setDisplayName(displayNameField.getText());
                line.setCountUnit(countUnitField.getText());
                line.setConversionFactorToBase(Double.parseDouble(conversionFactorField.getText()));
                line.setSortOrder(Integer.parseInt(sortOrderField.getText()));
                line.setActive(true);

                return line;
            }

            return null;
        });

        dialog.showAndWait().ifPresent(line -> {
            if (lineDao.productExistsInTemplate(line.getTemplateId(), line.getProductId())) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Duplicate Product");
                alert.setHeaderText(null);
                alert.setContentText("This product is already in this count template.");
                alert.showAndWait();
                return;
            }

            lineDao.add(line);
            refreshTable();
        });
    }

    private void removeSelectedLine() {
        InventoryCountTemplateLine selected = table.getSelectionModel().getSelectedItem();

        if (selected == null) {
            return;
        }

        lineDao.deactivate(selected.getId());
        refreshTable();
    }

    private void editSelectedLine() {
        InventoryCountTemplateLine selected = table.getSelectionModel().getSelectedItem();

        if (selected == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("No Product Selected");
            alert.setHeaderText(null);
            alert.setContentText("Please select a product to edit.");
            alert.showAndWait();
            return;
        }

        Dialog<InventoryCountTemplateLine> dialog = new Dialog<>();
        dialog.setTitle("Edit Template Product");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);

        dialog.getDialogPane().getButtonTypes().addAll(
                saveButtonType,
                ButtonType.CANCEL
        );

        ComboBox<String> sectionBox = new ComboBox<>();
        sectionBox.setItems(FXCollections.observableArrayList(
                "Walk-In Cooler",
                "Freezer",
                "Dry Storage",
                "Produce Cooler",
                "Bar",
                "Kitchen Line",
                "Prep Area",
                "Chemical Room",
                "Office",
                "Other"
        ));

        sectionBox.setEditable(true);
        sectionBox.setPrefWidth(350);
        sectionBox.getEditor().setText(selected.getSectionName());

        TextField displayNameField = new TextField(selected.getDisplayName());
        TextField countUnitField = new TextField(selected.getCountUnit());
        TextField conversionFactorField = new TextField(String.valueOf(selected.getConversionFactorToBase()));
        TextField sortOrderField = new TextField(String.valueOf(selected.getSortOrder()));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setStyle("-fx-padding: 15;");

        grid.add(new Label("Product:"), 0, 0);
        grid.add(new Label(selected.getSku() + " - " + selected.getProductDescription()), 1, 0);

        grid.add(new Label("Section:"), 0, 1);
        grid.add(sectionBox, 1, 1);

        grid.add(new Label("Display Name:"), 0, 2);
        grid.add(displayNameField, 1, 2);

        grid.add(new Label("Count Unit:"), 0, 3);
        grid.add(countUnitField, 1, 3);

        grid.add(new Label("Conversion Factor:"), 0, 4);
        grid.add(conversionFactorField, 1, 4);

        grid.add(new Label("Sort Order:"), 0, 5);
        grid.add(sortOrderField, 1, 5);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> {
            if (button == saveButtonType) {
                selected.setSectionName(sectionBox.getEditor().getText().trim());
                selected.setDisplayName(displayNameField.getText().trim());
                selected.setCountUnit(countUnitField.getText().trim());
                selected.setConversionFactorToBase(Double.parseDouble(conversionFactorField.getText().trim()));
                selected.setSortOrder(Integer.parseInt(sortOrderField.getText().trim()));

                return selected;
            }

            return null;
        });

        dialog.showAndWait().ifPresent(line -> {
            lineDao.update(line);
            refreshTable();
        });
    }

    private void moveSelectedLine(int direction) {
        InventoryCountTemplateLine selected = table.getSelectionModel().getSelectedItem();

        if (selected == null) {
            return;
        }

        int currentIndex = table.getItems().indexOf(selected);
        int newIndex = currentIndex + direction;

        if (newIndex < 0 || newIndex >= table.getItems().size()) {
            return;
        }

        table.getItems().remove(currentIndex);
        table.getItems().add(newIndex, selected);

        lineDao.updateSortOrders(table.getItems());

        refreshTable();
        table.getSelectionModel().select(newIndex);
    }

    private void renumberCurrentOrder() {
        if (table.getItems().isEmpty()) {
            return;
        }

        lineDao.updateSortOrders(table.getItems());
        refreshTable();
    }

    private void printBlankCountSheet() {
        List<InventoryCountTemplateLine> lines = lineDao.findByTemplate(template.getId());

        if (lines.isEmpty()) {
            showSimpleAlert(
                    Alert.AlertType.INFORMATION,
                    "Blank Count Sheet",
                    "This template has no active products to print."
            );
            return;
        }

        PrinterJob job = PrinterJob.createPrinterJob();

        if (job == null) {
            return;
        }

        Printer printer = job.getPrinter();

        PageLayout pageLayout = printer.createPageLayout(
                Paper.NA_LETTER,
                PageOrientation.PORTRAIT,
                Printer.MarginType.HARDWARE_MINIMUM
        );

        job.getJobSettings().setPageLayout(pageLayout);

        if (!job.showPrintDialog(table.getScene().getWindow())) {
            return;
        }

        List<List<PrintRow>> pageRows = paginateCountSheet(lines);
        List<Node> pages = buildCountSheetPages(pageRows);

        boolean success = true;

        for (Node page : pages) {
            success = job.printPage(pageLayout, page);

            if (!success) {
                break;
            }
        }

        if (success) {
            job.endJob();
        }
    }

    private List<List<PrintRow>> paginateCountSheet(List<InventoryCountTemplateLine> lines) {
        List<List<PrintRow>> pages = new ArrayList<>();

        int index = 0;

        while (index < lines.size()) {
            List<PrintRow> page = new ArrayList<>();
            int rowsUsed = 0;
            String currentSectionOnPage = null;

            while (index < lines.size() && rowsUsed < LINES_PER_PAGE) {
                InventoryCountTemplateLine line = lines.get(index);
                String section = cleanSectionName(line.getSectionName());

                boolean needsSectionHeader =
                        currentSectionOnPage == null || !currentSectionOnPage.equals(section);

                if (needsSectionHeader) {
                    int sectionItemCount = countRemainingItemsInSection(lines, index, section);
                    int minimumRowsNeeded = 1 + Math.min(sectionItemCount, 3);

                    if (!page.isEmpty() && rowsUsed + minimumRowsNeeded > LINES_PER_PAGE) {
                        break;
                    }

                    page.add(PrintRow.section(section));
                    rowsUsed++;
                    currentSectionOnPage = section;
                }

                if (rowsUsed >= LINES_PER_PAGE) {
                    break;
                }

                page.add(PrintRow.item(line));
                rowsUsed++;
                index++;
            }

            pages.add(page);
        }

        return pages;
    }

    private int countRemainingItemsInSection(
            List<InventoryCountTemplateLine> lines,
            int startIndex,
            String section
    ) {
        int count = 0;

        for (int i = startIndex; i < lines.size(); i++) {
            String lineSection = cleanSectionName(lines.get(i).getSectionName());

            if (!section.equals(lineSection)) {
                break;
            }

            count++;
        }

        return count;
    }

    private List<Node> buildCountSheetPages(List<List<PrintRow>> pageRows) {
        List<Node> pages = new ArrayList<>();
        int totalPages = pageRows.size();

        for (int i = 0; i < pageRows.size(); i++) {
            pages.add(createCountSheetPage(
                    pageRows.get(i),
                    i + 1,
                    totalPages,
                    i == pageRows.size() - 1
            ));
        }

        return pages;
    }

    private Node createCountSheetPage(
            List<PrintRow> rows,
            int pageNumber,
            int totalPages,
            boolean lastPage
    ) {
        VBox page = new VBox(4);
        page.setPadding(new Insets(18));
        page.setPrefWidth(SHEET_WIDTH + 36);
        page.setMaxWidth(SHEET_WIDTH + 36);
        page.setStyle("-fx-background-color: white;");

        VBox header = createCountSheetHeader(pageNumber, totalPages);
        GridPane tableHeader = createCountSheetTableHeader();

        VBox body = new VBox(0);

        for (PrintRow row : rows) {
            if (row.sectionHeader()) {
                body.getChildren().add(createSectionHeader(row.sectionName()));
            } else {
                body.getChildren().add(createCountSheetDataRow(row.line()));
            }
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        page.getChildren().addAll(header, tableHeader, body, spacer);

        if (lastPage) {
            page.getChildren().add(createSignatureArea());
        }

        page.getChildren().add(createPageFooter(pageNumber, totalPages));

        return page;
    }

    private VBox createCountSheetHeader(int pageNumber, int totalPages) {
        VBox header = new VBox(3);
        header.setAlignment(Pos.CENTER);
        header.setPrefWidth(SHEET_WIDTH);

        Label company = new Label("EAST SIDE MARIO'S");
        company.setFont(Font.font("Arial", FontWeight.BOLD, 17));
        company.setStyle("-fx-text-fill: black;");

        Label title = new Label("INVENTORY COUNT SHEET");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        title.setStyle("-fx-text-fill: black;");

        Label templateLabel = new Label("Template: " + template.getName());
        templateLabel.setFont(Font.font("Arial", 9));
        templateLabel.setStyle("-fx-text-fill: black;");

        GridPane infoGrid = new GridPane();
        infoGrid.setHgap(14);
        infoGrid.setVgap(3);
        infoGrid.setPadding(new Insets(6, 0, 6, 0));
        infoGrid.setAlignment(Pos.CENTER);

        addInfoLabel(infoGrid, "Count Date: __________________", 0, 0);
        addInfoLabel(infoGrid, "Manager: __________________", 1, 0);
        addInfoLabel(infoGrid, "Completed By: ________________", 0, 1);
        addInfoLabel(infoGrid, "Page " + pageNumber + " of " + totalPages, 1, 1);

        header.getChildren().addAll(company, title, templateLabel, infoGrid);

        return header;
    }

    private void addInfoLabel(GridPane grid, String text, int column, int row) {
        Label label = new Label(text);
        label.setFont(Font.font("Arial", 8.5));
        label.setStyle("-fx-text-fill: black;");
        grid.add(label, column, row);
    }

    private GridPane createCountSheetTableHeader() {
        GridPane grid = createCountSheetBaseGrid();

        grid.setStyle(
                "-fx-background-color: #f2f2f2;" +
                        "-fx-border-color: black;" +
                        "-fx-border-width: 1;"
        );

        addHeaderCell(grid, "Product", 0, Pos.CENTER_LEFT);
        addHeaderCell(grid, "Unit", 1, Pos.CENTER);
        addHeaderCell(grid, "Count", 2, Pos.CENTER);

        return grid;
    }

    private Label createSectionHeader(String sectionName) {
        Label label = new Label(sectionName);
        label.setPrefWidth(SHEET_WIDTH);
        label.setMaxWidth(SHEET_WIDTH);
        label.setFont(Font.font("Arial", FontWeight.BOLD, 10));
        label.setStyle(
                "-fx-background-color: #008EAA;" +
                        "-fx-text-fill: white;" +
                        "-fx-padding: 4 6 4 6;" +
                        "-fx-border-color: black;" +
                        "-fx-border-width: 1 1 0 1;"
        );

        return label;
    }

    private GridPane createCountSheetDataRow(InventoryCountTemplateLine line) {
        GridPane grid = createCountSheetBaseGrid();

        grid.setStyle(
                "-fx-border-color: black;" +
                        "-fx-border-width: 0 1 1 1;"
        );

        String productName = line.getDisplayName();

        if (productName == null || productName.isBlank()) {
            productName = line.getProductDescription();
        }

        addDataCell(grid, productName, 0, Pos.CENTER_LEFT);
        addDataCell(grid, line.getCountUnit(), 1, Pos.CENTER);
        addDataCell(grid, "", 2, Pos.CENTER);

        return grid;
    }

    private GridPane createCountSheetBaseGrid() {
        GridPane grid = new GridPane();
        grid.setPrefWidth(SHEET_WIDTH);
        grid.setMaxWidth(SHEET_WIDTH);

        ColumnConstraints product = new ColumnConstraints(PRODUCT_COL_WIDTH);
        ColumnConstraints unit = new ColumnConstraints(UNIT_COL_WIDTH);
        ColumnConstraints count = new ColumnConstraints(COUNT_COL_WIDTH);

        product.setMinWidth(PRODUCT_COL_WIDTH);
        product.setMaxWidth(PRODUCT_COL_WIDTH);

        unit.setMinWidth(UNIT_COL_WIDTH);
        unit.setMaxWidth(UNIT_COL_WIDTH);

        count.setMinWidth(COUNT_COL_WIDTH);
        count.setMaxWidth(COUNT_COL_WIDTH);

        grid.getColumnConstraints().addAll(product, unit, count);

        return grid;
    }

    private void addHeaderCell(
            GridPane grid,
            String text,
            int column,
            Pos alignment
    ) {
        Label label = new Label(text);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setMinHeight(20);
        label.setFont(Font.font("Arial", FontWeight.BOLD, 9));
        label.setAlignment(alignment);
        label.setStyle(
                "-fx-text-fill: black;" +
                        "-fx-padding: 3 5 3 5;" +
                        "-fx-border-color: black;" +
                        "-fx-border-width: 0 1 0 0;"
        );

        grid.add(label, column, 0);
    }

    private void addDataCell(
            GridPane grid,
            String text,
            int column,
            Pos alignment
    ) {
        Label label = new Label(text == null ? "" : text);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setMinHeight(20);
        label.setFont(Font.font("Arial", 8.5));
        label.setAlignment(alignment);
        label.setStyle(
                "-fx-text-fill: black;" +
                        "-fx-padding: 2 5 2 5;" +
                        "-fx-border-color: black;" +
                        "-fx-border-width: 0 1 0 0;"
        );

        grid.add(label, column, 0);
    }

    private HBox createSignatureArea() {
        HBox signatures = new HBox(26);
        signatures.setPadding(new Insets(12, 0, 4, 0));
        signatures.setAlignment(Pos.CENTER);
        signatures.setPrefWidth(SHEET_WIDTH);

        signatures.getChildren().addAll(
                createSignatureBox("Completed By"),
                createSignatureBox("Verified By"),
                createSignatureBox("Date")
        );

        return signatures;
    }

    private VBox createSignatureBox(String labelText) {
        VBox box = new VBox(4);
        box.setAlignment(Pos.CENTER);

        Label line = new Label("______________________");
        line.setFont(Font.font("Arial", 9));
        line.setStyle("-fx-text-fill: black;");

        Label label = new Label(labelText);
        label.setFont(Font.font("Arial", 8));
        label.setStyle("-fx-text-fill: black;");

        box.getChildren().addAll(line, label);

        return box;
    }

    private HBox createPageFooter(int pageNumber, int totalPages) {
        HBox footer = new HBox();
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPrefWidth(SHEET_WIDTH);

        Label label = new Label("Page " + pageNumber + " of " + totalPages);
        label.setFont(Font.font("Arial", 8));
        label.setStyle("-fx-text-fill: black;");

        footer.getChildren().add(label);

        return footer;
    }

    private String cleanSectionName(String sectionName) {
        if (sectionName == null || sectionName.isBlank()) {
            return "OTHER";
        }

        return sectionName.trim().toUpperCase();
    }

    private void showSimpleAlert(
            Alert.AlertType type,
            String title,
            String message
    ) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private record PrintRow(
            boolean sectionHeader,
            String sectionName,
            InventoryCountTemplateLine line
    ) {
        static PrintRow section(String sectionName) {
            return new PrintRow(true, sectionName, null);
        }

        static PrintRow item(InventoryCountTemplateLine line) {
            return new PrintRow(false, null, line);
        }
    }
}