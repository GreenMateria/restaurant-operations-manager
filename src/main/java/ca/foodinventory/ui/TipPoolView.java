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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TipPoolView extends BorderPane {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("EEE");

    private final LabourDailyEntryDao labourDailyEntryDao = new LabourDailyEntryDao();
    private final LabourApiClient apiClient = new LabourApiClient();
    private final LabourCalculationService calculationService = new LabourCalculationService();

    private final DatePicker startDatePicker = new DatePicker();
    private final DatePicker endDatePicker = new DatePicker();
    private final Button loadButton = primaryButton("Load Week", this::loadSelectedRange);
    private final Button saveButton = primaryButton("Save Tip Pool", this::saveRange);
    private final Label statusLabel = new Label("Ready");
    private final GridPane grid = new GridPane();
    private final Map<LocalDate, DailyLabourData> dataByDate = new LinkedHashMap<>();
    private final List<TextField> tipPoolFields = new ArrayList<>();
    private final List<Runnable> tipRefreshers = new ArrayList<>();

    private boolean dirty;

    public TipPoolView() {
        getStyleClass().add("root-dark");

        Label title = new Label("Tip Pool");
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
        grid.getColumnConstraints().add(column(170, HPos.LEFT));
        for (int i = 0; i < 14; i++) {
            grid.getColumnConstraints().add(column(70, HPos.RIGHT));
        }
        grid.getColumnConstraints().add(column(80, HPos.RIGHT));
        grid.getColumnConstraints().add(column(90, HPos.RIGHT));
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
        setBusy("Loading tip pool...");
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
            setReady("Tip pool loaded.");
        });
        task.setOnFailed(event -> {
            setReady("Load failed.");
            showAlert(Alert.AlertType.ERROR, "Load Failed", rootCauseMessage(task.getException()));
        });
        run(task, "tip-pool-load");
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
        tipPoolFields.clear();
        tipRefreshers.clear();

        if (dataByDate.isEmpty()) {
            grid.add(messageLabel("No tip pool data is available for this range."), 0, 0, 17, 1);
            return;
        }

        int rowIndex = 0;
        Label date = groupLabel(DATE_FORMAT.format(startDatePicker.getValue()).toUpperCase()
                + " TO " + DATE_FORMAT.format(endDatePicker.getValue()).toUpperCase());
        date.getStyleClass().add("labour-sheet-subtitle");
        grid.add(date, 0, rowIndex++, 17, 1);

        addDayHeaders(rowIndex++);
        addSubHeaders(rowIndex++);
        addTipPoolAmountRow(rowIndex++);

        List<EmployeeTipRow> employees = employeeRows();
        if (employees.isEmpty()) {
            grid.add(messageLabel("No tip-pool eligible employees are configured."), 0, rowIndex, 17, 1);
            return;
        }

        for (EmployeeTipRow employee : employees) {
            addEmployeeRow(employee, rowIndex++);
        }

        addTotalRow(rowIndex);
        refreshTips();
    }

    private void addDayHeaders(int rowIndex) {
        addHeaderCell("NAME", 0, rowIndex);
        int column = 1;
        for (LocalDate date : dataByDate.keySet()) {
            addHeaderCell(DAY_FORMAT.format(date).toUpperCase() + "\n" + DATE_FORMAT.format(date), column, rowIndex, 2);
            column += 2;
        }
        addHeaderCell("TOTAL\nHOURS", column, rowIndex);
        addHeaderCell("TOTAL\nTIP", column + 1, rowIndex);
    }

    private void addSubHeaders(int rowIndex) {
        addHeaderCell("", 0, rowIndex);
        int column = 1;
        for (int i = 0; i < dataByDate.size(); i++) {
            addHeaderCell("HOURS", column++, rowIndex);
            addHeaderCell("TIP", column++, rowIndex);
        }
        addHeaderCell("", column++, rowIndex);
        addHeaderCell("", column, rowIndex);
    }

    private void addTipPoolAmountRow(int rowIndex) {
        grid.add(groupLabel("TIP OUT AMOUNTS"), 0, rowIndex);
        int column = 1;
        for (DailyLabourData data : dataByDate.values()) {
            TextField field = moneyField(data.sales().getTipOutPool());
            field.getStyleClass().add("labour-cost-net-sales-field");
            field.textProperty().addListener((obs, oldText, newText) -> handleTipPoolEdit(field, data));
            tipPoolFields.add(field);
            grid.add(rightCellLabel(""), column, rowIndex);
            grid.add(field, column + 1, rowIndex);
            column += 2;
        }
        grid.add(rightCellLabel(""), column++, rowIndex);
        grid.add(rightCellLabel(""), column, rowIndex);
    }

    private void addEmployeeRow(EmployeeTipRow employee, int rowIndex) {
        grid.add(cellLabel(employee.name()), 0, rowIndex);
        int column = 1;
        Label totalHoursLabel = rightCellLabel("");
        Label totalTipLabel = rightCellLabel("");
        for (LocalDate date : dataByDate.keySet()) {
            Label hoursLabel = rightCellLabel("");
            Label tipLabel = rightCellLabel("");
            grid.add(hoursLabel, column, rowIndex);
            grid.add(tipLabel, column + 1, rowIndex);
            LocalDate workDate = date;
            tipRefreshers.add(() -> {
                BigDecimal hours = employee.hoursByDate().getOrDefault(workDate, BigDecimal.ZERO);
                BigDecimal tip = tipAmount(workDate, hours);
                hoursLabel.setText(formatHours(hours));
                tipLabel.setText(formatMoney(tip));
            });
            column += 2;
        }
        grid.add(totalHoursLabel, column++, rowIndex);
        grid.add(totalTipLabel, column, rowIndex);
        tipRefreshers.add(() -> {
            BigDecimal totalHours = BigDecimal.ZERO;
            BigDecimal totalTip = BigDecimal.ZERO;
            for (Map.Entry<LocalDate, BigDecimal> entry : employee.hoursByDate().entrySet()) {
                totalHours = totalHours.add(entry.getValue());
                totalTip = totalTip.add(tipAmount(entry.getKey(), entry.getValue()));
            }
            totalHoursLabel.setText(formatHours(totalHours));
            totalTipLabel.setText(formatMoney(totalTip));
        });
    }

    private void addTotalRow(int rowIndex) {
        grid.add(totalRowLabel("TOTAL"), 0, rowIndex);
        int column = 1;
        Label totalHoursLabel = totalRowValue("");
        Label totalTipLabel = totalRowValue("");
        for (LocalDate date : dataByDate.keySet()) {
            Label hoursLabel = totalRowValue("");
            Label tipLabel = totalRowValue("");
            grid.add(hoursLabel, column, rowIndex);
            grid.add(tipLabel, column + 1, rowIndex);
            LocalDate workDate = date;
            tipRefreshers.add(() -> {
                hoursLabel.setText(formatHours(totalEligibleHours(workDate)));
                tipLabel.setText(formatMoney(dataByDate.get(workDate).sales().getTipOutPool()));
            });
            column += 2;
        }
        grid.add(totalHoursLabel, column++, rowIndex);
        grid.add(totalTipLabel, column, rowIndex);
        tipRefreshers.add(() -> {
            BigDecimal totalHours = BigDecimal.ZERO;
            BigDecimal totalTip = BigDecimal.ZERO;
            for (LocalDate date : dataByDate.keySet()) {
                totalHours = totalHours.add(totalEligibleHours(date));
                totalTip = totalTip.add(dataByDate.get(date).sales().getTipOutPool());
            }
            totalHoursLabel.setText(formatHours(totalHours));
            totalTipLabel.setText(formatMoney(totalTip));
        });
    }

    private List<EmployeeTipRow> employeeRows() {
        Map<Integer, EmployeeTipRow> employees = new LinkedHashMap<>();
        for (Map.Entry<LocalDate, DailyLabourData> day : dataByDate.entrySet()) {
            for (WeeklyLabourRow row : day.getValue().rows()) {
                if (!row.isTipPoolEligible()) {
                    continue;
                }
                EmployeeTipRow employee = employees.computeIfAbsent(
                        row.getEmployeeId(),
                        ignored -> new EmployeeTipRow(row.getEmployeeId(), row.getEmployeeName(), new LinkedHashMap<>())
                );
                employee.hoursByDate().put(day.getKey(), calculationService.employeeTotals(row).hours());
            }
        }
        List<EmployeeTipRow> rows = new ArrayList<>(employees.values());
        rows.sort(Comparator.comparing(EmployeeTipRow::name, String.CASE_INSENSITIVE_ORDER));
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
                total = total.add(calculationService.employeeTotals(row).hours());
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

    private void handleTipPoolEdit(TextField field, DailyLabourData data) {
        BigDecimal value = parseMoney(field.getText());
        field.setStyle(value == null ? "-fx-border-color: #c62828;" : "");
        if (value == null) {
            statusLabel.setText("Invalid tip pool amount.");
            return;
        }
        data.sales().setTipOutPool(value);
        dirty = true;
        statusLabel.setText("Unsaved changes.");
        refreshTips();
    }

    private void refreshTips() {
        for (Runnable refresher : tipRefreshers) {
            refresher.run();
        }
    }

    private void saveRange() {
        if (dataByDate.isEmpty()) {
            return;
        }
        String validation = validateAllFields();
        if (validation != null) {
            showAlert(Alert.AlertType.WARNING, "Invalid Tip Pool", validation);
            return;
        }

        setBusy("Saving tip pool...");
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                for (DailyLabourData data : dataByDate.values()) {
                    if (DatabaseManager.isApiDatabase()) {
                        apiClient.saveDailyLabour(data);
                    } else {
                        labourDailyEntryDao.saveDay(data);
                    }
                }
                return null;
            }
        };
        task.setOnSucceeded(event -> {
            dirty = false;
            setReady("Tip pool saved.");
            showAlert(Alert.AlertType.INFORMATION, "Tip Pool", "Tip pool saved.");
            loadRange(startDatePicker.getValue(), endDatePicker.getValue());
        });
        task.setOnFailed(event -> {
            setReady("Save failed.");
            showAlert(Alert.AlertType.ERROR, "Save Failed", rootCauseMessage(task.getException()));
        });
        run(task, "tip-pool-save");
    }

    private String validateAllFields() {
        for (TextField field : tipPoolFields) {
            if (parseMoney(field.getText()) == null) {
                return "Tip pool amounts must be blank, zero, or non-negative decimal numbers.";
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
        alert.setTitle("Unsaved Tip Pool");
        alert.setHeaderText(null);
        alert.setContentText("Discard unsaved tip pool changes?");
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private LocalDate normalizeToMonday(LocalDate date) {
        LocalDate value = date == null ? LocalDate.now() : date;
        return value.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private TextField moneyField(BigDecimal value) {
        TextField field = new TextField(formatInputMoney(value));
        field.setPrefWidth(66);
        field.setAlignment(Pos.CENTER_RIGHT);
        field.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (isFocused) {
                field.selectAll();
            }
        });
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
        addHeaderCell(text, column, row, 1);
    }

    private void addHeaderCell(String text, int column, int row, int columnSpan) {
        Label label = new Label(text);
        label.setAlignment(Pos.CENTER);
        label.setMinHeight(34);
        label.setMaxWidth(Double.MAX_VALUE);
        label.getStyleClass().add("weekly-labour-header");
        grid.add(label, column, row, columnSpan, 1);
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
                ? "The Tip Pool action failed."
                : current.getMessage();
    }

    private record EmployeeTipRow(int employeeId, String name, Map<LocalDate, BigDecimal> hoursByDate) {
    }
}
