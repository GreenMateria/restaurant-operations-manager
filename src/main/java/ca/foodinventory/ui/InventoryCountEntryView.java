package ca.foodinventory.ui;

import ca.foodinventory.dao.AlcoholProductProfileDao;
import ca.foodinventory.dao.InventoryCountDao;
import ca.foodinventory.dao.InventoryCountLineDao;
import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.AlcoholProductProfile;
import ca.foodinventory.model.InventoryCount;
import ca.foodinventory.model.InventoryCountLine;
import ca.foodinventory.service.AlcoholProductProfileApiClient;
import ca.foodinventory.service.InventoryApiClient;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class InventoryCountEntryView {

    private final InventoryCount count;
    private final InventoryCountLineDao lineDao = new InventoryCountLineDao();
    private final InventoryCountDao countDao = new InventoryCountDao();
    private final AlcoholProductProfileDao alcoholProfileDao = new AlcoholProductProfileDao();
    private final InventoryApiClient apiClient = new InventoryApiClient();
    private final AlcoholProductProfileApiClient alcoholProfileApiClient =
            new AlcoholProductProfileApiClient();
    private final String department;

    private final VBox rowsBox = new VBox(0);
    private final List<RowBinding> rowBindings = new ArrayList<>();
    private final List<TextField> navigationFields = new ArrayList<>();
    private Button saveButton;
    private Button completeButton;
    private Button refreshButton;

    public InventoryCountEntryView(InventoryCount count) {
        this(count, null);
    }

    public InventoryCountEntryView(InventoryCount count, String department) {
        this.count = count;
        this.department = department;
    }

    public BorderPane getView() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-dark");

        Label title = new Label("Inventory Count: " + count.getTemplateName());
        title.getStyleClass().add("page-title");

        Label dateLabel = new Label("Date: " + count.getCountDate());

        saveButton = new Button("Save Quantities");
        completeButton = new Button("Complete Count");
        refreshButton = new Button("Refresh");

        saveButton.getStyleClass().add("primary-button");
        completeButton.getStyleClass().add("primary-button");

        HBox buttons = new HBox(10, saveButton, completeButton, refreshButton);
        VBox top = new VBox(10, title, dateLabel, buttons);
        top.setPadding(new Insets(15));

        rowsBox.setFillWidth(true);

        ScrollPane scrollPane = new ScrollPane(rowsBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setPannable(true);

        saveButton.setOnAction(e -> saveQuantities());
        completeButton.setOnAction(e -> completeCount());
        refreshButton.setOnAction(e -> refreshRows());

        root.setTop(top);
        root.setCenter(scrollPane);

        refreshRows();
        return root;
    }

    private void refreshRows() {
        rowsBox.getChildren().clear();
        rowBindings.clear();
        navigationFields.clear();

        rowsBox.getChildren().add(createHeaderRow());

        List<InventoryCountLine> lines = isMigratedDepartmentApiMode()
                ? apiClient.findCountLines(count.getId())
                : lineDao.findByCount(count.getId());
        Map<Integer, AlcoholProductProfile> profilesByProductId = loadAlcoholProfilesIfNeeded();
        String currentSection = null;

        for (InventoryCountLine line : lines) {
            String section = cleanSection(line.getSectionName());

            if (!section.equals(currentSection)) {
                rowsBox.getChildren().add(createSectionRow(section));
                currentSection = section;
            }

            AlcoholProductProfile profile = isAlcoholLayout()
                    ? profilesByProductId.get(line.getProductId())
                    : null;
            RowBinding binding = createDataRow(line, profile);
            rowBindings.add(binding);
            rowsBox.getChildren().add(binding.row());
        }

        if (!navigationFields.isEmpty()) {
            navigationFields.get(0).requestFocus();
            navigationFields.get(0).selectAll();
        }
    }

    private GridPane createHeaderRow() {
        GridPane grid = createBaseGrid();
        grid.setStyle("-fx-background-color: #2f3b46; -fx-border-color: #596775; -fx-border-width: 0 0 1 0;");

        addHeader(grid, "SKU", 0);
        addHeader(grid, "Product", 1);
        addHeader(grid, isAlcoholLayout() ? "Quantity / Total" : "Quantity", 2);

        if (isAlcoholLayout()) {
            addHeader(grid, "Full Units", 3);
            addHeader(grid, "Weight", 4);
            addHeader(grid, "Unit", 5);
        } else {
            addHeader(grid, "Unit", 3);
        }

        return grid;
    }

    private Label createSectionRow(String section) {
        Label label = new Label(section);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setStyle(
                "-fx-font-weight: bold;" +
                "-fx-padding: 7 10 7 10;" +
                "-fx-background-color: #008EAA;" +
                "-fx-text-fill: white;"
        );
        return label;
    }

    private RowBinding createDataRow(
            InventoryCountLine line,
            AlcoholProductProfile profile
    ) {
        GridPane grid = createBaseGrid();
        grid.setPadding(new Insets(4, 6, 4, 6));
        grid.setStyle("-fx-border-color: #4c5964; -fx-border-width: 0 0 1 0;");

        Label skuLabel = new Label(nullToBlank(line.getSku()));
        String productName = line.getDisplayName();
        if (productName == null || productName.isBlank()) {
            productName = line.getProductDescription();
        }

        Label productLabel = new Label(nullToBlank(productName));
        Label unitLabel = new Label(resolveUnit(line, profile));

        skuLabel.setMaxWidth(Double.MAX_VALUE);
        productLabel.setMaxWidth(Double.MAX_VALUE);
        unitLabel.setMaxWidth(Double.MAX_VALUE);

        grid.add(skuLabel, 0, 0);
        grid.add(productLabel, 1, 0);

        boolean weighted = isWeightedAlcohol(profile);

        TextField quantityField = null;
        TextField fullUnitsField = null;
        TextField weightField = null;
        Label totalLabel = new Label(formatQuantity(line.getQuantity()));

        totalLabel.setMaxWidth(Double.MAX_VALUE);
        totalLabel.setAlignment(Pos.CENTER_RIGHT);

        if (weighted) {
            double fullUnits = Math.floor(Math.max(0, line.getQuantity()));
            double partial = Math.max(0, line.getQuantity() - fullUnits);
            double weight = partial > 0
                    ? profile.getTareWeight() + partial * profile.getFullContentWeight()
                    : 0;

            fullUnitsField = createNumericField(formatEditable(fullUnits));
            weightField = createNumericField(formatEditable(weight));

            grid.add(totalLabel, 2, 0);
            grid.add(fullUnitsField, 3, 0);
            grid.add(weightField, 4, 0);
            grid.add(unitLabel, 5, 0);

            navigationFields.add(fullUnitsField);
            navigationFields.add(weightField);
        } else if (isAlcoholLayout()) {
            quantityField = createNumericField(formatEditable(line.getQuantity()));

            grid.add(quantityField, 2, 0);
            grid.add(createDashLabel(), 3, 0);
            grid.add(createDashLabel(), 4, 0);
            grid.add(unitLabel, 5, 0);

            navigationFields.add(quantityField);
        } else {
            quantityField = createNumericField(formatEditable(line.getQuantity()));

            grid.add(quantityField, 2, 0);
            grid.add(unitLabel, 3, 0);

            navigationFields.add(quantityField);
        }

        RowBinding binding = new RowBinding(
                line,
                profile,
                grid,
                quantityField,
                fullUnitsField,
                weightField,
                totalLabel
        );

        if (quantityField != null) {
            configureNavigation(quantityField, () -> commitNormal(binding));
        }

        if (fullUnitsField != null) {
            configureNavigation(fullUnitsField, () -> commitWeighted(binding));
        }

        if (weightField != null) {
            configureNavigation(weightField, () -> commitWeighted(binding));
        }

        return binding;
    }

    private GridPane createBaseGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setAlignment(Pos.CENTER_LEFT);

        ColumnConstraints sku = new ColumnConstraints(95);
        ColumnConstraints product = new ColumnConstraints();
        product.setHgrow(Priority.ALWAYS);
        product.setMinWidth(260);

        ColumnConstraints quantity = new ColumnConstraints(130);
        ColumnConstraints unit = new ColumnConstraints(70);

        if (isAlcoholLayout()) {
            ColumnConstraints full = new ColumnConstraints(105);
            ColumnConstraints weight = new ColumnConstraints(105);
            grid.getColumnConstraints().addAll(sku, product, quantity, full, weight, unit);
        } else {
            grid.getColumnConstraints().addAll(sku, product, quantity, unit);
        }

        return grid;
    }

    private void addHeader(GridPane grid, String text, int column) {
        Label label = new Label(text);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setPadding(new Insets(7, 6, 7, 6));
        label.setStyle("-fx-font-weight: bold; -fx-text-fill: white;");
        grid.add(label, column, 0);
    }

    private TextField createNumericField(String value) {
        TextField field = new TextField(value);
        field.setMaxWidth(Double.MAX_VALUE);
        field.setAlignment(Pos.CENTER_RIGHT);
        return field;
    }

    private Label createDashLabel() {
        Label label = new Label("—");
        label.setMaxWidth(Double.MAX_VALUE);
        label.setAlignment(Pos.CENTER);
        return label;
    }

    private void configureNavigation(TextField field, Runnable commitAction) {
        field.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.TAB) {
                commitAction.run();
                moveToNextField(field, event.isShiftDown());
                event.consume();
            }
        });

        field.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                commitAction.run();
            }
        });
    }

    private void moveToNextField(TextField current, boolean backwards) {
        int index = navigationFields.indexOf(current);

        if (index < 0) {
            return;
        }

        int nextIndex = backwards ? index - 1 : index + 1;

        if (nextIndex < 0 || nextIndex >= navigationFields.size()) {
            return;
        }

        TextField next = navigationFields.get(nextIndex);
        next.requestFocus();
        next.selectAll();
    }

    private void commitNormal(RowBinding binding) {
        try {
            double quantity = parseNonNegative(binding.quantityField().getText(), "Quantity");
            binding.line().setQuantity(quantity);
            binding.line().setConvertedQuantity(quantity * binding.line().getConversionFactor());
            binding.quantityField().setText(formatEditable(quantity));
        } catch (IllegalArgumentException ex) {
            showAlert(Alert.AlertType.WARNING, "Invalid Quantity", ex.getMessage());
            binding.quantityField().setText(formatEditable(binding.line().getQuantity()));
        }
    }

    private void commitWeighted(RowBinding binding) {
        try {
            double fullUnits = parseNonNegative(binding.fullUnitsField().getText(), "Full Units");
            double enteredWeight = parseNonNegative(binding.weightField().getText(), "Weight");

            AlcoholProductProfile profile = binding.profile();

            if (profile.getFullContentWeight() <= 0) {
                throw new IllegalArgumentException(
                        "The product's full-content weight must be greater than zero."
                );
            }

            double partial = 0;

            if (enteredWeight > 0) {
                partial = (enteredWeight - profile.getTareWeight())
                        / profile.getFullContentWeight();
                partial = Math.max(0, Math.min(1, partial));
            }

            double total = fullUnits + partial;

            binding.line().setQuantity(total);
            binding.line().setConvertedQuantity(total * binding.line().getConversionFactor());
            binding.totalLabel().setText(formatQuantity(total));
            binding.fullUnitsField().setText(formatEditable(fullUnits));
            binding.weightField().setText(formatEditable(enteredWeight));

        } catch (IllegalArgumentException ex) {
            showAlert(Alert.AlertType.WARNING, "Invalid Alcohol Count", ex.getMessage());
        }
    }

    private List<InventoryCountLine> collectCommittedLines() {
        List<InventoryCountLine> lines = new ArrayList<>();

        for (RowBinding binding : rowBindings) {
            if (isWeightedAlcohol(binding.profile())) {
                commitWeighted(binding);
            } else {
                commitNormal(binding);
            }

            lines.add(binding.line());
        }

        return lines;
    }

    private void saveQuantities() {
        List<InventoryCountLine> lines = collectCommittedLines();
        runCountSaveTask(
                "Saving inventory quantities...",
                () -> {
                    if (isMigratedDepartmentApiMode()) {
                        apiClient.updateCountLines(count.getId(), lines);
                    } else {
                        lineDao.updateQuantities(lines);
                    }
                },
                () -> showAlert(Alert.AlertType.INFORMATION, "Saved", "Inventory quantities saved.")
        );
    }

    private void completeCount() {
        List<InventoryCountLine> lines = collectCommittedLines();
        runCountSaveTask(
                "Completing inventory count...",
                () -> {
                    if (isMigratedDepartmentApiMode()) {
                        apiClient.completeCount(count.getId(), lines);
                    } else {
                        lineDao.updateQuantities(lines);
                        countDao.markCompleted(count.getId());
                    }
                },
                () -> showAlert(
                        Alert.AlertType.INFORMATION,
                        "Completed",
                        "Inventory count marked as completed."
                )
        );
    }

    private void runCountSaveTask(String statusText, Runnable work, Runnable onSuccess) {
        setSaving(true);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                work.run();
                return null;
            }
        };

        task.setOnSucceeded(event -> {
            setSaving(false);
            onSuccess.run();
        });

        task.setOnFailed(event -> {
            setSaving(false);
            showAlert(Alert.AlertType.ERROR, "Save Failed", task.getException().getMessage());
        });

        rowsBox.setDisable(true);
        saveButton.setText(statusText);

        Thread thread = new Thread(task, "inventory-count-save");
        thread.setDaemon(true);
        thread.start();
    }

    private void setSaving(boolean saving) {
        rowsBox.setDisable(saving);
        saveButton.setDisable(saving);
        completeButton.setDisable(saving);
        refreshButton.setDisable(saving);

        if (!saving) {
            saveButton.setText("Save Quantities");
        }
    }

    private boolean isWeightedAlcohol(AlcoholProductProfile profile) {
        return isAlcoholLayout()
                && profile != null
                && "WEIGHT".equalsIgnoreCase(profile.getCountMethod());
    }

    private boolean isAlcoholLayout() {
        if ("ALCOHOL".equals(department)) {
            return true;
        }

        String templateName = count.getTemplateName() == null ? "" : count.getTemplateName().toUpperCase();
        return templateName.contains("ALCOHOL");
    }

    private Map<Integer, AlcoholProductProfile> loadAlcoholProfilesIfNeeded() {
        if (!isAlcoholLayout()) {
            return Map.of();
        }

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

    private String resolveUnit(
            InventoryCountLine line,
            AlcoholProductProfile profile
    ) {
        if (isWeightedAlcohol(profile)
                && profile.getMeasurementUnit() != null
                && !profile.getMeasurementUnit().isBlank()) {
            return profile.getMeasurementUnit();
        }

        return nullToBlank(line.getCountUnit());
    }

    private double parseNonNegative(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }

        final double parsed;

        try {
            parsed = Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + " must be a valid number.");
        }

        if (parsed < 0) {
            throw new IllegalArgumentException(fieldName + " cannot be negative.");
        }

        return parsed;
    }

    private String cleanSection(String section) {
        return section == null || section.isBlank()
                ? "OTHER"
                : section.trim().toUpperCase();
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private String formatQuantity(double value) {
        return String.format("%.2f", value);
    }

    private String formatEditable(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0000001) {
            return String.valueOf((long) Math.rint(value));
        }

        return String.valueOf(value);
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private record RowBinding(
            InventoryCountLine line,
            AlcoholProductProfile profile,
            GridPane row,
            TextField quantityField,
            TextField fullUnitsField,
            TextField weightField,
            Label totalLabel
    ) {
    }
}
