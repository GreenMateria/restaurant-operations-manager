package ca.foodinventory.ui;

import ca.foodinventory.dao.LabourDailyEntryDao;
import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.DailyLabourData;
import ca.foodinventory.model.LabourDailySales;
import ca.foodinventory.model.LabourTotals;
import ca.foodinventory.model.WeeklyLabourRow;
import ca.foodinventory.service.LabourApiClient;
import ca.foodinventory.service.LabourCalculationService;
import javafx.concurrent.Task;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DailyLabourView extends BorderPane {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("EEEE");

    private final LabourDailyEntryDao labourDailyEntryDao = new LabourDailyEntryDao();
    private final LabourApiClient apiClient = new LabourApiClient();
    private final LabourCalculationService calculationService = new LabourCalculationService();

    private final DatePicker startDatePicker = new DatePicker();
    private final DatePicker endDatePicker = new DatePicker();
    private final Button loadButton = primaryButton("Load Week", this::loadSelectedRange);
    private final Button saveButton = primaryButton("Save Net Sales", this::saveRange);
    private final Label statusLabel = new Label("Ready");
    private final GridPane grid = new GridPane();
    private final Map<LocalDate, DailyLabourData> dataByDate = new LinkedHashMap<>();
    private final List<TextField> netSalesFields = new ArrayList<>();
    private final List<Runnable> costRefreshers = new ArrayList<>();

    private boolean dirty;

    public DailyLabourView() {
        getStyleClass().add("root-dark");

        Label title = new Label("Daily Labour Cost");
        title.getStyleClass().add("page-title");

        LocalDate weekStart = normalizeToMonday(LocalDate.now());
        startDatePicker.setValue(weekStart);
        endDatePicker.setValue(weekStart.plusDays(6));
        startDatePicker.valueProperty().addListener((obs, oldDate, newDate) -> {
            if (newDate != null && endDatePicker.getValue() != null && endDatePicker.getValue().isBefore(newDate)) {
                endDatePicker.setValue(newDate.plusDays(6));
            }
        });

        HBox controls = new HBox(
                10,
                new Label("Start Date:"),
                startDatePicker,
                new Label("End Date:"),
                endDatePicker,
                loadButton,
                saveButton,
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
        grid.getColumnConstraints().add(column(180, HPos.LEFT));
        grid.getColumnConstraints().add(column(130, HPos.RIGHT));
        grid.getColumnConstraints().add(column(130, HPos.RIGHT));
        grid.getColumnConstraints().add(column(130, HPos.RIGHT));
        grid.getColumnConstraints().add(column(95, HPos.RIGHT));
        grid.getColumnConstraints().add(column(95, HPos.RIGHT));
        grid.getColumnConstraints().add(column(95, HPos.RIGHT));
    }

    private ColumnConstraints column(double width, HPos alignment) {
        ColumnConstraints constraints = new ColumnConstraints();
        constraints.setMinWidth(width);
        constraints.setPrefWidth(width);
        constraints.setHalignment(alignment);
        return constraints;
    }

    private void loadSelectedRange() {
        if (!confirmDiscardChanges()) {
            return;
        }
        LocalDate start = startDatePicker.getValue();
        LocalDate end = endDatePicker.getValue();
        if (!validRange(start, end)) {
            return;
        }
        loadRange(start, end);
    }

    private void loadRange(LocalDate start, LocalDate end) {
        setBusy("Loading labour cost...");
        Task<Map<LocalDate, DailyLabourData>> task = new Task<>() {
            @Override
            protected Map<LocalDate, DailyLabourData> call() {
                Map<LocalDate, DailyLabourData> loaded = new LinkedHashMap<>();
                LocalDate date = start;
                while (!date.isAfter(end)) {
                    DailyLabourData data = DatabaseManager.isApiDatabase()
                            ? apiClient.loadDailyLabour(date)
                            : labourDailyEntryDao.loadDay(date);
                    loaded.put(date, normalizeData(data, date));
                    date = date.plusDays(1);
                }
                return loaded;
            }
        };
        task.setOnSucceeded(event -> {
            dataByDate.clear();
            dataByDate.putAll(task.getValue());
            buildGrid();
            dirty = false;
            setReady("Week loaded.");
        });
        task.setOnFailed(event -> {
            setReady("Load failed.");
            showAlert(Alert.AlertType.ERROR, "Load Failed", rootCauseMessage(task.getException()));
        });
        run(task, "daily-labour-cost-load");
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
        netSalesFields.clear();
        costRefreshers.clear();

        if (dataByDate.isEmpty()) {
            grid.add(messageLabel("No labour cost data is available for this range."), 0, 0, 7, 1);
            return;
        }

        int rowIndex = 0;
        Label date = groupLabel(DATE_FORMAT.format(startDatePicker.getValue()).toUpperCase()
                + " TO " + DATE_FORMAT.format(endDatePicker.getValue()).toUpperCase());
        date.getStyleClass().add("labour-sheet-subtitle");
        grid.add(date, 0, rowIndex++, 7, 1);

        grid.add(groupLabel("ACTUAL SALES AND LABOUR"), 0, rowIndex++, 7, 1);
        addHeaderCell("DAY", 0, rowIndex);
        addHeaderCell("NET SALES", 1, rowIndex);
        addHeaderCell("BOH LABOUR $", 2, rowIndex);
        addHeaderCell("BOH LABOUR %", 3, rowIndex);
        addHeaderCell("FOH LABOUR $", 4, rowIndex);
        addHeaderCell("FOH LABOUR %", 5, rowIndex);
        addHeaderCell("LABOUR %", 6, rowIndex++);

        for (DailyLabourData data : dataByDate.values()) {
            DayTotals totals = dayTotals(data);
            addDayRow(data, totals, rowIndex++);
        }

        addTotalRow(rowIndex);
    }

    private void addDayRow(DailyLabourData data, DayTotals totals, int rowIndex) {
        TextField netSalesField = moneyField();
        netSalesField.getStyleClass().add("labour-cost-net-sales-field");
        netSalesField.setText(formatInputMoney(data.sales().getNetSales()));
        netSalesField.textProperty().addListener((obs, oldText, newText) -> handleNetSalesEdit(netSalesField, data));
        netSalesFields.add(netSalesField);

        LabourTotals combined = totals.fohTotals().add(totals.bohTotals());
        Label bohLabel = rightCellLabel("");
        Label bohPercentLabel = rightCellLabel("");
        Label fohLabel = rightCellLabel("");
        Label fohPercentLabel = rightCellLabel("");
        Label percentLabel = rightCellLabel("");
        grid.add(cellLabel(DAY_FORMAT.format(data.workDate())), 0, rowIndex);
        grid.add(netSalesField, 1, rowIndex);
        grid.add(bohLabel, 2, rowIndex);
        grid.add(bohPercentLabel, 3, rowIndex);
        grid.add(fohLabel, 4, rowIndex);
        grid.add(fohPercentLabel, 5, rowIndex);
        grid.add(percentLabel, 6, rowIndex);

        Runnable refresher = () -> {
            bohLabel.setText(formatMoney(totals.bohTotals().labourDollars()));
            bohPercentLabel.setText(formatPercent(calculationService.labourPercentage(
                    totals.bohTotals(),
                    data.sales().getNetSales()
            )));
            fohLabel.setText(formatMoney(totals.fohTotals().labourDollars()));
            fohPercentLabel.setText(formatPercent(calculationService.labourPercentage(
                    totals.fohTotals(),
                    data.sales().getNetSales()
            )));
            percentLabel.setText(formatPercent(calculationService.labourPercentage(
                    combined,
                    data.sales().getNetSales()
            )));
        };
        costRefreshers.add(refresher);
        refresher.run();
    }

    private void addTotalRow(int rowIndex) {
        Label label = totalRowLabel("TOTAL");
        Label netSalesLabel = totalRowValue("");
        Label bohLabel = totalRowValue("");
        Label bohPercentLabel = totalRowValue("");
        Label fohLabel = totalRowValue("");
        Label fohPercentLabel = totalRowValue("");
        Label percentLabel = totalRowValue("");
        grid.add(label, 0, rowIndex);
        grid.add(netSalesLabel, 1, rowIndex);
        grid.add(bohLabel, 2, rowIndex);
        grid.add(bohPercentLabel, 3, rowIndex);
        grid.add(fohLabel, 4, rowIndex);
        grid.add(fohPercentLabel, 5, rowIndex);
        grid.add(percentLabel, 6, rowIndex);

        Runnable refresher = () -> {
            LabourTotals fohTotals = LabourTotals.zero();
            LabourTotals bohTotals = LabourTotals.zero();
            BigDecimal netSales = BigDecimal.ZERO;
            for (DailyLabourData data : dataByDate.values()) {
                DayTotals totals = dayTotals(data);
                fohTotals = fohTotals.add(totals.fohTotals());
                bohTotals = bohTotals.add(totals.bohTotals());
                netSales = netSales.add(data.sales().getNetSales());
            }
            LabourTotals combined = fohTotals.add(bohTotals);
            netSalesLabel.setText(formatMoney(netSales));
            bohLabel.setText(formatMoney(bohTotals.labourDollars()));
            bohPercentLabel.setText(formatPercent(calculationService.labourPercentage(bohTotals, netSales)));
            fohLabel.setText(formatMoney(fohTotals.labourDollars()));
            fohPercentLabel.setText(formatPercent(calculationService.labourPercentage(fohTotals, netSales)));
            percentLabel.setText(formatPercent(calculationService.labourPercentage(combined, netSales)));
        };
        costRefreshers.add(refresher);
        refresher.run();
    }

    private DayTotals dayTotals(DailyLabourData data) {
        LabourTotals fohTotals = LabourTotals.zero();
        LabourTotals bohTotals = LabourTotals.zero();

        for (WeeklyLabourRow row : data.rows()) {
            LabourTotals employeeTotals = calculationService.employeeTotals(row);
            if ("FOH".equalsIgnoreCase(row.getLabourGroup())) {
                fohTotals = fohTotals.add(employeeTotals);
            } else if ("BOH".equalsIgnoreCase(row.getLabourGroup())) {
                bohTotals = bohTotals.add(employeeTotals);
            }
        }
        return new DayTotals(fohTotals, bohTotals);
    }

    private void handleNetSalesEdit(TextField field, DailyLabourData data) {
        BigDecimal netSales = parseMoney(field.getText());
        field.setStyle(netSales == null ? "-fx-border-color: #c62828;" : "");
        if (netSales == null) {
            statusLabel.setText("Invalid net sales value.");
            return;
        }
        data.sales().setNetSales(netSales);
        dirty = true;
        statusLabel.setText("Unsaved changes.");
        refreshCosts();
    }

    private void refreshCosts() {
        for (Runnable refresher : costRefreshers) {
            refresher.run();
        }
    }

    private void saveRange() {
        if (dataByDate.isEmpty()) {
            return;
        }
        String validation = validateAllFields();
        if (validation != null) {
            showAlert(Alert.AlertType.WARNING, "Invalid Daily Labour Cost", validation);
            return;
        }

        setBusy("Saving net sales...");
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                for (DailyLabourData data : dataByDate.values()) {
                    if (DatabaseManager.isApiDatabase()) {
                        apiClient.saveNetSales(data.workDate(), data.sales().getNetSales());
                    } else {
                        labourDailyEntryDao.saveNetSales(data.workDate(), data.sales().getNetSales());
                    }
                }
                return null;
            }
        };
        task.setOnSucceeded(event -> {
            dirty = false;
            setReady("Net sales saved.");
            showAlert(Alert.AlertType.INFORMATION, "Daily Labour Cost", "Net sales saved.");
            loadRange(startDatePicker.getValue(), endDatePicker.getValue());
        });
        task.setOnFailed(event -> {
            setReady("Save failed.");
            showAlert(Alert.AlertType.ERROR, "Save Failed", rootCauseMessage(task.getException()));
        });
        run(task, "daily-labour-cost-save");
    }

    private String validateAllFields() {
        for (TextField field : netSalesFields) {
            if (parseMoney(field.getText()) == null) {
                return "Net sales values must be blank, zero, or non-negative decimal numbers.";
            }
        }
        return null;
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

    private boolean confirmDiscardChanges() {
        if (!dirty) {
            return true;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Unsaved Daily Labour Cost");
        alert.setHeaderText(null);
        alert.setContentText("Discard unsaved daily labour cost changes?");
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private LocalDate normalizeToMonday(LocalDate date) {
        LocalDate value = date == null ? LocalDate.now() : date;
        return value.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private TextField moneyField() {
        TextField field = new TextField();
        field.setPrefWidth(110);
        field.setAlignment(Pos.CENTER_RIGHT);
        return field;
    }

    private BigDecimal parseMoney(String text) {
        if (text == null || text.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal value = new BigDecimal(text.trim().replace("$", "").replace(",", ""));
            return value.compareTo(BigDecimal.ZERO) < 0 ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String formatInputMoney(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) == 0) {
            return "";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private String formatMoney(BigDecimal value) {
        return "$" + decimal(value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatPercent(BigDecimal value) {
        return decimal(value).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
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
        if (current == null || current.getMessage() == null || current.getMessage().isBlank()) {
            return "The Daily Labour Cost action failed.";
        }
        return current.getMessage();
    }

    private record DayTotals(LabourTotals fohTotals, LabourTotals bohTotals) {
    }
}
