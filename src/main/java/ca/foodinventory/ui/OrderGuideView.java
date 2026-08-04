package ca.foodinventory.ui;

import ca.foodinventory.dao.InventoryCountDao;
import ca.foodinventory.dao.InventoryCountTemplateLineDao;
import ca.foodinventory.model.InventoryCount;
import ca.foodinventory.model.OrderGuideRow;
import ca.foodinventory.service.OrderGuideService;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.print.PrinterJob;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.transform.Scale;
import javafx.util.StringConverter;
import javafx.util.converter.DoubleStringConverter;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.Printer;

import java.util.ArrayList;
import java.util.List;

public class OrderGuideView {

    private final InventoryCountDao countDao = new InventoryCountDao();
    private final InventoryCountTemplateLineDao templateLineDao =
            new InventoryCountTemplateLineDao();
    private final OrderGuideService orderGuideService = new OrderGuideService();

    private final ComboBox<InventoryCount> openingCountBox = new ComboBox<>();
    private final ComboBox<InventoryCount> closingCountBox = new ComboBox<>();
    private final TableView<OrderGuideRow> table = new TableView<>();

    private static final int LINES_PER_PAGE = 36;

    private final String department;

    public OrderGuideView() {
        this.department = null;
    }

    public OrderGuideView(String department) {
        this.department = department;
    }

    public BorderPane getView() {

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-dark");
        root.setPadding(new Insets(15));

        Label title = new Label(getTitleText());
        title.getStyleClass().add("page-title");

        setupComboBoxes();
        setupTable();

        Button generateButton = new Button("Generate");
        Button printButton = new Button("Print");

        generateButton.setOnAction(e -> generateGuide());
        printButton.setOnAction(e -> printGuide());

        HBox filters = new HBox(10,
                new Label("Opening Count:"),
                openingCountBox,
                new Label("Closing Count:"),
                closingCountBox,
                generateButton,
                printButton
        );

        VBox top = new VBox(12, title, filters);
        root.setTop(top);
        root.setCenter(table);

        loadCounts();

        return root;
    }

    private void setupComboBoxes() {

        StringConverter<InventoryCount> converter = new StringConverter<>() {
            @Override
            public String toString(InventoryCount count) {
                if (count == null) {
                    return "";
                }

                return count.getCountDate()
                        + " | "
                        + count.getPeriodStartDate()
                        + " to "
                        + count.getPeriodEndDate();
            }

            @Override
            public InventoryCount fromString(String string) {
                return null;
            }
        };

        openingCountBox.setConverter(converter);
        closingCountBox.setConverter(converter);

        openingCountBox.setPrefWidth(260);
        closingCountBox.setPrefWidth(260);
    }

    private void setupTable() {

        table.setEditable(true);

        TableColumn<OrderGuideRow, String> sectionCol = new TableColumn<>("Section");
        sectionCol.setCellValueFactory(new PropertyValueFactory<>("sectionName"));
        sectionCol.setPrefWidth(130);

        TableColumn<OrderGuideRow, String> productCol = new TableColumn<>("Product");
        productCol.setCellValueFactory(new PropertyValueFactory<>("productDescription"));
        productCol.setPrefWidth(260);

        TableColumn<OrderGuideRow, String> unitCol = new TableColumn<>("Unit");
        unitCol.setCellValueFactory(new PropertyValueFactory<>("unit"));
        unitCol.setPrefWidth(70);

        TableColumn<OrderGuideRow, String> caseSizeCol = new TableColumn<>("Case");
        caseSizeCol.setCellValueFactory(new PropertyValueFactory<>("caseSize"));
        caseSizeCol.setCellFactory(TextFieldTableCell.forTableColumn());
        caseSizeCol.setOnEditCommit(e -> saveCaseSize(e.getRowValue(), e.getNewValue()));
        caseSizeCol.setPrefWidth(100);
        caseSizeCol.setEditable(true);

        TableColumn<OrderGuideRow, Double> closingCol = new TableColumn<>("Close");
        closingCol.setCellValueFactory(new PropertyValueFactory<>("closingQuantity"));
        closingCol.setPrefWidth(90);

        TableColumn<OrderGuideRow, Double> usageCol = new TableColumn<>("Usage");
        usageCol.setCellValueFactory(new PropertyValueFactory<>("usageQuantity"));
        usageCol.setPrefWidth(90);

        TableColumn<OrderGuideRow, Double> order1Col = new TableColumn<>("Order 1");
        order1Col.setCellValueFactory(new PropertyValueFactory<>("orderQuantity"));
        order1Col.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        order1Col.setOnEditCommit(e ->
                e.getRowValue().setOrderQuantity(e.getNewValue())
        );
        order1Col.setPrefWidth(90);
        order1Col.setEditable(true);

        TableColumn<OrderGuideRow, String> order2Col = new TableColumn<>("Order 2");
        order2Col.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(""));
        order2Col.setPrefWidth(90);

        table.getColumns().addAll(
                sectionCol,
                productCol,
                unitCol,
                caseSizeCol,
                closingCol,
                usageCol,
                order1Col,
                order2Col
        );
    }

    private void loadCounts() {

        List<InventoryCount> counts;

        if (department == null) {
            counts = countDao.findCompleted();
        } else {
            counts = countDao.findCompletedByDepartment(department);
        }

        openingCountBox.setItems(FXCollections.observableArrayList(counts));
        closingCountBox.setItems(FXCollections.observableArrayList(counts));

        if (counts.size() >= 2) {
            closingCountBox.getSelectionModel().select(0);
            openingCountBox.getSelectionModel().select(1);
        } else if (counts.size() == 1) {
            closingCountBox.getSelectionModel().select(0);
        }
    }

    private void generateGuide() {

        InventoryCount opening = openingCountBox.getValue();
        InventoryCount closing = closingCountBox.getValue();

        if (opening == null || closing == null) {
            showAlert("Please select both an opening count and a closing count.");
            return;
        }

        List<OrderGuideRow> rows = orderGuideService.generateOrderGuide(
                opening.getId(),
                closing.getId()
        );

        table.setItems(FXCollections.observableArrayList(rows));
    }

    private void saveCaseSize(OrderGuideRow row, String value) {
        if (row == null) {
            return;
        }

        String caseSize = value == null ? "" : value.trim();

        try {
            row.setCaseSize(caseSize);
            templateLineDao.updateOrderGuideCaseSize(row.getTemplateLineId(), caseSize);
            table.refresh();
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            showAlert("Case value could not be saved.");
            table.refresh();
        }
    }

    private void printGuide() {

        if (table.getItems().isEmpty()) {
            showAlert("Generate an order guide before printing.");
            return;
        }

        PrinterJob job = PrinterJob.createPrinterJob();

        if (job == null) {
            return;
        }

        Printer printer = job.getPrinter();

        PageLayout pageLayout = printer.createPageLayout(
                Paper.NA_LETTER,
                PageOrientation.LANDSCAPE,
                Printer.MarginType.HARDWARE_MINIMUM
        );

        job.getJobSettings().setPageLayout(pageLayout);

        if (!job.showPrintDialog(table.getScene().getWindow())) {
            return;
        }

        List<Node> pages = buildPrintablePages(new ArrayList<>(table.getItems()));

        boolean success = true;

        for (Node page : pages) {
            Node printablePage = fitPageToPrintableArea(page, pageLayout);

            success = job.printPage(pageLayout, printablePage);
            if (!success) {
                break;
            }
        }

        if (success) {
            job.endJob();
        }
    }

    private Node fitPageToPrintableArea(Node page, PageLayout pageLayout) {
        page.applyCss();
        page.autosize();

        if (page instanceof Parent parent) {
            parent.layout();
        }

        double contentWidth = page.getLayoutBounds().getWidth();
        double contentHeight = page.getLayoutBounds().getHeight();

        if (contentWidth <= 0 || contentHeight <= 0) {
            return page;
        }

        double widthScale = pageLayout.getPrintableWidth() / contentWidth;
        double heightScale = pageLayout.getPrintableHeight() / contentHeight;
        double scaleFactor = Math.min(1.0, Math.min(widthScale, heightScale));

        Group wrapper = new Group(page);

        if (scaleFactor < 1.0) {
            page.getTransforms().add(new Scale(scaleFactor, scaleFactor));
        }

        return wrapper;
    }

    private List<Node> buildPrintablePages(List<OrderGuideRow> rows) {

        List<Node> pages = new ArrayList<>();

        int index = 0;
        int pageNumber = 1;

        while (index < rows.size()) {

            VBox page = new VBox(0);
            page.setPadding(new Insets(10));
            page.setStyle("-fx-background-color: white;");

            Label title = new Label("ORDER GUIDE");
            title.setFont(Font.font("Arial", FontWeight.BOLD, 10));
            title.setStyle("-fx-text-fill: black;");

            page.getChildren().addAll(title, createHeaderRow());

            String currentSection = null;
            int linesOnPage = 0;

            while (index < rows.size() && linesOnPage < LINES_PER_PAGE) {

                OrderGuideRow row = rows.get(index);
                String section = row.getSectionName() == null ? "OTHER" : row.getSectionName().toUpperCase();

                if (currentSection == null || !currentSection.equals(section)) {
                    currentSection = section;
                    page.getChildren().add(createSectionRow(currentSection));
                    linesOnPage++;
                }

                page.getChildren().add(createDataRow(row));
                linesOnPage++;
                index++;
            }

            Label footer = new Label("Page " + pageNumber);
            footer.setFont(Font.font("Arial", 7));
            footer.setStyle("-fx-text-fill: black;");
            footer.setPadding(new Insets(3, 0, 0, 0));

            page.getChildren().add(footer);

            pages.add(page);
            pageNumber++;
        }

        return pages;
    }
    private GridPane createSectionRow(String sectionName) {

        GridPane grid = createBaseGrid();
        grid.setStyle("-fx-background-color: #00B8E6; -fx-border-color: black; -fx-border-width: 0.5;");

        Label label = new Label(sectionName);
        label.setFont(Font.font("Arial", FontWeight.BOLD, 7));
        label.setStyle("-fx-text-fill: white;");
        label.setPadding(new Insets(1, 2, 1, 2));

        grid.add(label, 0, 0, 8, 1);

        return grid;
    }

    private GridPane createHeaderRow() {

        GridPane grid = createBaseGrid();
        grid.setStyle("-fx-border-color: black; -fx-border-width: 0.5;");

        addHeaderCell(grid, "Product", 0);
        addHeaderCell(grid, "Recd Mon", 1);
        addHeaderCell(grid, "Unit", 2);
        addHeaderCell(grid, "Case", 3);
        addHeaderCell(grid, "Close", 4);
        addHeaderCell(grid, "Usage", 5);
        addHeaderCell(grid, "Order 1", 6);
        addHeaderCell(grid, "Order 2", 7);

        return grid;
    }

    private GridPane createDataRow(OrderGuideRow row) {

        GridPane grid = createBaseGrid();
        grid.setStyle("-fx-border-color: black; -fx-border-width: 0 0.5 0.5 0.5;");

        addDataCell(grid, row.getProductDescription(), 0);
        addDataCell(grid, "", 1);
        addDataCell(grid, row.getUnit(), 2);
        addDataCell(grid, row.getCaseSize(), 3);
        addDataCell(grid, formatNumber(row.getClosingQuantity()), 4);
        addDataCell(grid, formatNumber(row.getUsageQuantity()), 5);
        addDataCell(grid, formatNumber(row.getOrderQuantity()), 6);
        addDataCell(grid, "", 7);

        return grid;
    }

    private GridPane createBaseGrid() {

        GridPane grid = new GridPane();
        grid.setHgap(0);
        grid.setVgap(0);
        grid.setPadding(new Insets(0));

        grid.getColumnConstraints().addAll(
                new ColumnConstraints(215), // Product
                new ColumnConstraints(65),  // Recd Mon
                new ColumnConstraints(45),  // Unit
                new ColumnConstraints(65),  // Case
                new ColumnConstraints(55),  // Close
                new ColumnConstraints(55),  // Usage
                new ColumnConstraints(60),  // Order 1
                new ColumnConstraints(60)   // Order 2
        );

        return grid;
    }

    private void addHeaderCell(GridPane grid, String text, int column) {
        Label label = new Label(text);
        label.setFont(Font.font("Arial", FontWeight.BOLD, 7));
        label.setStyle("-fx-text-fill: black; -fx-border-color: black; -fx-border-width: 0 0.5 0 0;");
        label.setPadding(new Insets(1, 2, 1, 2));
        label.setMaxWidth(Double.MAX_VALUE);
        grid.add(label, column, 0);
    }

    private void addDataCell(GridPane grid, String text, int column) {
        Label label = new Label(text == null ? "" : text);
        label.setFont(Font.font("Arial", 7));
        label.setStyle("-fx-text-fill: black; -fx-border-color: black; -fx-border-width: 0 0.5 0 0;");
        label.setPadding(new Insets(0, 2, 0, 2));
        label.setMinHeight(10);
        label.setPrefHeight(10);
        label.setMaxHeight(10);
        label.setMaxWidth(Double.MAX_VALUE);

        grid.add(label, column, 0);
    }

    private String formatNumber(Double value) {
        if (value == null) {
            return "";
        }

        return String.format("%.2f", value);
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Order Guide");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    private String getTitleText() {
        if (department == null) {
            return "Order Guide";
        }

        return switch (department) {
            case "FOOD" -> "Food Order Guide";
            case "ALCOHOL" -> "Alcohol Order Guide";
            case "SUPPLIES" -> "Supplies Order Guide";
            default -> "Order Guide";
        };
    }
}
