package ca.foodinventory.ui;

import ca.foodinventory.dao.AlcoholProductProfileDao;
import ca.foodinventory.dao.InventoryCountTemplateLineDao;
import ca.foodinventory.dao.ProductDao;
import ca.foodinventory.model.AlcoholProductProfile;
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
    private final AlcoholProductProfileDao alcoholProfileDao = new AlcoholProductProfileDao();

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
            showSimpleAlert(
                    Alert.AlertType.WARNING,
                    "No Product Selected",
                    "Please select a product to remove."
            );
            return;
        }

        String productName = selected.getDisplayName();
        if (productName == null || productName.isBlank()) {
            productName = selected.getProductDescription();
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Remove Product From Template");
        confirm.setHeaderText(null);
        confirm.setContentText(
                "Remove \"" + productName + "\" from this count template?\n\n"
                        + "This will not delete the product from the product list."
        );

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
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

        PageLayout pageLayout = job.getPrinter().createPageLayout(
                Paper.NA_LETTER,
                PageOrientation.PORTRAIT,
                Printer.MarginType.HARDWARE_MINIMUM
        );
        job.getJobSettings().setPageLayout(pageLayout);

        if (!job.showPrintDialog(table.getScene().getWindow())) {
            return;
        }

        List<List<TemplatePrintRow>> pages = paginateTemplateLines(lines, 27);
        boolean success = true;

        for (int i = 0; i < pages.size(); i++) {
            Node page = buildTemplatePrintPage(pages.get(i), i + 1, pages.size());
            page.applyCss();
            page.autosize();

            if (!job.printPage(pageLayout, page)) {
                success = false;
                break;
            }
        }

        if (success) {
            job.endJob();
        }
    }

    private List<List<TemplatePrintRow>> paginateTemplateLines(
            List<InventoryCountTemplateLine> lines,
            int maxRows
    ) {
        List<List<TemplatePrintRow>> pages = new ArrayList<>();
        int index = 0;

        while (index < lines.size()) {
            List<TemplatePrintRow> page = new ArrayList<>();
            int used = 0;
            String currentSection = null;

            while (index < lines.size()) {
                InventoryCountTemplateLine line = lines.get(index);
                String section = cleanSectionName(line.getSectionName());
                boolean newSection = !section.equals(currentSection);
                int required = newSection ? 2 : 1;

                if (!page.isEmpty() && used + required > maxRows) {
                    break;
                }

                if (newSection) {
                    page.add(TemplatePrintRow.section(section));
                    used++;
                    currentSection = section;
                }

                page.add(TemplatePrintRow.item(line));
                used++;
                index++;

                if (used >= maxRows) {
                    break;
                }
            }

            pages.add(page);
        }

        return pages;
    }

    private Node buildTemplatePrintPage(
            List<TemplatePrintRow> rows,
            int pageNumber,
            int totalPages
    ) {
        final double productWidth = 255;
        final double unitWidth = 50;
        final double quantityWidth = 75;
        final double fullWidth = 75;
        final double weightWidth = 85;
        final double sheetWidth =
                productWidth + unitWidth + quantityWidth + fullWidth + weightWidth;

        VBox page = new VBox(4);
        page.setPadding(new Insets(14));
        page.setPrefWidth(sheetWidth + 28);
        page.setMaxWidth(sheetWidth + 28);
        page.setStyle("-fx-background-color: white;");

        VBox header = new VBox(3);
        header.setAlignment(Pos.CENTER);

        Label company = new Label("EAST SIDE MARIO'S");
        company.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        company.setStyle("-fx-text-fill: black;");

        Label title = new Label("INVENTORY COUNT SHEET");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        title.setStyle("-fx-text-fill: black;");

        Label templateLabel = new Label(
                "Template: " + template.getName()
                        + " | Page " + pageNumber + " of " + totalPages
        );
        templateLabel.setFont(Font.font("Arial", 8.5));
        templateLabel.setStyle("-fx-text-fill: black;");

        Label info = new Label(
                "Count Date: __________________    Manager: __________________"
        );
        info.setFont(Font.font("Arial", 8.5));
        info.setStyle("-fx-text-fill: black;");

        header.getChildren().addAll(company, title, templateLabel, info);

        GridPane tableHeader = createTemplatePrintGrid(
                productWidth, unitWidth, quantityWidth, fullWidth, weightWidth
        );
        tableHeader.setStyle(
                "-fx-border-color: black;" +
                        "-fx-border-width: 1;" +
                        "-fx-background-color: #eeeeee;"
        );

        addTemplatePrintCell(tableHeader, "Product", 0, true, Pos.CENTER_LEFT);
        addTemplatePrintCell(tableHeader, "Unit", 1, true, Pos.CENTER);
        addTemplatePrintCell(tableHeader, "Quantity", 2, true, Pos.CENTER);
        addTemplatePrintCell(tableHeader, "Full", 3, true, Pos.CENTER);
        addTemplatePrintCell(tableHeader, "Weight", 4, true, Pos.CENTER);

        VBox body = new VBox(0);

        for (TemplatePrintRow printRow : rows) {
            if (printRow.sectionHeader()) {
                Label section = new Label(printRow.sectionName());
                section.setPrefWidth(sheetWidth);
                section.setMaxWidth(sheetWidth);
                section.setStyle(
                        "-fx-font-weight: bold;" +
                        "-fx-font-size: 9px;" +
                        "-fx-text-fill: white;" +
                        "-fx-background-color: #008EAA;" +
                        "-fx-padding: 3 5 3 5;" +
                        "-fx-border-color: black;" +
                        "-fx-border-width: 1 1 0 1;"
                );
                body.getChildren().add(section);
                continue;
            }

            InventoryCountTemplateLine line = printRow.line();
            AlcoholProductProfile profile =
                    alcoholProfileDao.findByProductId(line.getProductId());
            boolean weighted =
                    profile != null && "WEIGHT".equalsIgnoreCase(profile.getCountMethod());

            GridPane row = createTemplatePrintGrid(
                    productWidth, unitWidth, quantityWidth, fullWidth, weightWidth
            );
            row.setStyle("-fx-border-color: black; -fx-border-width: 0 1 1 1;");

            String name = line.getDisplayName();
            if (name == null || name.isBlank()) {
                name = line.getProductDescription();
            }

            String unit = weighted && profile.getMeasurementUnit() != null
                    ? profile.getMeasurementUnit()
                    : line.getCountUnit();

            addTemplatePrintCell(row, name, 0, false, Pos.CENTER_LEFT);
            addTemplatePrintCell(row, unit, 1, false, Pos.CENTER);
            addTemplatePrintCell(row, weighted ? "" : "____________", 2, false, Pos.CENTER);
            addTemplatePrintCell(row, weighted ? "________" : "", 3, false, Pos.CENTER);
            addTemplatePrintCell(row, weighted ? "________" : "", 4, false, Pos.CENTER);

            body.getChildren().add(row);
        }

        page.getChildren().addAll(header, tableHeader, body);
        return page;
    }

    private GridPane createTemplatePrintGrid(
            double productWidth,
            double unitWidth,
            double quantityWidth,
            double fullWidth,
            double weightWidth
    ) {
        GridPane grid = new GridPane();
        grid.getColumnConstraints().addAll(
                createFixedColumn(productWidth),
                createFixedColumn(unitWidth),
                createFixedColumn(quantityWidth),
                createFixedColumn(fullWidth),
                createFixedColumn(weightWidth)
        );
        return grid;
    }

    private ColumnConstraints createFixedColumn(double width) {
        ColumnConstraints column = new ColumnConstraints(width);
        column.setMinWidth(width);
        column.setMaxWidth(width);
        return column;
    }

    private void addTemplatePrintCell(
            GridPane grid,
            String text,
            int column,
            boolean header,
            Pos alignment
    ) {
        Label label = new Label(text == null ? "" : text);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setMinHeight(19);
        label.setAlignment(alignment);
        label.setFont(Font.font(
                "Arial",
                header ? FontWeight.BOLD : FontWeight.NORMAL,
                8.5
        ));
        label.setStyle(
                "-fx-text-fill: black;" +
                        "-fx-padding: 2 4 2 4;" +
                        "-fx-border-color: black;" +
                        "-fx-border-width: 0 1 0 0;"
        );

        grid.add(label, column, 0);
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

    private record TemplatePrintRow(
            boolean sectionHeader,
            String sectionName,
            InventoryCountTemplateLine line
    ) {
        static TemplatePrintRow section(String sectionName) {
            return new TemplatePrintRow(true, sectionName, null);
        }

        static TemplatePrintRow item(InventoryCountTemplateLine line) {
            return new TemplatePrintRow(false, null, line);
        }
    }
}
