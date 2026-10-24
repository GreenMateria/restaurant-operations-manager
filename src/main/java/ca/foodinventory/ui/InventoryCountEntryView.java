package ca.foodinventory.ui;

import ca.foodinventory.dao.AlcoholProductProfileDao;
import ca.foodinventory.dao.InventoryCountDao;
import ca.foodinventory.dao.InventoryCountLineDao;
import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.AlcoholProductProfile;
import ca.foodinventory.model.InventoryCount;
import ca.foodinventory.model.InventoryCountLine;
import ca.foodinventory.model.InventoryCountSynchronizationResult;
import ca.foodinventory.service.AlcoholProductProfileApiClient;
import ca.foodinventory.service.InventoryApiClient;
import ca.foodinventory.service.InventoryCountConflictException;
import ca.foodinventory.service.LocalDraftStore;
import javafx.animation.PauseTransition;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

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
    private final Map<Integer, double[]> loadedValues = new LinkedHashMap<>();
    private final LocalDraftStore draftStore = new LocalDraftStore();
    private final String localDraftStoreCode = DatabaseManager.getConfiguredLocationCode();
    private final PauseTransition draftDelay = new PauseTransition(Duration.seconds(1));
    private final Label draftStatus = new Label("");
    private boolean restoringDraft;
    private boolean dirtyDraft;
    private final List<TextField> navigationFields = new ArrayList<>();
    private Button saveButton;
    private Button completeButton;
    private Button refreshButton;
    private Button synchronizeButton;

    public InventoryCountEntryView(InventoryCount count) {
        this(count, null);
    }

    public InventoryCountEntryView(InventoryCount count, String department) {
        this.count = count;
        this.department = department;
        draftDelay.setOnFinished(event -> flushDraft());
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
        synchronizeButton = new Button("Synchronize with Template");
        synchronizeButton.setDisable(count.isCompleted());

        saveButton.getStyleClass().add("primary-button");
        completeButton.getStyleClass().add("primary-button");

        HBox buttons = new HBox(10, saveButton, completeButton, synchronizeButton, refreshButton);
        VBox top = new VBox(10, title, dateLabel, buttons, draftStatus);
        top.setPadding(new Insets(15));

        rowsBox.setFillWidth(true);

        ScrollPane scrollPane = new ScrollPane(rowsBox);
        scrollPane.getStyleClass().add("content-area");
        scrollPane.setFitToWidth(true);
        scrollPane.setPannable(true);

        saveButton.setOnAction(e -> saveQuantities());
        completeButton.setOnAction(e -> completeCount());
        refreshButton.setOnAction(e -> refreshRows());
        synchronizeButton.setOnAction(e -> synchronizeWithTemplate());

        root.setTop(top);
        root.setCenter(scrollPane);

        refreshRows();
        return root;
    }

    private void refreshRows() {
        flushDraft();
        rowsBox.getChildren().clear();
        rowBindings.clear();
        loadedValues.clear();
        dirtyDraft = false;
        navigationFields.clear();

        rowsBox.getChildren().add(createHeaderRow());

        List<InventoryCountLine> lines = isMigratedDepartmentApiMode()
                ? apiClient.findCountLines(count.getId())
                : lineDao.findByCount(count.getId());
        Map<Integer, AlcoholProductProfile> profilesByProductId = loadAlcoholProfilesIfNeeded();
        String currentSection = null;

        for (InventoryCountLine line : lines) {
            loadedValues.put(line.getId(), loadedSnapshot(line));
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
        restoreDraft();
    }

    private GridPane createHeaderRow() {
        GridPane grid = createBaseGrid();
        grid.setStyle("-fx-background-color: #2f3b46; -fx-border-color: #596775; -fx-border-width: 0 0 1 0;");

        addHeader(grid, "SKU", 0);
        addHeader(grid, "Product", 1);
        addHeader(grid, isAlcoholLayout() ? "Quantity / Total" : "SUPPLIES".equals(department) ? "Quantity / Bottom + Lid" : "Quantity", 2);

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
        TextField bottomField = null;
        TextField lidField = null;
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
        } else if (line.isPairedPackaging() && "SUPPLIES".equals(department)) {
            bottomField = createNumericField(line.getBottomSleeves() == null ? "" : formatEditable(line.getBottomSleeves()));
            lidField = createNumericField(line.getLidSleeves() == null ? "" : formatEditable(line.getLidSleeves()));
            bottomField.setPrefWidth(65);
            lidField.setPrefWidth(65);
            totalLabel.setText("Total " + formatQuantity(line.getQuantity()));
            grid.add(new HBox(5, new Label("Bottom"), bottomField, new Label("Lid"), lidField, totalLabel), 2, 0);
            grid.add(unitLabel, 3, 0);
            navigationFields.add(bottomField);
            navigationFields.add(lidField);
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
                bottomField,
                lidField,
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
        if (bottomField != null) {
            configureNavigation(bottomField, () -> previewPaired(binding));
            configureNavigation(lidField, () -> previewPaired(binding));
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

        ColumnConstraints quantity = new ColumnConstraints("SUPPLIES".equals(department) ? 350 : 130);
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
        field.textProperty().addListener((obs, oldText, newText) -> {
            if (!restoringDraft && draftEnabled()) {
                dirtyDraft = true;
                draftDelay.playFromStart();
            }
        });
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

    private void previewPaired(RowBinding binding) {
        try {
            double bottom = parseSleeves(binding.bottomField().getText(), "Bottom sleeves");
            double lids = parseSleeves(binding.lidField().getText(), "Lid sleeves");
            binding.totalLabel().setText("Total " + formatQuantity(bottom + lids));
        } catch (IllegalArgumentException e) {
            binding.totalLabel().setText("Enter both counts");
        }
    }

    private void commitPaired(RowBinding binding) {
        double bottom = parseSleeves(binding.bottomField().getText(), "Bottom sleeves");
        double lids = parseSleeves(binding.lidField().getText(), "Lid sleeves");
        double total = bottom + lids;
        binding.line().setBottomSleeves(bottom);
        binding.line().setLidSleeves(lids);
        binding.line().setQuantity(total);
        binding.line().setConvertedQuantity(total * binding.line().getConversionFactor());
        binding.totalLabel().setText("Total " + formatQuantity(total));
    }

    private double parseSleeves(String raw, String name) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException(name + " must be entered, even when zero.");
        double value = parseNonNegative(raw, name);
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be a finite number.");
        return value;
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

    private List<InventoryCountLine> collectCommittedLines(boolean completing) {
        List<InventoryCountLine> lines = new ArrayList<>();

        for (RowBinding binding : rowBindings) {
            if (binding.bottomField() != null) {
                boolean bottomBlank = binding.bottomField().getText().isBlank();
                boolean lidBlank = binding.lidField().getText().isBlank();
                if (bottomBlank && lidBlank && !completing
                        && binding.line().getBottomSleeves() == null && binding.line().getLidSleeves() == null) {
                    // A draft count may keep an older total until this product is counted again.
                } else {
                    commitPaired(binding);
                }
            } else if (isWeightedAlcohol(binding.profile())) {
                commitWeighted(binding);
            } else {
                commitNormal(binding);
            }

            lines.add(binding.line());
        }

        return lines;
    }

    private void saveQuantities() {
        draftDelay.stop();
        flushDraft();
        List<InventoryCountLine> lines;
        try {
            lines = collectCommittedLines(false);
        } catch (IllegalArgumentException e) {
            showAlert(Alert.AlertType.WARNING, "Check Packaging Counts", e.getMessage());
            return;
        }
        runCountSaveTask(
                "Saving inventory quantities...",
                () -> {
                    if (isMigratedDepartmentApiMode()) {
                        apiClient.updateCountLines(count.getId(), lines, loadedValues);
                    } else {
                        lineDao.updateQuantities(lines);
                    }
                },
                () -> {
                    updateLoadedValues(lines);
                    deleteDraft();
                    showAlert(Alert.AlertType.INFORMATION, "Saved", "Inventory quantities saved.");
                }
        );
    }

    private void synchronizeWithTemplate() {
        if (count.isCompleted()) return;
        setSaving(true);
        synchronizeButton.setText("Synchronizing...");
        Task<SynchronizationData> task = new Task<>() {
            @Override
            protected SynchronizationData call() {
                InventoryCountSynchronizationResult result = isMigratedDepartmentApiMode()
                        ? apiClient.synchronizeCountWithTemplate(count.getId())
                        : countDao.synchronizeWithTemplate(count.getId());
                List<InventoryCountLine> lines = isMigratedDepartmentApiMode()
                        ? apiClient.findCountLines(count.getId()) : lineDao.findByCount(count.getId());
                return new SynchronizationData(result, lines, loadAlcoholProfilesIfNeeded());
            }
        };
        task.setOnSucceeded(event -> {
            mergeSynchronizedRows(task.getValue());
            setSaving(false);
            List<Integer> added = task.getValue().result().addedProductIds();
            String message = added.isEmpty() ? "No missing products were found."
                    : "Added " + added.size() + " product(s) at zero quantity:\n"
                    + task.getValue().lines().stream().filter(line -> added.contains(line.getProductId()))
                    .limit(20).map(line -> nullToBlank(line.getSku()) + " — " + nullToBlank(line.getProductDescription()))
                    .collect(java.util.stream.Collectors.joining("\n"))
                    + (added.size() > 20 ? "\nAnd " + (added.size() - 20) + " more." : "");
            showAlert(Alert.AlertType.INFORMATION, "Template Synchronized", message
                    + "\n\nExisting quantities and unsaved entries were preserved. Use Save Quantities to save your edits.");
        });
        task.setOnFailed(event -> {
            setSaving(false);
            showAlert(Alert.AlertType.ERROR, "Synchronization Failed", task.getException().getMessage());
        });
        Thread thread = new Thread(task, "inventory-count-synchronize");
        thread.setDaemon(true);
        thread.start();
    }

    private void mergeSynchronizedRows(SynchronizationData data) {
        // Reuse existing controls, including pending text and weighted alcohol inputs.
        Map<Integer, RowBinding> existing = new LinkedHashMap<>();
        rowBindings.forEach(binding -> existing.put(binding.line().getId(), binding));
        List<RowBinding> merged = new ArrayList<>();
        for (InventoryCountLine line : data.lines()) {
            RowBinding binding = existing.remove(line.getId());
            if (binding == null) {
                loadedValues.put(line.getId(), loadedSnapshot(line));
                binding = createDataRow(line, isAlcoholLayout() ? data.profiles().get(line.getProductId()) : null);
            } else {
                binding.line().setSectionName(line.getSectionName());
            }
            merged.add(binding);
        }
        merged.addAll(existing.values());
        rowsBox.getChildren().clear();
        rowBindings.clear();
        navigationFields.clear();
        rowsBox.getChildren().add(createHeaderRow());
        String section = null;
        for (RowBinding binding : merged) {
            String nextSection = cleanSection(binding.line().getSectionName());
            if (!nextSection.equals(section)) {
                rowsBox.getChildren().add(createSectionRow(nextSection));
                section = nextSection;
            }
            rowBindings.add(binding);
            rowsBox.getChildren().add(binding.row());
            if (binding.quantityField() != null) navigationFields.add(binding.quantityField());
            if (binding.fullUnitsField() != null) navigationFields.add(binding.fullUnitsField());
            if (binding.weightField() != null) navigationFields.add(binding.weightField());
            if (binding.bottomField() != null) navigationFields.add(binding.bottomField());
            if (binding.lidField() != null) navigationFields.add(binding.lidField());
        }
    }

    private void completeCount() {
        draftDelay.stop();
        flushDraft();
        List<InventoryCountLine> lines;
        try {
            lines = collectCommittedLines(true);
        } catch (IllegalArgumentException e) {
            showAlert(Alert.AlertType.WARNING, "Check Packaging Counts", e.getMessage());
            return;
        }
        runCountSaveTask(
                "Completing inventory count...",
                () -> {
                    if (isMigratedDepartmentApiMode()) {
                        apiClient.completeCount(count.getId(), lines, loadedValues);
                    } else {
                        lineDao.updateQuantities(lines);
                        countDao.markCompleted(count.getId());
                    }
                },
                () -> {
                    count.setCompleted(true);
                    updateLoadedValues(lines);
                    deleteDraft();
                    synchronizeButton.setDisable(true);
                    showAlert(
                        Alert.AlertType.INFORMATION,
                        "Completed",
                        "Inventory count marked as completed."
                    );
                }
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
            boolean conflict = task.getException() instanceof InventoryCountConflictException;
            showAlert(conflict ? Alert.AlertType.WARNING : Alert.AlertType.ERROR,
                    conflict ? "Count Changed" : "Save Failed", task.getException().getMessage());
        });

        rowsBox.setDisable(true);
        saveButton.setText(statusText);

        Thread thread = new Thread(task, "inventory-count-save");
        thread.setDaemon(true);
        thread.start();
    }

    private void updateLoadedValues(List<InventoryCountLine> lines) {
        for (InventoryCountLine line : lines) {
            loadedValues.put(line.getId(), loadedSnapshot(line));
        }
    }

    private double[] loadedSnapshot(InventoryCountLine line) {
        return new double[]{line.getQuantity(), line.getConvertedQuantity(),
                line.getBottomSleeves() == null ? Double.NaN : line.getBottomSleeves(),
                line.getLidSleeves() == null ? Double.NaN : line.getLidSleeves()};
    }

    public void flushDraft() {
        if (!draftEnabled() || !dirtyDraft || rowBindings.isEmpty() || restoringDraft) return;
        Properties values = new Properties();
        for (RowBinding binding : rowBindings) {
            String prefix = "line." + binding.line().getId() + ".";
            if (binding.quantityField() != null) values.setProperty(prefix + "quantity", binding.quantityField().getText());
            if (binding.fullUnitsField() != null) values.setProperty(prefix + "full", binding.fullUnitsField().getText());
            if (binding.weightField() != null) values.setProperty(prefix + "weight", binding.weightField().getText());
            if (binding.bottomField() != null) values.setProperty(prefix + "bottom", binding.bottomField().getText());
            if (binding.lidField() != null) values.setProperty(prefix + "lid", binding.lidField().getText());
            double[] loaded = loadedValues.get(binding.line().getId());
            if (loaded != null) {
                values.setProperty(prefix + "expectedQuantity", Double.toString(loaded[0]));
                values.setProperty(prefix + "expectedConvertedQuantity", Double.toString(loaded[1]));
                if (loaded.length > 2) values.setProperty(prefix + "expectedBottomSleeves", Double.toString(loaded[2]));
                if (loaded.length > 3) values.setProperty(prefix + "expectedLidSleeves", Double.toString(loaded[3]));
            }
        }
        try {
            draftStore.save(draftStoreCode(), "count", Integer.toString(count.getId()), values);
            draftStatus.setText("Unsaved draft kept on this PC.");
        } catch (RuntimeException e) {
            draftStatus.setText("Local draft could not be saved: " + e.getMessage());
        }
    }

    private void restoreDraft() {
        if (!draftEnabled()) return;
        Properties values;
        try {
            values = draftStore.load(draftStoreCode(), "count", Integer.toString(count.getId()));
        } catch (RuntimeException e) {
            draftStatus.setText(e.getMessage());
            return;
        }
        if (values == null) return;
        ButtonType restore = new ButtonType("Restore Draft", ButtonBar.ButtonData.OK_DONE);
        ButtonType discard = new ButtonType("Discard Draft", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert prompt = new Alert(Alert.AlertType.CONFIRMATION,
                "Unsaved quantities from this PC were found for this count. Restore them for review?",
                restore, discard);
        prompt.setTitle("Unsaved Inventory Draft");
        if (prompt.showAndWait().orElse(discard) != restore) {
            deleteDraft();
            return;
        }
        restoringDraft = true;
        try {
            for (RowBinding binding : rowBindings) {
                String prefix = "line." + binding.line().getId() + ".";
                if (binding.quantityField() != null && values.containsKey(prefix + "quantity"))
                    binding.quantityField().setText(values.getProperty(prefix + "quantity"));
                if (binding.fullUnitsField() != null && values.containsKey(prefix + "full"))
                    binding.fullUnitsField().setText(values.getProperty(prefix + "full"));
                if (binding.weightField() != null && values.containsKey(prefix + "weight"))
                    binding.weightField().setText(values.getProperty(prefix + "weight"));
                if (binding.bottomField() != null && values.containsKey(prefix + "bottom"))
                    binding.bottomField().setText(values.getProperty(prefix + "bottom"));
                if (binding.lidField() != null && values.containsKey(prefix + "lid"))
                    binding.lidField().setText(values.getProperty(prefix + "lid"));
                if (values.containsKey(prefix + "expectedQuantity")
                        && values.containsKey(prefix + "expectedConvertedQuantity")) {
                    double[] existing = loadedValues.get(binding.line().getId());
                    loadedValues.put(binding.line().getId(), new double[]{
                            Double.parseDouble(values.getProperty(prefix + "expectedQuantity")),
                            Double.parseDouble(values.getProperty(prefix + "expectedConvertedQuantity")),
                            values.containsKey(prefix + "expectedBottomSleeves")
                                    ? Double.parseDouble(values.getProperty(prefix + "expectedBottomSleeves")) : existing[2],
                            values.containsKey(prefix + "expectedLidSleeves")
                                    ? Double.parseDouble(values.getProperty(prefix + "expectedLidSleeves")) : existing[3]});
                }
            }
            draftStatus.setText("Local draft restored. Review before saving; changed cloud data will block the save.");
            dirtyDraft = true;
        } catch (NumberFormatException e) {
            draftStatus.setText("Draft values could not be read. The cloud count was left loaded.");
        } finally {
            restoringDraft = false;
        }
    }

    private void deleteDraft() {
        if (!isMigratedDepartmentApiMode() || draftStoreCode().isBlank()) return;
        draftDelay.stop();
        dirtyDraft = false;
        try {
            draftStore.delete(draftStoreCode(), "count", Integer.toString(count.getId()));
            draftStatus.setText("");
        } catch (RuntimeException e) {
            draftStatus.setText(e.getMessage());
        }
    }

    private boolean draftEnabled() {
        return isMigratedDepartmentApiMode() && !draftStoreCode().isBlank() && !count.isCompleted();
    }

    private String draftStoreCode() {
        return localDraftStoreCode;
    }

    private void setSaving(boolean saving) {
        rowsBox.setDisable(saving);
        saveButton.setDisable(saving);
        completeButton.setDisable(saving);
        refreshButton.setDisable(saving);
        synchronizeButton.setDisable(saving || count.isCompleted());
        if (!saving) synchronizeButton.setText("Synchronize with Template");

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
            TextField bottomField,
            TextField lidField,
            Label totalLabel
    ) {
    }

    private record SynchronizationData(InventoryCountSynchronizationResult result,
                                       List<InventoryCountLine> lines,
                                       Map<Integer, AlcoholProductProfile> profiles) {
    }
}
