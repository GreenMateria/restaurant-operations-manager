package ca.foodinventory.ui;

import ca.foodinventory.dao.LabourDailyEntryDao;
import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.LabourDailyEntry;
import ca.foodinventory.model.LabourTotals;
import ca.foodinventory.model.WeeklyLabourData;
import ca.foodinventory.model.WeeklyLabourRow;
import ca.foodinventory.model.WeeklyLabourSummary;
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
import javafx.scene.input.KeyCode;
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

public class
WeeklyLabourView extends BorderPane {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("EEE");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d");

    private final LabourDailyEntryDao labourDailyEntryDao = new LabourDailyEntryDao();
    private final LabourApiClient apiClient = new LabourApiClient();
    private final LabourCalculationService calculationService = new LabourCalculationService();

    private final DatePicker weekStartPicker = new DatePicker();
    private final Button loadButton = primaryButton("Load Week", this::loadSelectedWeek);
    private final Button saveButton = primaryButton("Save Hours", this::saveWeek);
    private final Label statusLabel = new Label("Ready");
    private final GridPane grid = new GridPane();
    private final List<TextField> entryFields = new ArrayList<>();
    private final Map<WeeklyLabourRow, Label> rowHoursLabels = new LinkedHashMap<>();
    private final Map<WeeklyLabourRow, Label> rowDollarsLabels = new LinkedHashMap<>();
    private final Map<String, Label> groupHoursLabels = new LinkedHashMap<>();
    private final Map<String, Label> groupDollarsLabels = new LinkedHashMap<>();
    private Map<String, List<WeeklyLabourRow>> currentRowsByPosition = new LinkedHashMap<>();

    private final Label fohHoursLabel = summaryValue();
    private final Label fohDollarsLabel = summaryValue();
    private final Label bohHoursLabel = summaryValue();
    private final Label bohDollarsLabel = summaryValue();
    private final Label totalHoursLabel = summaryValue();
    private final Label totalDollarsLabel = summaryValue();

    private WeeklyLabourData currentData;
    private boolean dirty;

    public WeeklyLabourView() {
        this(LocalDate.now());
    }

    public WeeklyLabourView(LocalDate initialWeekStart) {
        getStyleClass().add("root-dark");

        Label title = new Label("Labour Hours");
        title.getStyleClass().add("page-title");

        weekStartPicker.setValue(normalizeToMonday(initialWeekStart));
        weekStartPicker.valueProperty().addListener((obs, oldDate, newDate) -> {
            LocalDate normalized = normalizeToMonday(newDate);
            if (newDate != null && !newDate.equals(normalized)) {
                weekStartPicker.setValue(normalized);
            }
        });

        HBox controls = new HBox(
                10,
                new Label("Week Starting:"),
                weekStartPicker,
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
        BorderPane.setMargin(scrollPane, new Insets(0, 12, 0, 12));
        setCenter(scrollPane);

        setBottom(null);
        loadWeek(weekStartPicker.getValue());
    }

    private void configureColumns() {
        grid.getColumnConstraints().clear();
        grid.getColumnConstraints().add(column(170, HPos.LEFT));
        for (int i = 0; i < 14; i++) {
            grid.getColumnConstraints().add(column(56, HPos.CENTER));
        }
        grid.getColumnConstraints().add(column(76, HPos.RIGHT));
    }

    private ColumnConstraints column(double width, HPos alignment) {
        ColumnConstraints constraints = new ColumnConstraints();
        constraints.setMinWidth(width);
        constraints.setPrefWidth(width);
        constraints.setHalignment(alignment);
        return constraints;
    }

    private VBox buildSummary() {
        HBox row = new HBox(
                14,
                summaryBox("FOH Hours", fohHoursLabel),
                summaryBox("FOH Labour $", fohDollarsLabel),
                summaryBox("BOH Hours", bohHoursLabel),
                summaryBox("BOH Labour $", bohDollarsLabel),
                summaryBox("Total Variable Hours", totalHoursLabel),
                summaryBox("Total Variable Labour $", totalDollarsLabel)
        );
        row.setAlignment(Pos.CENTER_LEFT);

        VBox bottom = new VBox(row);
        bottom.setPadding(new Insets(8, 12, 10, 12));
        bottom.getStyleClass().add("content-area");
        return bottom;
    }

    private VBox summaryBox(String labelText, Label valueLabel) {
        Label label = new Label(labelText);
        label.getStyleClass().add("weekly-labour-summary-label");
        VBox box = new VBox(3, label, valueLabel);
        box.setMinWidth(108);
        return box;
    }

    private Label summaryValue() {
        Label label = new Label("0.00");
        label.getStyleClass().add("section-title");
        return label;
    }

    private void loadSelectedWeek() {
        LocalDate selected = normalizeToMonday(weekStartPicker.getValue());
        if (!confirmDiscardChanges()) {
            return;
        }
        weekStartPicker.setValue(selected);
        loadWeek(selected);
    }

    private void loadWeek(LocalDate weekStartDate) {
        setBusy("Loading week...");
        Task<WeeklyLabourData> task = new Task<>() {
            @Override
            protected WeeklyLabourData call() {
                if (DatabaseManager.isApiDatabase()) {
                    return apiClient.loadWeeklyLabour(weekStartDate);
                }
                return labourDailyEntryDao.loadWeek(weekStartDate);
            }
        };
        task.setOnSucceeded(event -> {
            currentData = normalizeData(task.getValue());
            dirty = false;
            buildGrid();
            setReady("Week loaded.");
        });
        task.setOnFailed(event -> {
            setReady("Load failed.");
            showAlert(Alert.AlertType.ERROR, "Load Failed", rootCauseMessage(task.getException()));
        });
        run(task, "weekly-labour-load");
    }

    private WeeklyLabourData normalizeData(WeeklyLabourData data) {
        List<WeeklyLabourRow> rows = data == null ? List.of() : data.rows();
        LocalDate weekStart = data == null
                ? normalizeToMonday(weekStartPicker.getValue())
                : data.weekStartDate();

        for (WeeklyLabourRow row : rows) {
            for (int day = 0; day < 7; day++) {
                row.getOrCreateEntry(weekStart.plusDays(day));
            }
        }
        return new WeeklyLabourData(weekStart, rows);
    }

    private void buildGrid() {
        grid.getChildren().clear();
        entryFields.clear();
        rowHoursLabels.clear();
        rowDollarsLabels.clear();
        groupHoursLabels.clear();
        groupDollarsLabels.clear();

        if (currentData == null || currentData.rows().isEmpty()) {
            grid.add(messageLabel("No active labour employees are configured."), 0, 0, 6, 1);
            updateSummary();
            return;
        }

        int rowIndex = 0;
        addDateBand(rowIndex++);
        addHeader(rowIndex);
        rowIndex += 2;

        currentRowsByPosition = rowsByPosition();
        for (Map.Entry<String, List<WeeklyLabourRow>> group : currentRowsByPosition.entrySet()) {
            grid.add(positionGroupLabel("DEPARTMENT: " + group.getKey().toUpperCase()), 0, rowIndex++, 16, 1);

            for (WeeklyLabourRow labourRow : group.getValue()) {
                addEmployeeRow(labourRow, rowIndex++);
            }
        }

        wireKeyboardTraversal();
        updateAllTotals();
    }

    private void addDateBand(int rowIndex) {
        LocalDate start = currentData.weekStartDate();
        Label label = groupLabel("WEEK OF " + DATE_FORMAT.format(start).toUpperCase()
                + " TO " + DATE_FORMAT.format(start.plusDays(6)).toUpperCase());
        label.getStyleClass().add("labour-sheet-subtitle");
        grid.add(label, 0, rowIndex, 16, 1);
    }

    private void addHeader(int rowIndex) {
        addHeaderCell("NAME", 0, rowIndex, 1, 1);
        for (int day = 0; day < 7; day++) {
            LocalDate date = currentData.weekStartDate().plusDays(day);
            addHeaderCell(DAY_FORMAT.format(date).toUpperCase() + "\n" + DATE_FORMAT.format(date),
                    1 + day * 2,
                    rowIndex,
                    2,
                    1);
            addHeaderCell("SHIFT 1", 1 + day * 2, rowIndex + 1, 1, 1);
            addHeaderCell("SHIFT 2", 2 + day * 2, rowIndex + 1, 1, 1);
        }
        addHeaderCell("TOTAL\nHOURS", 15, rowIndex, 1, 1);
    }

    private void addEmployeeRow(WeeklyLabourRow labourRow, int rowIndex) {
        grid.add(cellLabel(labourRow.getEmployeeName()), 0, rowIndex);

        List<TextField> rowFields = new ArrayList<>();
        for (int day = 0; day < 7; day++) {
            LocalDate date = currentData.weekStartDate().plusDays(day);
            LabourDailyEntry entry = labourRow.getOrCreateEntry(date);
            TextField shift1 = hoursField(entry.getShift1Hours());
            TextField shift2 = hoursField(entry.getShift2Hours());
            rowFields.add(shift1);
            rowFields.add(shift2);
            entryFields.add(shift1);
            entryFields.add(shift2);
            grid.add(shift1, 1 + day * 2, rowIndex);
            grid.add(shift2, 2 + day * 2, rowIndex);

            shift1.textProperty().addListener((obs, oldText, newText) ->
                    handleHoursEdit(shift1, entry, true));
            shift2.textProperty().addListener((obs, oldText, newText) ->
                    handleHoursEdit(shift2, entry, false));
        }

        Label totalHours = rightCellLabel("");
        grid.add(totalHours, 15, rowIndex);
        rowHoursLabels.put(labourRow, totalHours);

        Runnable refreshRow = () -> {
            LabourTotals totals = calculationService.employeeTotals(labourRow);
            totalHours.setText(formatHours(totals.hours()));
        };
        rowFields.forEach(field -> field.textProperty().addListener((obs, oldText, newText) -> refreshRow.run()));
        refreshRow.run();
    }

    private TextField hoursField(BigDecimal value) {
        TextField field = new TextField(formatInputHours(value));
        field.setPrefWidth(50);
        field.setMinWidth(50);
        field.setMaxWidth(50);
        field.setAlignment(Pos.CENTER_RIGHT);
        field.getStyleClass().add("weekly-labour-hours-field");
        field.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (isFocused) {
                field.selectAll();
            }
        });
        field.setOnMouseClicked(event -> {
            if (field.isFocused()) {
                field.selectAll();
            }
        });
        return field;
    }

    private void handleHoursEdit(TextField field, LabourDailyEntry entry, boolean shift1) {
        BigDecimal value = parseHours(field.getText());
        field.setStyle(value == null ? "-fx-border-color: #c62828;" : "");
        if (value == null) {
            statusLabel.setText("Invalid hours value.");
            return;
        }

        if (shift1) {
            entry.setShift1Hours(value);
        } else {
            entry.setShift2Hours(value);
        }
        dirty = true;
        statusLabel.setText("Unsaved changes.");
        updateAllTotals();
    }

    private Map<String, List<WeeklyLabourRow>> rowsByPosition() {
        Map<String, List<WeeklyLabourRow>> rowsByPosition = new LinkedHashMap<>();
        for (WeeklyLabourRow row : currentData.rows()) {
            String position = row.getPositionName();
            if (position == null || position.isBlank()) {
                position = "Unassigned";
            }
            rowsByPosition.computeIfAbsent(position, ignored -> new ArrayList<>()).add(row);
        }
        return rowsByPosition;
    }

    private LabourTotals positionTotals(List<WeeklyLabourRow> rows) {
        LabourTotals totals = LabourTotals.zero();
        for (WeeklyLabourRow row : rows) {
            totals = totals.add(calculationService.employeeTotals(row));
        }
        return totals;
    }

    private void updateAllTotals() {
        for (WeeklyLabourRow row : rowHoursLabels.keySet()) {
            LabourTotals totals = calculationService.employeeTotals(row);
            rowHoursLabels.get(row).setText(formatHours(totals.hours()));
            Label dollarsLabel = rowDollarsLabels.get(row);
            if (dollarsLabel != null) {
                dollarsLabel.setText(formatMoney(totals.labourDollars()));
            }
        }

        for (Map.Entry<String, List<WeeklyLabourRow>> group : currentRowsByPosition.entrySet()) {
            LabourTotals totals = positionTotals(group.getValue());
            Label hours = groupHoursLabels.get(group.getKey());
            Label dollars = groupDollarsLabels.get(group.getKey());
            if (hours != null) {
                hours.setText(formatHours(totals.hours()));
            }
            if (dollars != null) {
                dollars.setText(formatMoney(totals.labourDollars()));
            }
        }

        updateSummary();
    }

    private void updateSummary() {
        WeeklyLabourSummary summary = calculationService.weeklySummary(
                currentData == null ? List.of() : currentData.rows()
        );
        fohHoursLabel.setText(formatHours(summary.fohTotals().hours()));
        fohDollarsLabel.setText(formatMoney(summary.fohTotals().labourDollars()));
        bohHoursLabel.setText(formatHours(summary.bohTotals().hours()));
        bohDollarsLabel.setText(formatMoney(summary.bohTotals().labourDollars()));
        totalHoursLabel.setText(formatHours(summary.totalVariableTotals().hours()));
        totalDollarsLabel.setText(formatMoney(summary.totalVariableTotals().labourDollars()));
    }

    private void saveWeek() {
        if (currentData == null) {
            return;
        }
        String validation = validateAllFields();
        if (validation != null) {
            showAlert(Alert.AlertType.WARNING, "Invalid Hours", validation);
            return;
        }

        setBusy("Saving week...");
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                if (DatabaseManager.isApiDatabase()) {
                    apiClient.saveWeeklyLabour(currentData);
                } else {
                    labourDailyEntryDao.saveWeek(currentData.rows(), currentData.weekStartDate());
                }
                return null;
            }
        };
        task.setOnSucceeded(event -> {
            dirty = false;
            setReady("Week saved.");
            showAlert(Alert.AlertType.INFORMATION, "Labour Hours", "Week saved.");
            loadWeek(currentData.weekStartDate());
        });
        task.setOnFailed(event -> {
            setReady("Save failed.");
            showAlert(Alert.AlertType.ERROR, "Save Failed", rootCauseMessage(task.getException()));
        });
        run(task, "weekly-labour-save");
    }

    private String validateAllFields() {
        for (TextField field : entryFields) {
            if (parseHours(field.getText()) == null) {
                return "Hours must be blank, zero, or a non-negative decimal number.";
            }
        }
        return null;
    }

    private void wireKeyboardTraversal() {
        for (int i = 0; i < entryFields.size(); i++) {
            TextField field = entryFields.get(i);
            int nextIndex = i + 1;
            field.setOnAction(event -> focusField(nextIndex));
            field.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.ENTER) {
                    focusField(nextIndex);
                    event.consume();
                }
            });
        }
    }

    private void focusField(int index) {
        if (index >= 0 && index < entryFields.size()) {
            TextField next = entryFields.get(index);
            next.requestFocus();
            next.selectAll();
        }
    }

    private boolean confirmDiscardChanges() {
        if (!dirty) {
            return true;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Unsaved Labour Hours");
        alert.setHeaderText(null);
        alert.setContentText("Discard unsaved labour hours changes?");
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private LocalDate normalizeToMonday(LocalDate date) {
        LocalDate value = date == null ? LocalDate.now() : date;
        return value.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private BigDecimal parseHours(String text) {
        if (text == null || text.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal value = new BigDecimal(text.trim());
            return value.compareTo(BigDecimal.ZERO) < 0 ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String formatInputHours(BigDecimal value) {
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
        label.setMinHeight(26);
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

    private Label positionGroupLabel(String text) {
        Label label = groupLabel(text);
        label.getStyleClass().add("weekly-labour-position-group");
        label.setMinHeight(34);
        return label;
    }

    private Label totalLabel() {
        Label label = groupLabel("");
        label.setAlignment(Pos.CENTER_RIGHT);
        return label;
    }

    private Label totalRowLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("weekly-labour-total");
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private Label totalRowValue() {
        Label label = totalRowLabel("");
        label.setAlignment(Pos.CENTER_RIGHT);
        return label;
    }

    private Label messageLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    private void addHeaderCell(String text, int column, int row) {
        addHeaderCell(text, column, row, 1, 1);
    }

    private void addHeaderCell(String text, int column, int row, int columnSpan, int rowSpan) {
        Label label = new Label(text);
        label.setAlignment(Pos.CENTER);
        label.setMinHeight(44);
        label.setMaxWidth(Double.MAX_VALUE);
        label.getStyleClass().add("weekly-labour-header");
        grid.add(label, column, row, columnSpan, rowSpan);
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
            return "The Labour Hours action failed.";
        }
        return current.getMessage();
    }
}
