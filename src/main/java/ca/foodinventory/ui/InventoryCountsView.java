package ca.foodinventory.ui;

import ca.foodinventory.dao.AlcoholProductProfileDao;
import ca.foodinventory.dao.InventoryCountDao;
import ca.foodinventory.dao.InventoryCountLineDao;
import ca.foodinventory.dao.InventoryCountTemplateDao;
import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.AlcoholProductProfile;
import ca.foodinventory.model.InventoryCount;
import ca.foodinventory.model.InventoryCountLine;
import ca.foodinventory.model.InventoryCountTemplate;
import ca.foodinventory.service.AlcoholProductProfileApiClient;
import ca.foodinventory.service.InventoryApiClient;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.*;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.transform.Scale;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class InventoryCountsView {

    private final InventoryCountDao countDao = new InventoryCountDao();
    private final InventoryCountTemplateDao templateDao = new InventoryCountTemplateDao();
    private final InventoryCountLineDao lineDao = new InventoryCountLineDao();
    private final AlcoholProductProfileDao alcoholProfileDao = new AlcoholProductProfileDao();
    private final InventoryApiClient apiClient = new InventoryApiClient();
    private final AlcoholProductProfileApiClient alcoholProfileApiClient =
            new AlcoholProductProfileApiClient();

    private final TableView<InventoryCount> table = new TableView<>();

    private final String department;

    public InventoryCountsView() {
        this.department = null;
    }

    public InventoryCountsView(String department) {
        this.department = department;
    }

    public BorderPane getView() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-dark");

        Label title = new Label(getTitleText());
        title.getStyleClass().add("page-title");

        Button startButton = new Button("Start Count");
        Button openButton = new Button("Open Count");
        Button printButton = new Button("Print Count Sheet");
        Button deleteButton = new Button("Delete Count");
        Button refreshButton = new Button("Refresh");

        startButton.getStyleClass().add("primary-button");
        openButton.getStyleClass().add("primary-button");
        printButton.getStyleClass().add("primary-button");
        deleteButton.getStyleClass().add("primary-button");
        refreshButton.getStyleClass().add("primary-button");

        HBox buttons = new HBox(10, startButton, openButton, printButton, deleteButton, refreshButton);
        VBox top = new VBox(10, title, buttons);
        top.setStyle("-fx-padding: 15;");

        setupTable();

        startButton.setOnAction(e -> showStartCountDialog());
        openButton.setOnAction(e -> openSelectedCount());
        refreshButton.setOnAction(e -> refreshTable());
        printButton.setOnAction(e -> printSelectedCountSheet());
        deleteButton.setOnAction(e -> deleteSelectedCount());

        root.setTop(top);
        root.setCenter(table);

        refreshTable();

        return root;
    }

    private void setupTable() {
        TableColumn<InventoryCount, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("countDate"));
        dateCol.setPrefWidth(140);

        TableColumn<InventoryCount, String> periodStartCol = new TableColumn<>("Period Start");
        periodStartCol.setCellValueFactory(new PropertyValueFactory<>("periodStartDate"));
        periodStartCol.setPrefWidth(140);

        TableColumn<InventoryCount, String> periodEndCol = new TableColumn<>("Period End");
        periodEndCol.setCellValueFactory(new PropertyValueFactory<>("periodEndDate"));
        periodEndCol.setPrefWidth(140);

        TableColumn<InventoryCount, String> templateCol = new TableColumn<>("Template");
        templateCol.setCellValueFactory(new PropertyValueFactory<>("templateName"));
        templateCol.setPrefWidth(220);

        TableColumn<InventoryCount, String> notesCol = new TableColumn<>("Notes");
        notesCol.setCellValueFactory(new PropertyValueFactory<>("notes"));
        notesCol.setPrefWidth(300);

        TableColumn<InventoryCount, Boolean> completedCol = new TableColumn<>("Completed");
        completedCol.setCellValueFactory(new PropertyValueFactory<>("completed"));
        completedCol.setPrefWidth(120);

        table.getColumns().setAll(
                dateCol,
                periodStartCol,
                periodEndCol,
                templateCol,
                notesCol,
                completedCol
        );
    }

    private void refreshTable() {
        if (isMigratedDepartmentApiMode()) {
            table.setItems(FXCollections.observableArrayList(
                    apiClient.findCounts(department)
            ));
            return;
        }

        List<InventoryCount> counts = countDao.findAll();

        if (department != null) {
            counts.removeIf(count -> {
                String templateName = count.getTemplateName() == null
                        ? ""
                        : count.getTemplateName().toUpperCase();

                return switch (department) {
                    case "FOOD" -> !templateName.contains("FOOD");
                    case "ALCOHOL" -> !templateName.contains("ALCOHOL");
                    case "SUPPLIES" -> !templateName.contains("SUPPLIES");
                    default -> false;
                };
            });
        }

        table.setItems(FXCollections.observableArrayList(counts));
    }

    private void showStartCountDialog() {
        Dialog<InventoryCountTemplate> dialog = new Dialog<>();
        dialog.setTitle("Start Inventory Count");

        ButtonType startButtonType = new ButtonType("Start", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(startButtonType, ButtonType.CANCEL);

        ComboBox<InventoryCountTemplate> templateBox = new ComboBox<>();
        templateBox.setItems(FXCollections.observableArrayList(getFilteredTemplates()));
        templateBox.setPrefWidth(350);

        templateBox.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(InventoryCountTemplate template, boolean empty) {
                super.updateItem(template, empty);
                setText(empty || template == null ? null : template.getName());
            }
        });

        templateBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(InventoryCountTemplate template, boolean empty) {
                super.updateItem(template, empty);
                setText(empty || template == null ? null : template.getName());
            }
        });

        DatePicker datePicker = new DatePicker(LocalDate.now());
        DatePicker periodStartPicker = new DatePicker(LocalDate.now().minusDays(6));
        DatePicker periodEndPicker = new DatePicker(LocalDate.now());

        TextArea notesArea = new TextArea();
        notesArea.setPromptText("Optional notes...");
        notesArea.setPrefRowCount(3);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setStyle("-fx-padding: 15;");

        grid.add(new Label("Template:"), 0, 0);
        grid.add(templateBox, 1, 0);

        grid.add(new Label("Count Date:"), 0, 1);
        grid.add(datePicker, 1, 1);

        grid.add(new Label("Period Start:"), 0, 2);
        grid.add(periodStartPicker, 1, 2);

        grid.add(new Label("Period End:"), 0, 3);
        grid.add(periodEndPicker, 1, 3);

        grid.add(new Label("Notes:"), 0, 4);
        grid.add(notesArea, 1, 4);

        dialog.getDialogPane().setContent(grid);

        Button startButton = (Button) dialog.getDialogPane().lookupButton(startButtonType);
        startButton.disableProperty().bind(templateBox.valueProperty().isNull());

        dialog.setResultConverter(button -> {
            if (button == startButtonType) {
                return templateBox.getSelectionModel().getSelectedItem();
            }
            return null;
        });

        dialog.showAndWait().ifPresent(template -> {
            if (isMigratedDepartmentApiMode()) {
                apiClient.createCount(
                        department,
                        template.getId(),
                        datePicker.getValue().toString(),
                        periodStartPicker.getValue().toString(),
                        periodEndPicker.getValue().toString(),
                        notesArea.getText()
                );
                refreshTable();
                return;
            }

            int countId = countDao.createCount(
                    template.getId(),
                    datePicker.getValue().toString(),
                    periodStartPicker.getValue().toString(),
                    periodEndPicker.getValue().toString(),
                    notesArea.getText()
            );

            if (countId > 0) {
                countDao.createCountLinesFromTemplate(countId, template.getId());
                refreshTable();
            }
        });
    }

    private List<InventoryCountTemplate> getFilteredTemplates() {
        if (isMigratedDepartmentApiMode()) {
            return apiClient.findActiveTemplates(department);
        }

        List<InventoryCountTemplate> templates = templateDao.findAllActive();

        if (department != null) {
            templates.removeIf(template -> {
                String name = template.getName() == null
                        ? ""
                        : template.getName().toUpperCase();

                return switch (department) {
                    case "FOOD" -> !name.contains("FOOD");
                    case "ALCOHOL" -> !name.contains("ALCOHOL");
                    case "SUPPLIES" -> !name.contains("SUPPLIES");
                    default -> false;
                };
            });
        }

        return templates;
    }

    private void openSelectedCount() {
        InventoryCount selected =
                table.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showAlert(
                    Alert.AlertType.WARNING,
                    "No Count Selected",
                    "Please select a count."
            );
            return;
        }

        InventoryCountEntryView view =
                new InventoryCountEntryView(selected, department);

        Stage stage = new Stage();
        if (table.getScene() != null && table.getScene().getWindow() != null) {
            stage.initOwner(table.getScene().getWindow());
        }

        stage.setTitle(
                "Inventory Count - " +
                        selected.getTemplateName()
        );

        Scene scene = WindowSizing.scene(
                view.getView(),
                1200,
                800,
                stage.getOwner()
        );

        stage.setScene(scene);
        WindowSizing.fitAndCenter(stage, 1200, 800);
        stage.show();
    }

    private void deleteSelectedCount() {
        InventoryCount selected = table.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showAlert(
                    Alert.AlertType.WARNING,
                    "No Count Selected",
                    "Please select a count to delete."
            );
            return;
        }

        TextInputDialog dialog = new TextInputDialog();

        dialog.setTitle("Delete Inventory Count");
        dialog.setHeaderText(
                "WARNING\n\n" +
                        "This will permanently delete:\n" +
                        "- The inventory count\n" +
                        "- All inventory count lines\n\n" +
                        "This may affect:\n" +
                        "- Weekly Cost Reports\n" +
                        "- Order Guides\n" +
                        "- Historical Inventory Records"
        );

        dialog.setContentText("Type DELETE to continue:");

        dialog.showAndWait().ifPresent(response -> {
            if (!"DELETE".equals(response.trim())) {
                showAlert(
                        Alert.AlertType.INFORMATION,
                        "Delete Cancelled",
                        "Delete operation cancelled.\n\n" +
                                "You must type DELETE exactly to remove a count."
                );
                return;
            }

            if (isMigratedDepartmentApiMode()) {
                apiClient.deleteCount(selected.getId());
            } else {
                countDao.deleteCount(selected.getId());
            }
            refreshTable();

            showAlert(
                    Alert.AlertType.INFORMATION,
                    "Count Deleted",
                    "Inventory count deleted successfully."
            );
        });
    }

    private void printSelectedCountSheet() {
        InventoryCount selected = table.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Count Selected", "Please select a count to print.");
            return;
        }

        List<InventoryCountLine> lines = isMigratedDepartmentApiMode()
                ? apiClient.findCountLines(selected.getId())
                : lineDao.findByCount(selected.getId());

        if (lines.isEmpty()) {
            showAlert(Alert.AlertType.INFORMATION, "Empty Count", "This count has no active products to print.");
            return;
        }

        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            return;
        }

        if (!job.showPrintDialog(table.getScene().getWindow())) {
            return;
        }

        PageLayout pageLayout = createPrintPageLayout(job);
        List<List<CountPrintRow>> pages = paginateCountLines(lines, 27);
        boolean success = true;

        for (int i = 0; i < pages.size(); i++) {
            Node page = buildCountSheetPage(selected, pages.get(i), i + 1, pages.size());
            if (!job.printPage(pageLayout, fitPageToPrintableArea(page, pageLayout))) {
                success = false;
                break;
            }
        }

        if (success) {
            job.endJob();
        } else {
            showAlert(
                    Alert.AlertType.ERROR,
                    "Print Failed",
                    "The selected inventory count sheet could not be printed."
            );
        }
    }

    private PageLayout createPrintPageLayout(PrinterJob job) {
        Printer printer = job.getPrinter();
        PageLayout pageLayout = printer.createPageLayout(
                Paper.NA_LETTER,
                PageOrientation.PORTRAIT,
                Printer.MarginType.HARDWARE_MINIMUM
        );
        job.getJobSettings().setPageLayout(pageLayout);
        return pageLayout;
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

        Group scaledPage = new Group(page);
        if (scaleFactor < 1.0) {
            scaledPage.getTransforms().add(new Scale(scaleFactor, scaleFactor));
        }

        Pane wrapper = new Pane(scaledPage);
        wrapper.setPrefSize(pageLayout.getPrintableWidth(), pageLayout.getPrintableHeight());
        wrapper.setMinSize(pageLayout.getPrintableWidth(), pageLayout.getPrintableHeight());
        wrapper.setMaxSize(pageLayout.getPrintableWidth(), pageLayout.getPrintableHeight());
        return wrapper;
    }

    private List<List<CountPrintRow>> paginateCountLines(
            List<InventoryCountLine> lines,
            int maxRows
    ) {
        List<List<CountPrintRow>> pages = new java.util.ArrayList<>();
        int index = 0;

        while (index < lines.size()) {
            List<CountPrintRow> page = new java.util.ArrayList<>();
            int used = 0;
            String currentSection = null;

            while (index < lines.size()) {
                InventoryCountLine line = lines.get(index);
                String section = cleanPrintSection(line.getSectionName());
                boolean newSection = !section.equals(currentSection);
                int required = newSection ? 2 : 1;

                if (!page.isEmpty() && used + required > maxRows) {
                    break;
                }

                if (newSection) {
                    page.add(CountPrintRow.section(section));
                    used++;
                    currentSection = section;
                }

                page.add(CountPrintRow.item(line));
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

    private VBox buildCountSheetPage(
            InventoryCount count,
            List<CountPrintRow> rows,
            int pageNumber,
            int totalPages
    ) {
        final double productWidth = 255;
        final double unitWidth = 50;
        final double quantityWidth = 75;
        final double fullWidth = 75;
        final double weightWidth = 85;
        final boolean alcoholLayout = isAlcoholCount(count);
        final double sheetWidth = alcoholLayout
                ? productWidth + unitWidth + quantityWidth + fullWidth + weightWidth
                : productWidth + unitWidth + quantityWidth;

        VBox page = new VBox(4);
        page.setPadding(new Insets(14));
        page.setPrefWidth(sheetWidth + 28);
        page.setMaxWidth(sheetWidth + 28);
        page.setStyle("-fx-background-color: white;");

        Label title = new Label("INVENTORY COUNT SHEET");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: black;");

        Label details = new Label(
                count.getTemplateName()
                        + " | Date: " + count.getCountDate()
                        + " | Period: " + count.getPeriodStartDate()
                        + " to " + count.getPeriodEndDate()
                        + " | Page " + pageNumber + " of " + totalPages
        );
        details.setStyle("-fx-font-size: 8px; -fx-text-fill: black;");

        GridPane header = createPrintGrid(
                alcoholLayout, productWidth, unitWidth, quantityWidth, fullWidth, weightWidth
        );
        header.setStyle("-fx-border-color: black; -fx-border-width: 1; -fx-background-color: #eeeeee;");
        addPrintCell(header, "Product", 0, true, Pos.CENTER_LEFT);
        addPrintCell(header, "Unit", 1, true, Pos.CENTER);
        addPrintCell(header, "Quantity", 2, true, Pos.CENTER);

        if (alcoholLayout) {
            addPrintCell(header, "Full", 3, true, Pos.CENTER);
            addPrintCell(header, "Weight", 4, true, Pos.CENTER);
        }

        VBox body = new VBox(0);
        Map<Integer, AlcoholProductProfile> profilesByProductId = alcoholLayout
                ? loadAlcoholProfilesForPrint()
                : Map.of();

        for (CountPrintRow printRow : rows) {
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

            InventoryCountLine line = printRow.line();
            AlcoholProductProfile profile = alcoholLayout
                    ? profilesByProductId.get(line.getProductId())
                    : null;
            boolean weighted =
                    profile != null && "WEIGHT".equalsIgnoreCase(profile.getCountMethod());

            GridPane row = createPrintGrid(
                    alcoholLayout, productWidth, unitWidth, quantityWidth, fullWidth, weightWidth
            );
            row.setStyle("-fx-border-color: black; -fx-border-width: 0 1 1 1;");

            String unit = weighted && profile.getMeasurementUnit() != null
                    ? profile.getMeasurementUnit()
                    : line.getCountUnit();

            String productName = line.getDisplayName();
            if (productName == null || productName.isBlank()) {
                productName = line.getProductDescription();
            }

            addPrintCell(row, productName, 0, false, Pos.CENTER_LEFT);
            addPrintCell(row, unit, 1, false, Pos.CENTER);
            addPrintCell(row, weighted ? "" : "____________", 2, false, Pos.CENTER);

            if (alcoholLayout) {
                addPrintCell(row, weighted ? "________" : "", 3, false, Pos.CENTER);
                addPrintCell(row, weighted ? "________" : "", 4, false, Pos.CENTER);
            }

            body.getChildren().add(row);
        }

        page.getChildren().addAll(title, details, header, body);
        return page;
    }

    private GridPane createPrintGrid(
            boolean alcoholLayout,
            double productWidth,
            double unitWidth,
            double quantityWidth,
            double fullWidth,
            double weightWidth
    ) {
        GridPane grid = new GridPane();
        grid.getColumnConstraints().addAll(
                fixedColumn(productWidth),
                fixedColumn(unitWidth),
                fixedColumn(quantityWidth)
        );

        if (alcoholLayout) {
            grid.getColumnConstraints().addAll(
                    fixedColumn(fullWidth),
                    fixedColumn(weightWidth)
            );
        }

        return grid;
    }

    private ColumnConstraints fixedColumn(double width) {
        ColumnConstraints column = new ColumnConstraints(width);
        column.setMinWidth(width);
        column.setMaxWidth(width);
        return column;
    }

    private void addPrintCell(
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
        label.setStyle(
                "-fx-text-fill: black;" +
                "-fx-font-size: 8.5px;" +
                "-fx-padding: 2 4 2 4;" +
                "-fx-border-color: black;" +
                "-fx-border-width: 0 1 0 0;" +
                (header ? "-fx-font-weight: bold;" : "")
        );
        grid.add(label, column, 0);
    }

    private String cleanPrintSection(String section) {
        return section == null || section.isBlank()
                ? "OTHER"
                : section.trim().toUpperCase();
    }

    private record CountPrintRow(
            boolean sectionHeader,
            String sectionName,
            InventoryCountLine line
    ) {
        static CountPrintRow section(String sectionName) {
            return new CountPrintRow(true, sectionName, null);
        }

        static CountPrintRow item(InventoryCountLine line) {
            return new CountPrintRow(false, null, line);
        }
    }

    private String getTitleText() {
        if (department == null) {
            return "Inventory Counts";
        }

        return switch (department) {
            case "FOOD" -> "Food Inventory Counts";
            case "ALCOHOL" -> "Alcohol Inventory Counts";
            case "SUPPLIES" -> "Supplies Inventory Counts";
            default -> "Inventory Counts";
        };
    }

    private boolean isAlcoholCount(InventoryCount count) {
        if ("ALCOHOL".equals(department)) {
            return true;
        }

        String templateName = count.getTemplateName() == null ? "" : count.getTemplateName().toUpperCase();
        return templateName.contains("ALCOHOL");
    }

    private Map<Integer, AlcoholProductProfile> loadAlcoholProfilesForPrint() {
        return isMigratedDepartmentApiMode()
                ? alcoholProfileApiClient.findAllActiveByProductId()
                : alcoholProfileDao.findAllActiveByProductId();
    }

    private boolean isMigratedDepartmentApiMode() {
        return DatabaseManager.isApiDatabase()
                && ("FOOD".equals(department)
                || "ALCOHOL".equals(department)
                || "SUPPLIES".equals(department));
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
