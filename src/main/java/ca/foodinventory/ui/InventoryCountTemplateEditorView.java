package ca.foodinventory.ui;

import ca.foodinventory.dao.InventoryCountTemplateLineDao;
import ca.foodinventory.dao.ProductDao;
import ca.foodinventory.model.InventoryCountTemplate;
import ca.foodinventory.model.InventoryCountTemplateLine;
import ca.foodinventory.model.Product;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.geometry.Insets;
import javafx.print.*;
import javafx.scene.Node;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.ArrayList;

import java.util.List;

public class InventoryCountTemplateEditorView {
    private static final int LINES_PER_PAGE = 28;
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
        TableColumn<InventoryCountTemplateLine, String> skuCol =
                new TableColumn<>("SKU");

        skuCol.setCellValueFactory(
                new PropertyValueFactory<>("sku")
        );

        skuCol.setPrefWidth(120);

        TableColumn<InventoryCountTemplateLine, String> productDescCol =
                new TableColumn<>("Product");

        productDescCol.setCellValueFactory(
                new PropertyValueFactory<>("productDescription")
        );

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

        TextField sortOrderField =
                new TextField(
                        String.valueOf(
                                (table.getItems().size() + 1)
                        )
                );


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

        InventoryCountTemplateLine selected =
                table.getSelectionModel().getSelectedItem();

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

        ButtonType saveButtonType =
                new ButtonType(
                        "Save",
                        ButtonBar.ButtonData.OK_DONE
                );

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

        TextField displayNameField =
                new TextField(selected.getDisplayName());

        TextField countUnitField =
                new TextField(selected.getCountUnit());

        TextField conversionFactorField =
                new TextField(
                        String.valueOf(
                                selected.getConversionFactorToBase()
                        )
                );

        TextField sortOrderField =
                new TextField(
                        String.valueOf(
                                selected.getSortOrder()
                        )
                );

        GridPane grid = new GridPane();

        grid.setHgap(10);
        grid.setVgap(10);
        grid.setStyle("-fx-padding: 15;");

        grid.add(new Label("Product:"), 0, 0);

        grid.add(
                new Label(
                        selected.getSku()
                                + " - "
                                + selected.getProductDescription()
                ),
                1,
                0
        );

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

                selected.setSectionName(
                        sectionBox.getEditor().getText().trim()
                );

                selected.setDisplayName(
                        displayNameField.getText().trim()
                );

                selected.setCountUnit(
                        countUnitField.getText().trim()
                );

                selected.setConversionFactorToBase(
                        Double.parseDouble(
                                conversionFactorField.getText().trim()
                        )
                );

                selected.setSortOrder(
                        Integer.parseInt(
                                sortOrderField.getText().trim()
                        )
                );

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
        InventoryCountTemplateLine selected =
                table.getSelectionModel().getSelectedItem();

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
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Blank Count Sheet");
            alert.setHeaderText(null);
            alert.setContentText("This template has no active products to print.");
            alert.showAndWait();
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
                Printer.MarginType.DEFAULT
        );

        job.getJobSettings().setPageLayout(pageLayout);

        if (!job.showPrintDialog(table.getScene().getWindow())) {
            return;
        }

        List<Node> pages = buildBlankCountSheetPages(lines);

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

    private List<Node> buildBlankCountSheetPages(List<InventoryCountTemplateLine> lines) {

        List<Node> pages = new ArrayList<>();

        int index = 0;
        int pageNumber = 1;
        int totalPages = (int) Math.ceil(lines.size() / (double) LINES_PER_PAGE);

        while (index < lines.size()) {

            VBox page = new VBox(6);
            page.setPadding(new Insets(30));
            page.setStyle("-fx-background-color: white;");

            Label title = new Label("INVENTORY COUNT SHEET");
            title.setFont(Font.font("Arial", FontWeight.BOLD, 18));
            title.setStyle("-fx-text-fill: black;");

            Label templateLabel = new Label("Template: " + template.getName());
            templateLabel.setFont(Font.font("Arial", 11));
            templateLabel.setStyle("-fx-text-fill: black;");

            Label pageLabel = new Label("Page " + pageNumber + " of " + totalPages);
            pageLabel.setFont(Font.font("Arial", 10));
            pageLabel.setStyle("-fx-text-fill: black;");

            page.getChildren().addAll(title, templateLabel, pageLabel, createBlankSheetHeaderRow());

            String currentSection = null;
            int linesOnPage = 0;

            while (index < lines.size() && linesOnPage < LINES_PER_PAGE) {

                InventoryCountTemplateLine line = lines.get(index);

                if (currentSection == null || !currentSection.equals(line.getSectionName())) {
                    currentSection = line.getSectionName();

                    Label sectionLabel = new Label(
                            currentSection == null || currentSection.isBlank()
                                    ? "OTHER"
                                    : currentSection.toUpperCase()
                    );

                    sectionLabel.setFont(Font.font("Arial", FontWeight.BOLD, 11));
                    sectionLabel.setStyle("-fx-text-fill: black;");
                    sectionLabel.setPadding(new Insets(8, 0, 2, 0));

                    page.getChildren().add(sectionLabel);
                    linesOnPage++;
                }

                page.getChildren().add(createBlankSheetDataRow(line));
                linesOnPage++;
                index++;
            }

            pages.add(page);
            pageNumber++;
        }

        return pages;
    }

    private GridPane createBlankSheetHeaderRow() {

        GridPane grid = createBlankSheetBaseGrid();
        grid.setStyle("-fx-border-color: black; -fx-border-width: 0 0 1 0;");

        addBlankSheetHeaderCell(grid, "Product", 0);
        addBlankSheetHeaderCell(grid, "Unit", 1);
        addBlankSheetHeaderCell(grid, "Count", 2);

        return grid;
    }

    private GridPane createBlankSheetDataRow(InventoryCountTemplateLine line) {

        GridPane grid = createBlankSheetBaseGrid();
        grid.setStyle("-fx-border-color: #999999; -fx-border-width: 0 0 1 0;");

        String productName = line.getDisplayName();

        if (productName == null || productName.isBlank()) {
            productName = line.getProductDescription();
        }

        addBlankSheetDataCell(grid, productName, 0);
        addBlankSheetDataCell(grid, line.getCountUnit(), 1);
        addBlankSheetDataCell(grid, "________________", 2);

        return grid;
    }

    private GridPane createBlankSheetBaseGrid() {

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setPadding(new Insets(3, 0, 3, 0));

        ColumnConstraints product = new ColumnConstraints(360);
        ColumnConstraints unit = new ColumnConstraints(80);
        ColumnConstraints count = new ColumnConstraints(130);

        grid.getColumnConstraints().addAll(product, unit, count);

        return grid;
    }

    private void addBlankSheetHeaderCell(GridPane grid, String text, int column) {
        Label label = new Label(text);
        label.setFont(Font.font("Arial", FontWeight.BOLD, 10));
        label.setStyle("-fx-text-fill: black;");
        grid.add(label, column, 0);
    }

    private void addBlankSheetDataCell(GridPane grid, String text, int column) {
        Label label = new Label(text == null ? "" : text);
        label.setFont(Font.font("Arial", 9));
        label.setStyle("-fx-text-fill: black;");
        grid.add(label, column, 0);
    }
}