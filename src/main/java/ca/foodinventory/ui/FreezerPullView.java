package ca.foodinventory.ui;

import ca.foodinventory.model.ImportedUsageReportSummary;
import ca.foodinventory.model.ProductionReportLine;
import ca.foodinventory.model.ProductionReportSummary;
import ca.foodinventory.service.ProductionReportService;
import ca.foodinventory.service.ProductionUsageReportImportService;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.transform.Scale;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FreezerPullView extends BorderPane {

    private static final String FREEZER_PULL_STATION = "Freezer Pull";

    private final ProductionUsageReportImportService usageReportImportService =
            new ProductionUsageReportImportService();
    private final ProductionReportService productionReportService = new ProductionReportService();
    private final TableView<ProductionReportLine> table = new TableView<>();
    private final Label summaryLabel = new Label("Import a usage report to generate freezer pull quantities.");

    public FreezerPullView() {
        getStyleClass().add("root-dark");
        buildLayout();
    }

    private void buildLayout() {
        Label title = new Label("Freezer Pull");
        title.getStyleClass().add("page-title");

        Label subtitle = new Label("Generate a separate freezer pull sheet from POS sales.");
        subtitle.getStyleClass().add("section-title");

        Button importButton = new Button("Import Usage Report");
        importButton.getStyleClass().add("primary-button");
        importButton.setOnAction(e -> importUsageReport());

        Button printButton = new Button("Print Freezer Pull");
        printButton.getStyleClass().add("primary-button");
        printButton.setOnAction(e -> printFreezerPull());

        HBox buttons = new HBox(10, importButton, printButton);
        VBox top = new VBox(15, title, subtitle, buttons, summaryLabel);
        top.setPadding(new Insets(20));
        top.getStyleClass().add("content-area");

        setupTable();

        setTop(top);
        setCenter(table);
    }

    private void setupTable() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<ProductionReportLine, String> itemCol = new TableColumn<>("Item To Pull");
        itemCol.setCellValueFactory(new PropertyValueFactory<>("productionItemName"));
        itemCol.setPrefWidth(240);

        TableColumn<ProductionReportLine, String> unitCol = new TableColumn<>("Unit");
        unitCol.setCellValueFactory(new PropertyValueFactory<>("unit"));

        TableColumn<ProductionReportLine, Double> mondayCol = quantityColumn("Mon", "mondayQuantity");
        TableColumn<ProductionReportLine, Double> tuesdayCol = quantityColumn("Tues", "tuesdayQuantity");
        TableColumn<ProductionReportLine, Double> wednesdayCol = quantityColumn("Wed", "wednesdayQuantity");
        TableColumn<ProductionReportLine, Double> thursdayCol = quantityColumn("Thurs", "thursdayQuantity");
        TableColumn<ProductionReportLine, Double> fridayCol = quantityColumn("Fri", "fridayQuantity");
        TableColumn<ProductionReportLine, Double> saturdayCol = quantityColumn("Sat", "saturdayQuantity");
        TableColumn<ProductionReportLine, Double> sundayCol = quantityColumn("Sun", "sundayQuantity");
        TableColumn<ProductionReportLine, Double> weeklyCol = quantityColumn("Total Pull", "weeklyQuantity");

        table.getColumns().setAll(
                List.of(
                        itemCol,
                        unitCol,
                        mondayCol,
                        tuesdayCol,
                        wednesdayCol,
                        thursdayCol,
                        fridayCol,
                        saturdayCol,
                        sundayCol,
                        weeklyCol
                )
        );
    }

    private TableColumn<ProductionReportLine, Double> quantityColumn(String title, String propertyName) {
        TableColumn<ProductionReportLine, Double> column = new TableColumn<>(title);
        column.setCellValueFactory(new PropertyValueFactory<>(propertyName));
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : formatNumber(value));
            }
        });
        return column;
    }

    private void importUsageReport() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Usage Report");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx")
        );

        File file = chooser.showOpenDialog(null);

        if (file == null) {
            return;
        }

        try {
            ImportedUsageReportSummary usageSummary = usageReportImportService.importUsageReport(file);
            ProductionReportSummary productionSummary =
                    productionReportService.generateReport(usageSummary);
            List<ProductionReportLine> freezerPullLines = filterFreezerPullLines(productionSummary.getLines());

            table.setItems(FXCollections.observableArrayList(freezerPullLines));
            summaryLabel.setText(
                    "Imported " + usageSummary.getTotalRows()
                            + " configured POS rows from " + usageSummary.getSheetName()
                            + " | Generated production items: " + productionSummary.getLineCount()
                            + " | Freezer pull items: " + freezerPullLines.size()
            );

            if (freezerPullLines.isEmpty()) {
                showNoFreezerPullLinesAlert(productionSummary);
            }

        } catch (RuntimeException e) {
            showAlert(
                    Alert.AlertType.ERROR,
                    "Import Failed",
                    e.getMessage()
            );
        }
    }

    private List<ProductionReportLine> filterFreezerPullLines(List<ProductionReportLine> lines) {
        List<ProductionReportLine> freezerPullLines = new ArrayList<>();

        for (ProductionReportLine line : lines) {
            if (FREEZER_PULL_STATION.equalsIgnoreCase(line.getStationName())) {
                freezerPullLines.add(line);
            }
        }

        freezerPullLines.sort(Comparator
                .comparingInt(ProductionReportLine::getPrintOrder)
                .thenComparing(ProductionReportLine::getProductionItemName, String.CASE_INSENSITIVE_ORDER));

        return freezerPullLines;
    }

    private void showNoFreezerPullLinesAlert(ProductionReportSummary productionSummary) {
        String generatedStations = summarizeGeneratedStations(productionSummary.getLines());

        showAlert(
                Alert.AlertType.INFORMATION,
                "No Freezer Pull Items Generated",
                "The usage report imported, but no generated production lines were assigned to the Freezer Pull station.\n\n"
                        + "Generated production items: " + productionSummary.getLineCount() + "\n"
                        + "POS rows skipped because they have no production profile: "
                        + productionSummary.getSkippedRowsWithoutProfile() + "\n"
                        + "Generated stations: " + generatedStations + "\n\n"
                        + "Check that the sold POS menu item has a Production Profile, and that the profile includes "
                        + "an active Production Item assigned to the Freezer Pull station."
        );
    }

    private String summarizeGeneratedStations(List<ProductionReportLine> lines) {
        if (lines.isEmpty()) {
            return "none";
        }

        Map<String, Integer> stationCounts = new LinkedHashMap<>();

        for (ProductionReportLine line : lines) {
            String stationName = line.getStationName();

            if (stationName == null || stationName.isBlank()) {
                stationName = "Unassigned";
            }

            stationCounts.merge(stationName, 1, Integer::sum);
        }

        List<String> summaries = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : stationCounts.entrySet()) {
            summaries.add(entry.getKey() + " (" + entry.getValue() + ")");
        }

        return String.join(", ", summaries);
    }

    private void printFreezerPull() {
        if (table.getItems().isEmpty()) {
            showAlert(
                    Alert.AlertType.WARNING,
                    "Nothing To Print",
                    "Import a usage report before printing freezer pull."
            );
            return;
        }

        PrinterJob job = PrinterJob.createPrinterJob();

        if (job == null || !job.showPrintDialog(getScene().getWindow())) {
            return;
        }

        PageLayout pageLayout = createPrintPageLayout(job);
        VBox page = buildPrintPage(new ArrayList<>(table.getItems()));

        if (job.printPage(pageLayout, createScaledPrintNode(page, pageLayout))) {
            job.endJob();
        } else {
            showAlert(
                    Alert.AlertType.ERROR,
                    "Print Failed",
                    "The freezer pull sheet could not be printed."
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

    private Node createScaledPrintNode(VBox page, PageLayout pageLayout) {
        page.applyCss();
        page.layout();

        double pageWidth = page.getBoundsInLocal().getWidth();
        double pageHeight = page.getBoundsInLocal().getHeight();
        double scaleX = pageLayout.getPrintableWidth() / pageWidth;
        double scaleY = pageLayout.getPrintableHeight() / pageHeight;
        double scale = Math.min(0.96, Math.min(scaleX, scaleY));

        Group scaledPage = new Group(page);
        scaledPage.getTransforms().add(new Scale(scale, scale));
        double scaledWidth = pageWidth * scale;
        double scaledHeight = pageHeight * scale;
        scaledPage.relocate(
                Math.max((pageLayout.getPrintableWidth() - scaledWidth) / 2, 0),
                Math.max((pageLayout.getPrintableHeight() - scaledHeight) / 2, 0)
        );

        Pane printRoot = new Pane(scaledPage);
        printRoot.setPrefSize(pageLayout.getPrintableWidth(), pageLayout.getPrintableHeight());
        return printRoot;
    }

    private VBox buildPrintPage(List<ProductionReportLine> lines) {
        Label title = new Label("FREEZER PULL");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        GridPane header = new GridPane();
        header.setStyle("-fx-border-color: black; -fx-border-width: 1 0 1 0;");
        addCell(header, "ITEM", 0, 145, true, Pos.CENTER);
        addCell(header, "UNIT", 1, 52, true, Pos.CENTER);
        addCell(header, "MON", 2, 38, true, Pos.CENTER);
        addCell(header, "TUES", 3, 38, true, Pos.CENTER);
        addCell(header, "WED", 4, 38, true, Pos.CENTER);
        addCell(header, "THURS", 5, 40, true, Pos.CENTER);
        addCell(header, "FRI", 6, 38, true, Pos.CENTER);
        addCell(header, "SAT", 7, 38, true, Pos.CENTER);
        addCell(header, "SUN", 8, 38, true, Pos.CENTER);
        addCell(header, "TOTAL", 9, 44, true, Pos.CENTER);

        VBox page = new VBox(4, title, header);
        page.setPrefWidth(545);
        page.setMinWidth(545);
        page.setMaxWidth(545);
        page.setPadding(new Insets(14));
        page.setStyle("-fx-background-color: white; -fx-text-fill: black;");

        for (ProductionReportLine line : lines) {
            page.getChildren().add(createPrintLine(line));
        }

        return page;
    }

    private Node createPrintLine(ProductionReportLine line) {
        GridPane row = new GridPane();
        row.setStyle("-fx-border-color: #bbbbbb; -fx-border-width: 0 0 1 0;");

        addCell(row, line.getProductionItemName(), 0, 145, false, Pos.CENTER_LEFT);
        addCell(row, line.getUnit(), 1, 52, false, Pos.CENTER);
        addCell(row, formatNumber(line.getMondayQuantity()), 2, 38, false, Pos.CENTER);
        addCell(row, formatNumber(line.getTuesdayQuantity()), 3, 38, false, Pos.CENTER);
        addCell(row, formatNumber(line.getWednesdayQuantity()), 4, 38, false, Pos.CENTER);
        addCell(row, formatNumber(line.getThursdayQuantity()), 5, 40, false, Pos.CENTER);
        addCell(row, formatNumber(line.getFridayQuantity()), 6, 38, false, Pos.CENTER);
        addCell(row, formatNumber(line.getSaturdayQuantity()), 7, 38, false, Pos.CENTER);
        addCell(row, formatNumber(line.getSundayQuantity()), 8, 38, false, Pos.CENTER);
        addCell(row, formatNumber(line.getWeeklyQuantity()), 9, 44, false, Pos.CENTER);

        return row;
    }

    private void addCell(
            GridPane row,
            String text,
            int column,
            double width,
            boolean header,
            Pos alignment
    ) {
        Label label = new Label(text == null ? "" : text);
        label.setPrefWidth(width);
        label.setMinHeight(header ? 17 : 15);
        label.setAlignment(alignment);
        label.setStyle(
                "-fx-font-size: 6px;"
                        + (header ? "-fx-font-weight: bold;" : "")
                        + "-fx-border-color: #bbbbbb;"
                        + "-fx-border-width: 0 1 0 0;"
                        + "-fx-padding: 2;"
        );
        row.add(label, column, 0);
    }

    private String formatNumber(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }

        return String.format("%.2f", value);
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
