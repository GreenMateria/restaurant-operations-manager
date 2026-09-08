package ca.foodinventory.ui;

import ca.foodinventory.dao.LabourDailyEntryDao;
import ca.foodinventory.dao.LabourSettingsDao;
import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.DailyLabourData;
import ca.foodinventory.model.LabourDailySales;
import ca.foodinventory.model.LabourSettings;
import ca.foodinventory.model.WeeklyLabourRow;
import ca.foodinventory.service.LabourApiClient;
import ca.foodinventory.service.LabourCalculationService;
import javafx.concurrent.Task;
import javafx.geometry.HPos;
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
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.transform.Scale;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TipPoolBreakdownView extends BorderPane {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private final LabourDailyEntryDao labourDailyEntryDao = new LabourDailyEntryDao();
    private final LabourSettingsDao labourSettingsDao = new LabourSettingsDao();
    private final LabourApiClient apiClient = new LabourApiClient();
    private final LabourCalculationService calculationService = new LabourCalculationService();

    private final DatePicker startDatePicker = new DatePicker();
    private final DatePicker endDatePicker = new DatePicker();
    private final Button loadButton = primaryButton("Load Breakdown", this::loadSelectedRange);
    private final Button printButton = primaryButton("Print", this::printBreakdown);
    private final Label statusLabel = new Label("Ready");
    private final GridPane grid = new GridPane();
    private final Map<LocalDate, DailyLabourData> dataByDate = new LinkedHashMap<>();

    private BigDecimal uniformDeduction = BigDecimal.ZERO;

    public TipPoolBreakdownView() {
        getStyleClass().add("root-dark");

        Label title = new Label("Tip Pool Breakdown");
        title.getStyleClass().add("page-title");

        LocalDate weekStart = normalizeToMonday(LocalDate.now());
        startDatePicker.setValue(weekStart);
        endDatePicker.setValue(weekStart.plusDays(6));

        HBox controls = new HBox(
                10,
                new Label("Start Date:"),
                startDatePicker,
                new Label("End Date:"),
                endDatePicker,
                loadButton,
                printButton,
                statusLabel
        );
        controls.setAlignment(Pos.CENTER_LEFT);

        VBox top = new VBox(10, title, controls);
        top.setPadding(new Insets(12, 16, 10, 16));
        setTop(top);

        grid.setHgap(1);
        grid.setVgap(1);
        grid.setPadding(new Insets(8));
        grid.getStyleClass().add("weekly-labour-grid");
        configureColumns();

        ScrollPane scrollPane = new ScrollPane(grid);
        scrollPane.setFitToHeight(false);
        scrollPane.setFitToWidth(false);
        scrollPane.setPannable(true);
        scrollPane.getStyleClass().add("content-area");
        BorderPane.setMargin(scrollPane, new Insets(0, 12, 12, 12));
        setCenter(scrollPane);

        loadRange(startDatePicker.getValue(), endDatePicker.getValue());
    }

    private void configureColumns() {
        grid.getColumnConstraints().clear();
        grid.getColumnConstraints().add(column(210, HPos.LEFT));
        grid.getColumnConstraints().add(column(105, HPos.RIGHT));
        grid.getColumnConstraints().add(column(115, HPos.RIGHT));
        grid.getColumnConstraints().add(column(115, HPos.RIGHT));
        grid.getColumnConstraints().add(column(115, HPos.RIGHT));
    }

    private ColumnConstraints column(double width, HPos alignment) {
        ColumnConstraints constraints = new ColumnConstraints();
        constraints.setMinWidth(width);
        constraints.setPrefWidth(width);
        constraints.setHalignment(alignment);
        return constraints;
    }

    private void loadSelectedRange() {
        LocalDate start = startDatePicker.getValue();
        LocalDate end = endDatePicker.getValue();
        if (!validRange(start, end)) {
            return;
        }
        loadRange(start, end);
    }

    private void loadRange(LocalDate start, LocalDate end) {
        setBusy("Loading tip breakdown...");
        Task<BreakdownLoad> task = new Task<>() {
            @Override
            protected BreakdownLoad call() {
                Map<LocalDate, DailyLabourData> loaded = new LinkedHashMap<>();
                LocalDate date = start;
                while (!date.isAfter(end)) {
                    DailyLabourData data = DatabaseManager.isApiDatabase()
                            ? apiClient.loadDailyLabour(date)
                            : labourDailyEntryDao.loadDay(date);
                    loaded.put(date, normalizeData(data, date));
                    date = date.plusDays(1);
                }
                LabourSettings settings = DatabaseManager.isApiDatabase()
                        ? apiClient.loadSettings()
                        : labourSettingsDao.load();
                return new BreakdownLoad(loaded, settings.getDefaultUniformDeduction());
            }
        };
        task.setOnSucceeded(event -> {
            dataByDate.clear();
            dataByDate.putAll(task.getValue().dataByDate());
            uniformDeduction = task.getValue().uniformDeduction();
            buildGrid();
            setReady("Breakdown loaded.");
        });
        task.setOnFailed(event -> {
            setReady("Load failed.");
            showAlert(Alert.AlertType.ERROR, "Load Failed", rootCauseMessage(task.getException()));
        });
        run(task, "tip-pool-breakdown-load");
    }

    private DailyLabourData normalizeData(DailyLabourData data, LocalDate fallbackDate) {
        LocalDate workDate = data == null ? fallbackDate : data.workDate();
        LabourDailySales sales = data == null ? new LabourDailySales() : data.sales();
        if (sales.getSalesDate() == null) {
            sales.setSalesDate(workDate);
        }
        List<WeeklyLabourRow> rows = data == null ? List.of() : data.rows();
        for (WeeklyLabourRow row : rows) {
            row.getOrCreateEntry(workDate);
        }
        return new DailyLabourData(workDate, sales, rows);
    }

    private void buildGrid() {
        grid.getChildren().clear();
        if (dataByDate.isEmpty()) {
            grid.add(messageLabel("No tip pool data is available for this range."), 0, 0, 5, 1);
            return;
        }

        int rowIndex = 0;
        Label date = groupLabel(DATE_FORMAT.format(startDatePicker.getValue()).toUpperCase()
                + " TO " + DATE_FORMAT.format(endDatePicker.getValue()).toUpperCase());
        date.getStyleClass().add("labour-sheet-subtitle");
        grid.add(date, 0, rowIndex++, 5, 1);

        grid.add(groupLabel("TIP POOL BREAKDOWN"), 0, rowIndex++, 5, 1);
        addHeaderCell("EMPLOYEE", 0, rowIndex);
        addHeaderCell("HOURS", 1, rowIndex);
        addHeaderCell("GROSS TIP", 2, rowIndex);
        addHeaderCell("UNIFORM", 3, rowIndex);
        addHeaderCell("NET PAYOUT", 4, rowIndex++);

        List<EmployeeBreakdown> rows = employeeBreakdowns();
        if (rows.isEmpty()) {
            grid.add(messageLabel("No tip-pool eligible employees are configured for this range."), 0, rowIndex, 5, 1);
            return;
        }

        BigDecimal totalHours = BigDecimal.ZERO;
        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalUniform = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;
        for (EmployeeBreakdown row : rows) {
            BigDecimal net = calculationService.netTipPayout(row.grossTip(), row.uniformDeduction());
            totalHours = totalHours.add(row.hours());
            totalGross = totalGross.add(row.grossTip());
            totalUniform = totalUniform.add(row.uniformDeduction());
            totalNet = totalNet.add(net);

            grid.add(cellLabel(row.employeeName()), 0, rowIndex);
            grid.add(rightCellLabel(formatHours(row.hours())), 1, rowIndex);
            grid.add(rightCellLabel(formatMoney(row.grossTip())), 2, rowIndex);
            grid.add(rightCellLabel(formatMoney(row.uniformDeduction())), 3, rowIndex);
            grid.add(rightCellLabel(formatMoney(net)), 4, rowIndex++);
        }

        grid.add(totalRowLabel("TOTAL"), 0, rowIndex);
        grid.add(totalRowValue(formatHours(totalHours)), 1, rowIndex);
        grid.add(totalRowValue(formatMoney(totalGross)), 2, rowIndex);
        grid.add(totalRowValue(formatMoney(totalUniform)), 3, rowIndex);
        grid.add(totalRowValue(formatMoney(totalNet)), 4, rowIndex);
    }

    private List<EmployeeBreakdown> employeeBreakdowns() {
        Map<Integer, MutableBreakdown> employees = new LinkedHashMap<>();
        for (Map.Entry<LocalDate, DailyLabourData> day : dataByDate.entrySet()) {
            LocalDate date = day.getKey();
            for (WeeklyLabourRow row : day.getValue().rows()) {
                if (!row.isTipPoolEligible()) {
                    continue;
                }
                BigDecimal hours = calculationService.dailyHours(row.getEntriesByDate().get(date));
                if (hours.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                MutableBreakdown employee = employees.computeIfAbsent(
                        row.getEmployeeId(),
                        ignored -> new MutableBreakdown(row.getEmployeeName())
                );
                employee.hours = employee.hours.add(hours);
                employee.grossTip = employee.grossTip.add(tipAmount(date, hours));
                employee.uniformDeduction = employee.uniformDeduction.add(
                        calculationService.uniformDeductionForWorkedDay(row, date, uniformDeduction)
                );
            }
        }

        List<EmployeeBreakdown> rows = new ArrayList<>();
        for (MutableBreakdown employee : employees.values()) {
            rows.add(new EmployeeBreakdown(
                    employee.name,
                    employee.hours,
                    employee.grossTip,
                    employee.uniformDeduction
            ));
        }
        rows.sort(Comparator.comparing(EmployeeBreakdown::employeeName, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private BigDecimal totalEligibleHours(LocalDate date) {
        DailyLabourData data = dataByDate.get(date);
        if (data == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (WeeklyLabourRow row : data.rows()) {
            if (row.isTipPoolEligible()) {
                total = total.add(calculationService.dailyHours(row.getEntriesByDate().get(date)));
            }
        }
        return total;
    }

    private BigDecimal tipAmount(LocalDate date, BigDecimal employeeHours) {
        DailyLabourData data = dataByDate.get(date);
        BigDecimal totalHours = totalEligibleHours(date);
        if (data == null || totalHours.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return data.sales().getTipOutPool()
                .multiply(employeeHours)
                .divide(totalHours, 4, RoundingMode.HALF_UP);
    }

    private void printBreakdown() {
        List<EmployeeBreakdown> rows = employeeBreakdowns();
        if (rows.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Nothing To Print", "Load a tip pool breakdown before printing.");
            return;
        }

        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null || !job.showPrintDialog(getScene().getWindow())) {
            return;
        }

        PageLayout pageLayout = createPrintPageLayout(job);
        Node printNode = fitPageToPrintableArea(buildPrintPage(rows), pageLayout);
        if (job.printPage(pageLayout, printNode)) {
            job.endJob();
        } else {
            showAlert(Alert.AlertType.ERROR, "Print Failed", "The tip pool breakdown could not be printed.");
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

    private Node fitPageToPrintableArea(VBox page, PageLayout pageLayout) {
        page.applyCss();
        page.autosize();
        page.layout();

        double contentWidth = page.getLayoutBounds().getWidth();
        double contentHeight = page.getLayoutBounds().getHeight();
        if (contentWidth <= 0 || contentHeight <= 0) {
            return page;
        }

        double scale = Math.min(
                1.0,
                Math.min(
                        pageLayout.getPrintableWidth() / contentWidth,
                        pageLayout.getPrintableHeight() / contentHeight
                )
        );
        if (scale < 1.0) {
            page.getTransforms().add(new Scale(scale, scale));
        }
        return new Group(page);
    }

    private VBox buildPrintPage(List<EmployeeBreakdown> rows) {
        Label title = printLabel("TIP POOL BREAKDOWN", 15, true, Pos.CENTER_LEFT);
        Label dateRange = printLabel(
                DATE_FORMAT.format(startDatePicker.getValue()).toUpperCase()
                        + " TO " + DATE_FORMAT.format(endDatePicker.getValue()).toUpperCase(),
                10,
                false,
                Pos.CENTER_LEFT
        );

        GridPane header = createPrintGrid();
        addPrintCell(header, "EMPLOYEE", 0, 190, true, Pos.CENTER_LEFT);
        addPrintCell(header, "HOURS", 1, 70, true, Pos.CENTER_RIGHT);
        addPrintCell(header, "GROSS TIP", 2, 90, true, Pos.CENTER_RIGHT);
        addPrintCell(header, "UNIFORM", 3, 80, true, Pos.CENTER_RIGHT);
        addPrintCell(header, "NET PAYOUT", 4, 90, true, Pos.CENTER_RIGHT);

        VBox page = new VBox(2, title, dateRange, header);
        page.setPrefWidth(540);
        page.setMinWidth(540);
        page.setMaxWidth(540);
        page.setPadding(new Insets(12));
        page.setStyle("-fx-background-color: white;");

        BigDecimal totalHours = BigDecimal.ZERO;
        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalUniform = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;
        for (EmployeeBreakdown row : rows) {
            BigDecimal net = calculationService.netTipPayout(row.grossTip(), row.uniformDeduction());
            totalHours = totalHours.add(row.hours());
            totalGross = totalGross.add(row.grossTip());
            totalUniform = totalUniform.add(row.uniformDeduction());
            totalNet = totalNet.add(net);

            GridPane line = createPrintGrid();
            addPrintCell(line, row.employeeName(), 0, 190, false, Pos.CENTER_LEFT);
            addPrintCell(line, formatHours(row.hours()), 1, 70, false, Pos.CENTER_RIGHT);
            addPrintCell(line, formatMoney(row.grossTip()), 2, 90, false, Pos.CENTER_RIGHT);
            addPrintCell(line, formatMoney(row.uniformDeduction()), 3, 80, false, Pos.CENTER_RIGHT);
            addPrintCell(line, formatMoney(net), 4, 90, false, Pos.CENTER_RIGHT);
            page.getChildren().add(line);
        }

        GridPane total = createPrintGrid();
        addPrintCell(total, "TOTAL", 0, 190, true, Pos.CENTER_LEFT);
        addPrintCell(total, formatHours(totalHours), 1, 70, true, Pos.CENTER_RIGHT);
        addPrintCell(total, formatMoney(totalGross), 2, 90, true, Pos.CENTER_RIGHT);
        addPrintCell(total, formatMoney(totalUniform), 3, 80, true, Pos.CENTER_RIGHT);
        addPrintCell(total, formatMoney(totalNet), 4, 90, true, Pos.CENTER_RIGHT);
        page.getChildren().add(total);

        return page;
    }

    private GridPane createPrintGrid() {
        GridPane row = new GridPane();
        row.setStyle("-fx-border-color: #bbbbbb; -fx-border-width: 0 0 1 0;");
        return row;
    }

    private void addPrintCell(
            GridPane row,
            String text,
            int column,
            double width,
            boolean bold,
            Pos alignment
    ) {
        Label label = printLabel(text, 8, bold, alignment);
        label.setPrefWidth(width);
        label.setMinHeight(bold ? 18 : 16);
        label.setStyle(label.getStyle()
                + "-fx-border-color: #bbbbbb;"
                + "-fx-border-width: 0 1 0 0;"
                + "-fx-padding: 2 4 2 4;");
        row.add(label, column, 0);
    }

    private Label printLabel(String text, int fontSize, boolean bold, Pos alignment) {
        Label label = new Label(text == null ? "" : text);
        label.setAlignment(alignment);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setStyle("-fx-text-fill: black;"
                + "-fx-font-size: " + fontSize + "px;"
                + (bold ? "-fx-font-weight: bold;" : ""));
        return label;
    }

    private boolean validRange(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            showAlert(Alert.AlertType.WARNING, "Invalid Date Range", "Start date and end date are required.");
            return false;
        }
        if (end.isBefore(start)) {
            showAlert(Alert.AlertType.WARNING, "Invalid Date Range", "End date cannot be before start date.");
            return false;
        }
        return true;
    }

    private LocalDate normalizeToMonday(LocalDate date) {
        LocalDate value = date == null ? LocalDate.now() : date;
        return value.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private String formatHours(BigDecimal value) {
        return decimal(value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatMoney(BigDecimal value) {
        return "$" + decimal(value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private BigDecimal decimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Label cellLabel(String text) {
        Label label = new Label(text == null ? "" : text);
        label.setMinHeight(28);
        label.setMaxWidth(Double.MAX_VALUE);
        label.getStyleClass().add("weekly-labour-cell");
        return label;
    }

    private Label rightCellLabel(String text) {
        Label label = cellLabel(text);
        label.setAlignment(Pos.CENTER_RIGHT);
        return label;
    }

    private Label groupLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("weekly-labour-group");
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private Label totalRowLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("weekly-labour-total");
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private Label totalRowValue(String text) {
        Label label = totalRowLabel(text);
        label.setAlignment(Pos.CENTER_RIGHT);
        return label;
    }

    private Label messageLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    private void addHeaderCell(String text, int column, int row) {
        Label label = new Label(text);
        label.setAlignment(Pos.CENTER);
        label.setMinHeight(34);
        label.setMaxWidth(Double.MAX_VALUE);
        label.getStyleClass().add("weekly-labour-header");
        grid.add(label, column, row);
    }

    private Button primaryButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("primary-button");
        button.setOnAction(event -> action.run());
        return button;
    }

    private void setBusy(String message) {
        statusLabel.setText(message);
        setDisable(true);
    }

    private void setReady(String message) {
        statusLabel.setText(message);
        setDisable(false);
    }

    private void run(Task<?> task, String threadName) {
        Thread thread = new Thread(task, threadName);
        thread.setDaemon(true);
        thread.start();
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        return current == null || current.getMessage() == null || current.getMessage().isBlank()
                ? "The Tip Pool Breakdown action failed."
                : current.getMessage();
    }

    private static class MutableBreakdown {
        private final String name;
        private BigDecimal hours = BigDecimal.ZERO;
        private BigDecimal grossTip = BigDecimal.ZERO;
        private BigDecimal uniformDeduction = BigDecimal.ZERO;

        MutableBreakdown(String name) {
            this.name = name;
        }
    }

    private record BreakdownLoad(Map<LocalDate, DailyLabourData> dataByDate, BigDecimal uniformDeduction) {
    }

    private record EmployeeBreakdown(
            String employeeName,
            BigDecimal hours,
            BigDecimal grossTip,
            BigDecimal uniformDeduction
    ) {
    }
}
