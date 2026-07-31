package ca.foodinventory.ui;

import ca.foodinventory.dao.FreezerPullParDao;
import ca.foodinventory.dao.ProductionItemDao;
import ca.foodinventory.model.ProductionItem;
import ca.foodinventory.model.ProductionReportLine;
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
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.StringConverter;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.transform.Scale;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FreezerPullView extends BorderPane {
    private static final String FREEZER_PULL_STATION = "Freezer Pull";
    private final ProductionItemDao productionItemDao = new ProductionItemDao();
    private final FreezerPullParDao freezerPullParDao = new FreezerPullParDao();
    private final TableView<ProductionReportLine> table = new TableView<>();
    private final Label summaryLabel = new Label("Load the table, then enter quantities directly for each day.");

    public FreezerPullView() {
        getStyleClass().add("root-dark");
        buildLayout();
    }

    private void buildLayout() {
        Label title = new Label("Freezer Pull");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Use manual pars for items assigned to the Freezer Pull station.");
        subtitle.getStyleClass().add("section-title");
        Button loadButton = new Button("Load Manual Pars");
        loadButton.getStyleClass().add("primary-button");
        loadButton.setOnAction(e -> loadManualPars());
        Button printButton = new Button("Print Freezer Pull");
        printButton.getStyleClass().add("primary-button");
        printButton.setOnAction(e -> printFreezerPull());
        HBox buttons = new HBox(10, loadButton, printButton);
        VBox top = new VBox(15, title, subtitle, buttons, summaryLabel);
        top.setPadding(new Insets(20));
        top.getStyleClass().add("content-area");
        setupTable();
        setTop(top);
        setCenter(table);
    }

    private void setupTable() {
        table.setEditable(true);
        table.setEditable(true);
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
        table.getColumns().setAll(List.of(itemCol, unitCol, mondayCol, tuesdayCol, wednesdayCol, thursdayCol, fridayCol, saturdayCol, sundayCol, weeklyCol));
    }

    private TableColumn<ProductionReportLine, Double> quantityColumn(String title, String propertyName) {
        TableColumn<ProductionReportLine, Double> column = new TableColumn<>(title);
        column.setEditable(true);
        column.setCellValueFactory(new PropertyValueFactory<>(propertyName));
        column.setCellFactory(TextFieldTableCell.forTableColumn(new StringConverter<Double>() {
            @Override
            public String toString(Double value) {
                return value == null ? "0" : formatNumber(value);
            }

            @Override
            public Double fromString(String value) {
                if (value == null || value.isBlank()) {
                    return 0.0;
                }
                return Double.parseDouble(value.trim());
            }
        }));
        column.setOnEditCommit(event -> {
            try {
                ProductionReportLine row = event.getRowValue();

                row.setDayQuantity(
                        propertyName,
                        event.getNewValue()
                );

                freezerPullParDao.save(
                        row.getProductionItemId(),
                        new double[]{
                                row.getMondayQuantity(),
                                row.getTuesdayQuantity(),
                                row.getWednesdayQuantity(),
                                row.getThursdayQuantity(),
                                row.getFridayQuantity(),
                                row.getSaturdayQuantity(),
                                row.getSundayQuantity()
                        }
                );

                table.refresh();

            } catch (RuntimeException exception) {
                showAlert(
                        Alert.AlertType.ERROR,
                        "Invalid Quantity",
                        "Enter a non-negative number."
                );

                table.refresh();
            }
        });
        return column;
    }

    private void loadManualPars() {
        try {
            List<ProductionReportLine> lines = new ArrayList<>();
            for (ProductionItem item : productionItemDao.findAll()) {
                if (!item.isActive() || !FREEZER_PULL_STATION.equalsIgnoreCase(item.getStationName())) continue;
                int par = item.getPermanentOverridePar() == null ? 0 : item.getPermanentOverridePar();
                ProductionReportLine line = new ProductionReportLine(item.getId(), item.getName(), item.getUnit(), item.getStationId(), item.getStationName(), item.getPrintOrder());
                double[] saved = freezerPullParDao.load(item.getId());
                String[] properties = {"mondayQuantity", "tuesdayQuantity", "wednesdayQuantity", "thursdayQuantity", "fridayQuantity", "saturdayQuantity", "sundayQuantity"};
                for (int i = 0; i < properties.length; i++) line.setDayQuantity(properties[i], saved[i]);
                lines.add(line);
            }
            lines.sort(Comparator.comparingInt(ProductionReportLine::getPrintOrder).thenComparing(ProductionReportLine::getProductionItemName, String.CASE_INSENSITIVE_ORDER));
            table.setItems(FXCollections.observableArrayList(lines));
            summaryLabel.setText("Loaded " + lines.size() + " Freezer Pull items. Enter daily quantities directly in the table.");
        } catch (RuntimeException e) {
            showAlert(Alert.AlertType.ERROR, "Load Failed", e.getMessage());
        }
    }

    private void saveManualPar(ProductionReportLine line, Double value) {
        if (value == null || value < 0 || !Double.isFinite(value)) {
            table.refresh();
            return;
        }
        try {
            productionItemDao.updatePermanentOverridePar(line.getProductionItemId(), (int) Math.round(value));
            line.setManualPar(Math.round(value));
            table.refresh();
        } catch (RuntimeException e) {
            showAlert(Alert.AlertType.ERROR, "Save Failed", e.getMessage());
            table.refresh();
        }
    }

    private void printFreezerPull() {
        if (table.getItems().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Nothing To Print", "Import a usage report before printing freezer pull.");
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
            showAlert(Alert.AlertType.ERROR, "Print Failed", "The freezer pull sheet could not be printed.");
        }
    }

    private PageLayout createPrintPageLayout(PrinterJob job) {
        Printer printer = job.getPrinter();
        PageLayout pageLayout = printer.createPageLayout(Paper.NA_LETTER, PageOrientation.LANDSCAPE, Printer.MarginType.HARDWARE_MINIMUM);
        job.getJobSettings().setPageLayout(pageLayout);
        return pageLayout;
    }

    private Node createScaledPrintNode(VBox page, PageLayout pageLayout) {
        page.applyCss();
        page.autosize();
        page.layout();
        double width = page.getLayoutBounds().getWidth();
        double height = page.getLayoutBounds().getHeight();
        if (width <= 0 || height <= 0) return page;
        double scale = Math.min(1.0, Math.min(pageLayout.getPrintableWidth() / width, pageLayout.getPrintableHeight() / height));
        Group wrapper = new Group(page);
        if (scale < 1.0) page.getTransforms().add(new Scale(scale, scale));
        return wrapper;
    }

    private VBox buildPrintPage(List<ProductionReportLine> lines) {
        Label title = new Label("FREEZER PULL");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        GridPane header = new GridPane();
        header.setStyle("-fx-border-color: black; -fx-border-width: 1 0 1 0;");
        addCell(header, "ITEM", 0, 240, true, Pos.CENTER);
        addCell(header, "UNIT", 1, 48, true, Pos.CENTER);
        addCell(header, "MON", 2, 48, true, Pos.CENTER);
        addCell(header, "TUES", 3, 48, true, Pos.CENTER);
        addCell(header, "WED", 4, 48, true, Pos.CENTER);
        addCell(header, "THURS", 5, 48, true, Pos.CENTER);
        addCell(header, "FRI", 6, 48, true, Pos.CENTER);
        addCell(header, "SAT", 7, 48, true, Pos.CENTER);
        addCell(header, "SUN", 8, 48, true, Pos.CENTER);
        addCell(header, "TOTAL", 9, 55, true, Pos.CENTER);
        VBox page = new VBox(1, title, header);
        page.setPrefWidth(700);
        page.setMinWidth(700);
        page.setMaxWidth(700);
        page.setPadding(new Insets(10));
        page.setStyle("-fx-background-color: white; -fx-text-fill: black;");
        for (ProductionReportLine line : lines) {
            page.getChildren().add(createPrintLine(line));
        }
        return page;
    }

    private Node createPrintLine(ProductionReportLine line) {
        GridPane row = new GridPane();
        row.setStyle("-fx-border-color: #bbbbbb; -fx-border-width: 0 0 1 0;");
        addCell(row, line.getProductionItemName(), 0, 240, false, Pos.CENTER_LEFT);
        addCell(row, line.getUnit(), 1, 48, false, Pos.CENTER);
        addCell(row, formatNumber(line.getMondayQuantity()), 2, 48, false, Pos.CENTER);
        addCell(row, formatNumber(line.getTuesdayQuantity()), 3, 48, false, Pos.CENTER);
        addCell(row, formatNumber(line.getWednesdayQuantity()), 4, 48, false, Pos.CENTER);
        addCell(row, formatNumber(line.getThursdayQuantity()), 5, 48, false, Pos.CENTER);
        addCell(row, formatNumber(line.getFridayQuantity()), 6, 48, false, Pos.CENTER);
        addCell(row, formatNumber(line.getSaturdayQuantity()), 7, 48, false, Pos.CENTER);
        addCell(row, formatNumber(line.getSundayQuantity()), 8, 48, false, Pos.CENTER);
        addCell(row, formatNumber(line.getWeeklyQuantity()), 9, 55, false, Pos.CENTER);
        return row;
    }

    private void addCell(GridPane row, String text, int column, double width, boolean header, Pos alignment) {
        Label label = new Label(text == null ? "" : text);
        label.setPrefWidth(width);
        label.setMinHeight(header ? 14 : 12);
        label.setAlignment(alignment);
        label.setStyle("-fx-font-size: 7px;" + (header ? "-fx-font-weight: bold;" : "") + "-fx-border-color: #bbbbbb;" + "-fx-border-width: 0 1 0 0;" + "-fx-padding: 2;");
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