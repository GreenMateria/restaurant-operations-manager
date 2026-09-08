package ca.foodinventory.ui;

import ca.foodinventory.dao.LabourDailyEntryDao;
import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.service.LabourApiClient;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

public class LabourHoursView extends BorderPane {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private final LabourDailyEntryDao labourDailyEntryDao = new LabourDailyEntryDao();
    private final LabourApiClient apiClient = new LabourApiClient();
    private final TableView<LabourWeekRow> table = new TableView<>();
    private final Label statusLabel = new Label("Ready");

    public LabourHoursView() {
        getStyleClass().add("root-dark");

        Label title = new Label("Labour Hours");
        title.getStyleClass().add("page-title");

        Button newButton = button("New Week", this::newWeek);
        Button openButton = button("Open Week", this::openSelectedWeek);
        Button refreshButton = button("Refresh", this::refreshTable);

        HBox actions = new HBox(10, newButton, openButton, refreshButton, statusLabel);
        VBox top = new VBox(10, title, actions);
        top.setPadding(new Insets(15));

        setupTable();
        setTop(top);
        setCenter(table);
        refreshTable();
    }

    private void setupTable() {
        TableColumn<LabourWeekRow, String> weekStartColumn = new TableColumn<>("Week Start");
        weekStartColumn.setCellValueFactory(new PropertyValueFactory<>("weekStart"));
        weekStartColumn.setPrefWidth(160);

        TableColumn<LabourWeekRow, String> weekEndColumn = new TableColumn<>("Week End");
        weekEndColumn.setCellValueFactory(new PropertyValueFactory<>("weekEnd"));
        weekEndColumn.setPrefWidth(160);

        TableColumn<LabourWeekRow, String> statusColumn = new TableColumn<>("Status");
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusColumn.setPrefWidth(160);

        table.getColumns().setAll(weekStartColumn, weekEndColumn, statusColumn);
        table.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                openSelectedWeek();
            }
        });
    }

    private void refreshTable() {
        statusLabel.setText("Loading weeks...");
        Task<List<LocalDate>> task = new Task<>() {
            @Override
            protected List<LocalDate> call() {
                if (DatabaseManager.isApiDatabase()) {
                    return apiClient.findSavedLabourWeeks();
                }
                return labourDailyEntryDao.findSavedWeeks();
            }
        };
        task.setOnSucceeded(event -> {
            List<LabourWeekRow> rows = new ArrayList<>();
            for (LocalDate weekStart : task.getValue()) {
                rows.add(new LabourWeekRow(weekStart));
            }
            table.setItems(FXCollections.observableArrayList(rows));
            statusLabel.setText("Ready");
        });
        task.setOnFailed(event -> {
            statusLabel.setText("Load failed.");
            showAlert(Alert.AlertType.ERROR, "Load Failed", rootCauseMessage(task.getException()));
        });
        run(task, "labour-hours-weeks-load");
    }

    private void newWeek() {
        DatePicker picker = new DatePicker(normalizeToMonday(LocalDate.now()));
        picker.valueProperty().addListener((obs, oldDate, newDate) -> {
            LocalDate normalized = normalizeToMonday(newDate);
            if (newDate != null && !newDate.equals(normalized)) {
                picker.setValue(normalized);
            }
        });

        javafx.scene.control.Dialog<LocalDate> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("New Labour Hours Week");
        javafx.scene.control.ButtonType startButton =
                new javafx.scene.control.ButtonType("Open", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(startButton, javafx.scene.control.ButtonType.CANCEL);

        VBox content = new VBox(10, new Label("Week Starting:"), picker);
        content.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(button -> button == startButton ? normalizeToMonday(picker.getValue()) : null);
        dialog.showAndWait().ifPresent(this::openWeek);
    }

    private void openSelectedWeek() {
        LabourWeekRow selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Week Selected", "Please select a labour week.");
            return;
        }
        openWeek(selected.weekStartDate());
    }

    private void openWeek(LocalDate weekStartDate) {
        WeeklyLabourView editor = new WeeklyLabourView(weekStartDate);
        Stage stage = new Stage();
        stage.setTitle("Labour Hours - " + DATE_FORMAT.format(weekStartDate));
        Scene scene = new Scene(editor, 1250, 780);
        String stylesheet = getClass().getResource("/style.css") == null
                ? null
                : getClass().getResource("/style.css").toExternalForm();
        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet);
        }
        stage.setScene(scene);
        stage.setOnHidden(event -> refreshTable());
        stage.show();
    }

    private Button button(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("primary-button");
        button.setOnAction(event -> action.run());
        return button;
    }

    private LocalDate normalizeToMonday(LocalDate date) {
        LocalDate value = date == null ? LocalDate.now() : date;
        return value.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
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
                ? "The Labour Hours action failed."
                : current.getMessage();
    }

    public static class LabourWeekRow {
        private final LocalDate weekStartDate;

        LabourWeekRow(LocalDate weekStartDate) {
            this.weekStartDate = weekStartDate;
        }

        public LocalDate weekStartDate() {
            return weekStartDate;
        }

        public String getWeekStart() {
            return DATE_FORMAT.format(weekStartDate);
        }

        public String getWeekEnd() {
            return DATE_FORMAT.format(weekStartDate.plusDays(6));
        }

        public String getStatus() {
            return "Saved";
        }
    }
}
