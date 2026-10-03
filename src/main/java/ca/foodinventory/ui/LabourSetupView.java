package ca.foodinventory.ui;

import ca.foodinventory.dao.LabourEmployeeDao;
import ca.foodinventory.dao.LabourPositionDao;
import ca.foodinventory.dao.LabourSettingsDao;
import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.LabourEmployee;
import ca.foodinventory.model.LabourPosition;
import ca.foodinventory.model.LabourSettings;
import ca.foodinventory.service.LabourApiClient;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class LabourSetupView extends BorderPane {

    private final LabourPositionDao positionDao = new LabourPositionDao();
    private final LabourEmployeeDao employeeDao = new LabourEmployeeDao();
    private final LabourSettingsDao settingsDao = new LabourSettingsDao();
    private final LabourApiClient apiClient = new LabourApiClient();

    private final TableView<LabourPosition> positionTable = new TableView<>();
    private final TableView<LabourEmployee> employeeTable = new TableView<>();
    private final TextField positionSearchField = new TextField();
    private final TextField employeeSearchField = new TextField();
    private final TextField uniformDeductionField = new TextField();
    private final Label statusLabel = new Label("Ready");

    private FilteredList<LabourPosition> filteredPositions;
    private FilteredList<LabourEmployee> filteredEmployees;

    public LabourSetupView() {
        getStyleClass().add("root-dark");

        Label title = new Label("Labour Setup");
        title.getStyleClass().add("page-title");

        Label subtitle = new Label("Manage labour positions, employees, and labour defaults.");
        subtitle.getStyleClass().add("section-title");

        VBox top = new VBox(15, title, subtitle);
        top.setPadding(new Insets(20));
        setTop(top);

        TabPane tabs = new TabPane();
        tabs.getTabs().setAll(
                new Tab("Positions", buildPositionsTab()),
                new Tab("Employees", buildEmployeesTab()),
                new Tab("Settings", buildSettingsTab())
        );
        tabs.getTabs().forEach(tab -> tab.setClosable(false));
        setCenter(tabs);

        HBox bottom = new HBox(statusLabel);
        bottom.setPadding(new Insets(8, 20, 12, 20));
        bottom.setAlignment(Pos.CENTER_LEFT);
        setBottom(bottom);

        loadAll();
    }

    private VBox buildPositionsTab() {
        setupPositionTable();

        positionSearchField.setPromptText("Search positions...");
        positionSearchField.setPrefWidth(300);
        positionSearchField.textProperty().addListener((obs, oldValue, newValue) -> applyPositionSearch());

        Button addButton = primaryButton("Add", this::addPosition);
        Button editButton = primaryButton("Edit", this::editSelectedPosition);
        Button deactivateButton = primaryButton("Deactivate", this::deactivateSelectedPosition);
        Button refreshButton = primaryButton("Refresh", this::loadAll);

        HBox toolbar = new HBox(
                10,
                positionSearchField,
                addButton,
                editButton,
                deactivateButton,
                refreshButton
        );
        toolbar.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(10, toolbar, positionTable);
        content.setPadding(new Insets(20));
        content.getStyleClass().add("content-area");
        VBox.setVgrow(positionTable, Priority.ALWAYS);
        return content;
    }

    private VBox buildEmployeesTab() {
        setupEmployeeTable();

        employeeSearchField.setPromptText("Search employees...");
        employeeSearchField.setPrefWidth(300);
        employeeSearchField.textProperty().addListener((obs, oldValue, newValue) -> applyEmployeeSearch());

        Button addButton = primaryButton("Add", this::addEmployee);
        Button editButton = primaryButton("Edit", this::editSelectedEmployee);
        Button deactivateButton = primaryButton("Deactivate", this::deactivateSelectedEmployee);
        Button refreshButton = primaryButton("Refresh", this::loadAll);

        HBox toolbar = new HBox(
                10,
                employeeSearchField,
                addButton,
                editButton,
                deactivateButton,
                refreshButton
        );
        toolbar.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(10, toolbar, employeeTable);
        content.setPadding(new Insets(20));
        content.getStyleClass().add("content-area");
        VBox.setVgrow(employeeTable, Priority.ALWAYS);
        return content;
    }

    private VBox buildSettingsTab() {
        Label uniformLabel = new Label("Default Uniform Deduction");
        uniformDeductionField.setPromptText("0.00");
        uniformDeductionField.setPrefWidth(140);

        Button saveButton = primaryButton("Save Settings", this::saveSettings);

        HBox row = new HBox(12, uniformLabel, uniformDeductionField, saveButton);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(18, row);
        content.setPadding(new Insets(20));
        content.getStyleClass().add("content-area");
        return content;
    }

    private void setupPositionTable() {
        TableColumn<LabourPosition, String> nameCol = new TableColumn<>("Position");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<LabourPosition, String> groupCol = new TableColumn<>("Group");
        groupCol.setCellValueFactory(new PropertyValueFactory<>("labourGroup"));

        TableColumn<LabourPosition, Integer> sortCol = new TableColumn<>("Sort Order");
        sortCol.setCellValueFactory(new PropertyValueFactory<>("sortOrder"));

        TableColumn<LabourPosition, BigDecimal> targetCol = new TableColumn<>("Target %");
        targetCol.setCellValueFactory(new PropertyValueFactory<>("targetLabourPercentage"));
        targetCol.setCellFactory(column -> decimalCell(""));

        TableColumn<LabourPosition, Boolean> activeCol = new TableColumn<>("Active");
        activeCol.setCellValueFactory(new PropertyValueFactory<>("active"));

        positionTable.getColumns().setAll(nameCol, groupCol, sortCol, targetCol, activeCol);
        positionTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        positionTable.setRowFactory(tv -> {
            TableRow<LabourPosition> row = new TableRow<>();
            row.itemProperty().addListener((obs, oldItem, newItem) ->
                    row.setStyle(newItem == null || newItem.isActive() ? "" : "-fx-opacity: 0.45;"));
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty()
                        && event.getButton() == MouseButton.PRIMARY
                        && event.getClickCount() == 2) {
                    editPosition(row.getItem());
                }
            });
            return row;
        });
    }

    private void setupEmployeeTable() {
        TableColumn<LabourEmployee, String> nameCol = new TableColumn<>("Employee");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<LabourEmployee, String> positionCol = new TableColumn<>("Position");
        positionCol.setCellValueFactory(new PropertyValueFactory<>("positionName"));

        TableColumn<LabourEmployee, String> groupCol = new TableColumn<>("Group");
        groupCol.setCellValueFactory(new PropertyValueFactory<>("labourGroup"));

        TableColumn<LabourEmployee, BigDecimal> wageCol = new TableColumn<>("Hourly Wage");
        wageCol.setCellValueFactory(new PropertyValueFactory<>("hourlyWage"));
        wageCol.setCellFactory(column -> decimalCell("$"));

        TableColumn<LabourEmployee, LocalDate> effectiveDateCol = new TableColumn<>("Rate Effective");
        effectiveDateCol.setCellValueFactory(new PropertyValueFactory<>("payRateEffectiveDate"));

        TableColumn<LabourEmployee, Boolean> tipCol = new TableColumn<>("Tip Eligible");
        tipCol.setCellValueFactory(new PropertyValueFactory<>("tipPoolEligible"));

        TableColumn<LabourEmployee, Boolean> uniformCol = new TableColumn<>("Uniform Deduction");
        uniformCol.setCellValueFactory(new PropertyValueFactory<>("uniformDeductionApplicable"));

        TableColumn<LabourEmployee, Boolean> activeCol = new TableColumn<>("Active");
        activeCol.setCellValueFactory(new PropertyValueFactory<>("active"));

        employeeTable.getColumns().setAll(
                nameCol,
                positionCol,
                groupCol,
                wageCol,
                effectiveDateCol,
                tipCol,
                uniformCol,
                activeCol
        );
        employeeTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        employeeTable.setRowFactory(tv -> {
            TableRow<LabourEmployee> row = new TableRow<>();
            row.itemProperty().addListener((obs, oldItem, newItem) ->
                    row.setStyle(newItem == null || newItem.isActive() ? "" : "-fx-opacity: 0.45;"));
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty()
                        && event.getButton() == MouseButton.PRIMARY
                        && event.getClickCount() == 2) {
                    editEmployee(row.getItem());
                }
            });
            return row;
        });
    }

    private void loadAll() {
        setBusy("Loading Labour Setup...");
        Task<LabourSetupData> task = new Task<>() {
            @Override
            protected LabourSetupData call() {
                if (DatabaseManager.isApiDatabase()) {
                    return new LabourSetupData(
                            apiClient.findPositions(),
                            apiClient.findEmployees(),
                            apiClient.loadSettings()
                    );
                }
                return new LabourSetupData(
                        positionDao.findAll(),
                        employeeDao.findAll(),
                        settingsDao.load()
                );
            }
        };
        task.setOnSucceeded(event -> {
            LabourSetupData data = task.getValue();
            filteredPositions = new FilteredList<>(
                    FXCollections.observableArrayList(data.positions()),
                    position -> true
            );
            filteredEmployees = new FilteredList<>(
                    FXCollections.observableArrayList(data.employees()),
                    employee -> true
            );
            positionTable.setItems(filteredPositions);
            employeeTable.setItems(filteredEmployees);
            uniformDeductionField.setText(formatDecimal(data.settings().getDefaultUniformDeduction()));
            applyPositionSearch();
            applyEmployeeSearch();
            setReady("Labour Setup loaded.");
        });
        task.setOnFailed(event -> {
            setReady("Load failed.");
            showAlert(Alert.AlertType.ERROR, "Load Failed", rootCauseMessage(task.getException()));
        });
        run(task, "labour-setup-load");
    }

    private void addPosition() {
        LabourPositionDialog dialog = new LabourPositionDialog(null);
        Optional<LabourPosition> result = dialog.showAndWait();
        result.ifPresent(this::savePosition);
    }

    private void editSelectedPosition() {
        LabourPosition selected = positionTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a labour position.");
            return;
        }
        editPosition(selected);
    }

    private void editPosition(LabourPosition position) {
        LabourPositionDialog dialog = new LabourPositionDialog(position);
        Optional<LabourPosition> result = dialog.showAndWait();
        result.ifPresent(this::savePosition);
    }

    private void savePosition(LabourPosition position) {
        setBusy("Saving labour position...");
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                if (DatabaseManager.isApiDatabase()) {
                    apiClient.savePosition(position);
                } else {
                    positionDao.save(position);
                }
                return null;
            }
        };
        task.setOnSucceeded(event -> loadAll());
        task.setOnFailed(event -> {
            setReady("Save failed.");
            showAlert(Alert.AlertType.ERROR, "Save Failed", rootCauseMessage(task.getException()));
        });
        run(task, "labour-position-save");
    }

    private void deactivateSelectedPosition() {
        LabourPosition selected = positionTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a labour position.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Deactivate Labour Position");
        confirm.setHeaderText(null);
        confirm.setContentText("Deactivate " + selected.getName() + "?");
        confirm.showAndWait().ifPresent(button -> {
            if (button == ButtonType.OK) {
                setBusy("Deactivating labour position...");
                Task<Void> task = new Task<>() {
                    @Override
                    protected Void call() {
                        if (DatabaseManager.isApiDatabase()) {
                            apiClient.deactivatePosition(selected.getId());
                        } else {
                            positionDao.deactivate(selected.getId());
                        }
                        return null;
                    }
                };
                task.setOnSucceeded(event -> loadAll());
                task.setOnFailed(event -> {
                    setReady("Deactivate failed.");
                    showAlert(Alert.AlertType.ERROR, "Deactivate Failed", rootCauseMessage(task.getException()));
                });
                run(task, "labour-position-deactivate");
            }
        });
    }

    private void addEmployee() {
        List<LabourPosition> positions = activePositionsForDialog();
        if (positions.isEmpty()) {
            showAlert(
                    Alert.AlertType.WARNING,
                    "No Positions",
                    "Create at least one active labour position before adding employees."
            );
            return;
        }
        LabourEmployeeDialog dialog = new LabourEmployeeDialog(null, positions);
        Optional<LabourEmployee> result = dialog.showAndWait();
        result.ifPresent(this::saveEmployee);
    }

    private void editSelectedEmployee() {
        LabourEmployee selected = employeeTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a labour employee.");
            return;
        }
        editEmployee(selected);
    }

    private void editEmployee(LabourEmployee employee) {
        List<LabourPosition> positions = activePositionsForDialog();
        positions.stream()
                .filter(position -> position.getId() == employee.getPositionId())
                .findFirst()
                .ifPresentOrElse(
                        ignored -> {
                        },
                        () -> currentPositions().stream()
                                .filter(position -> position.getId() == employee.getPositionId())
                                .findFirst()
                                .ifPresent(positions::add)
                );
        LabourEmployeeDialog dialog = new LabourEmployeeDialog(employee, positions);
        Optional<LabourEmployee> result = dialog.showAndWait();
        result.ifPresent(this::saveEmployee);
    }

    private void saveEmployee(LabourEmployee employee) {
        setBusy("Saving labour employee...");
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                if (DatabaseManager.isApiDatabase()) {
                    apiClient.saveEmployee(employee);
                } else {
                    employeeDao.save(employee);
                }
                return null;
            }
        };
        task.setOnSucceeded(event -> loadAll());
        task.setOnFailed(event -> {
            setReady("Save failed.");
            showAlert(Alert.AlertType.ERROR, "Save Failed", rootCauseMessage(task.getException()));
        });
        run(task, "labour-employee-save");
    }

    private void deactivateSelectedEmployee() {
        LabourEmployee selected = employeeTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a labour employee.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Deactivate Labour Employee");
        confirm.setHeaderText(null);
        confirm.setContentText("Deactivate " + selected.getName() + "?");
        confirm.showAndWait().ifPresent(button -> {
            if (button == ButtonType.OK) {
                setBusy("Deactivating labour employee...");
                Task<Void> task = new Task<>() {
                    @Override
                    protected Void call() {
                        if (DatabaseManager.isApiDatabase()) {
                            apiClient.deactivateEmployee(selected.getId());
                        } else {
                            employeeDao.deactivate(selected.getId());
                        }
                        return null;
                    }
                };
                task.setOnSucceeded(event -> loadAll());
                task.setOnFailed(event -> {
                    setReady("Deactivate failed.");
                    showAlert(Alert.AlertType.ERROR, "Deactivate Failed", rootCauseMessage(task.getException()));
                });
                run(task, "labour-employee-deactivate");
            }
        });
    }

    private void saveSettings() {
        BigDecimal deduction;
        try {
            deduction = parseMoney(uniformDeductionField.getText());
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.WARNING, "Invalid Settings", "Uniform deduction must be a number.");
            return;
        }
        if (deduction.compareTo(BigDecimal.ZERO) < 0) {
            showAlert(Alert.AlertType.WARNING, "Invalid Settings", "Uniform deduction cannot be negative.");
            return;
        }

        LabourSettings settings = new LabourSettings(deduction);
        setBusy("Saving labour settings...");
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                if (DatabaseManager.isApiDatabase()) {
                    apiClient.saveSettings(settings);
                } else {
                    settingsDao.save(settings);
                }
                return null;
            }
        };
        task.setOnSucceeded(event -> {
            uniformDeductionField.setText(formatDecimal(settings.getDefaultUniformDeduction()));
            setReady("Labour settings saved.");
        });
        task.setOnFailed(event -> {
            setReady("Save failed.");
            showAlert(Alert.AlertType.ERROR, "Save Failed", rootCauseMessage(task.getException()));
        });
        run(task, "labour-settings-save");
    }

    private List<LabourPosition> activePositionsForDialog() {
        return currentPositions().stream()
                .filter(LabourPosition::isActive)
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    }

    private List<LabourPosition> currentPositions() {
        if (filteredPositions == null) {
            return List.of();
        }
        return List.copyOf(filteredPositions.getSource());
    }

    private void applyPositionSearch() {
        if (filteredPositions == null) {
            return;
        }
        String search = positionSearchField.getText();
        filteredPositions.setPredicate(position -> {
            if (search == null || search.isBlank()) {
                return true;
            }
            return containsIgnoreCase(position.getName(), search)
                    || containsIgnoreCase(position.getLabourGroup(), search)
                    || String.valueOf(position.getSortOrder()).contains(search);
        });
    }

    private void applyEmployeeSearch() {
        if (filteredEmployees == null) {
            return;
        }
        String search = employeeSearchField.getText();
        filteredEmployees.setPredicate(employee -> {
            if (search == null || search.isBlank()) {
                return true;
            }
            return containsIgnoreCase(employee.getName(), search)
                    || containsIgnoreCase(employee.getPositionName(), search)
                    || containsIgnoreCase(employee.getLabourGroup(), search);
        });
    }

    private Button primaryButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("primary-button");
        button.setOnAction(event -> action.run());
        return button;
    }

    private <T> TableCell<T, BigDecimal> decimalCell(String prefix) {
        return new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : prefix + formatDecimal(value));
            }
        };
    }

    private BigDecimal parseMoney(String value) {
        if (value == null || value.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.trim());
    }

    private String formatDecimal(BigDecimal value) {
        BigDecimal decimal = value == null ? BigDecimal.ZERO : value;
        return decimal.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private boolean containsIgnoreCase(String value, String search) {
        return value != null && value.toLowerCase().contains(search.toLowerCase());
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
            return "The Labour Setup action failed.";
        }
        return current.getMessage();
    }

    private record LabourSetupData(
            List<LabourPosition> positions,
            List<LabourEmployee> employees,
            LabourSettings settings
    ) {
    }
}
