package ca.foodinventory.ui;

import ca.foodinventory.dao.PosMenuItemDao;
import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.ImportedUsageReportSummary;
import ca.foodinventory.model.PosMenuItem;
import ca.foodinventory.model.PosMenuItemImportSummary;
import ca.foodinventory.model.ProductionReportSummary;
import ca.foodinventory.service.PosMenuItemImportService;
import ca.foodinventory.service.ProductionApiClient;
import ca.foodinventory.service.ProductionReportService;
import ca.foodinventory.service.ProductionUsageReportImportService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.Optional;
import java.util.Set;

public class PosMenuItemsView extends ProductionModuleView<PosMenuItem> {

    private final boolean productionMode;
    private final PosMenuItemDao dao = new PosMenuItemDao();
    private final ProductionApiClient apiClient = new ProductionApiClient();
    private final ProductionUsageReportImportService usageReportImportService =
            new ProductionUsageReportImportService();
    private final PosMenuItemImportService posMenuItemImportService =
            new PosMenuItemImportService();
    private final ProductionReportService productionReportService = new ProductionReportService();
    private FilteredList<PosMenuItem> filteredItems;
    private Button addButton;
    private Button editButton;
    private Button deactivateButton;
    private Button setupImportButton;
    private Button deleteKdsButton;
    private Button importButton;

    public PosMenuItemsView() {
        this(false);
    }

    public PosMenuItemsView(boolean productionMode) {
        super(
                productionMode ? "POS Production Mappings" : "POS Catalog / PLUs",
                productionMode
                        ? "Assign POS items to production profiles. Import shared items from Administration > POS Catalog / PLUs."
                        : "Shared POS items for Alcohol Sales Mappings and Production."
        );
        this.productionMode = productionMode;

        setupTable();
        installToolbar();
        loadItems();

        searchField.textProperty().addListener((obs, oldValue, newValue) -> applySearch());
    }

    @Override
    protected HBox buildToolbar() {
        addButton = createPrimaryButton("Add", this::addItem);
        editButton = createPrimaryButton("Edit", this::editSelectedItem);
        deactivateButton = createPrimaryButton("Deactivate", this::deactivateSelectedItem);
        setupImportButton = createPrimaryButton("Import POS Catalog", this::importMenuItems);
        deleteKdsButton = createPrimaryButton("Delete KDS Items", this::deleteKdsItems);
        importButton = createPrimaryButton("Import Usage Report", this::importUsageReport);

        return productionMode
                ? new HBox(10, editButton, importButton)
                : new HBox(10, addButton, editButton, deactivateButton, setupImportButton, deleteKdsButton);
    }

    private void setupTable() {
        TableColumn<PosMenuItem, String> posNumberCol = new TableColumn<>("POS PLU");
        posNumberCol.setCellValueFactory(new PropertyValueFactory<>("posSku"));

        TableColumn<PosMenuItem, String> nameCol = new TableColumn<>("Menu Item Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<PosMenuItem, String> categoryCol = new TableColumn<>("Category");
        categoryCol.setCellValueFactory(new PropertyValueFactory<>("category"));

        TableColumn<PosMenuItem, String> productionProfileCol = new TableColumn<>("Production Profile");
        productionProfileCol.setCellValueFactory(new PropertyValueFactory<>("productionProfileName"));

        TableColumn<PosMenuItem, Boolean> activeCol = new TableColumn<>("Active");
        activeCol.setCellValueFactory(new PropertyValueFactory<>("active"));

        table.getColumns().setAll(
                posNumberCol,
                nameCol,
                categoryCol,
                productionProfileCol,
                activeCol
        );

        table.setRowFactory(tv -> {
            TableRow<PosMenuItem> row = new TableRow<>();

            row.itemProperty().addListener((obs, oldItem, newItem) -> {
                if (newItem == null || newItem.isActive()) {
                    row.setStyle("");
                } else {
                    row.setStyle("-fx-opacity: 0.45;");
                }
            });

            row.setOnMouseClicked(event -> {
                if (!row.isEmpty()
                        && event.getButton() == MouseButton.PRIMARY
                        && event.getClickCount() == 2) {
                    editItem(row.getItem());
                }
            });

            return row;
        });
    }

    private void loadItems() {
        if (DatabaseManager.isApiDatabase()) {
            loadItemsFromApi();
            return;
        }

        filteredItems = new FilteredList<>(
                FXCollections.observableArrayList(dao.findAll()),
                item -> true
        );

        table.setItems(filteredItems);
        applySearch();
    }

    private void loadItemsFromApi() {
        table.setPlaceholder(new Label("Loading POS menu items..."));

        Task<java.util.List<PosMenuItem>> task = new Task<>() {
            @Override
            protected java.util.List<PosMenuItem> call() {
                return apiClient.findPosMenuItems();
            }
        };

        task.setOnSucceeded(event -> {
            filteredItems = new FilteredList<>(
                    FXCollections.observableArrayList(task.getValue()),
                    item -> true
            );

            table.setItems(filteredItems);
            applySearch();
        });

        task.setOnFailed(event -> {
            Throwable exception = task.getException();
            if (exception != null) {
                exception.printStackTrace();
            }

            filteredItems = new FilteredList<>(
                    FXCollections.observableArrayList(),
                    item -> true
            );
            table.setItems(filteredItems);
            showAlert(
                    Alert.AlertType.ERROR,
                    "POS Menu Items API Failed",
                    "POS menu items could not be loaded from the API."
                            + formatFailureDetails(exception)
            );
        });

        Thread thread = new Thread(task, "pos-menu-items-api-load");
        thread.setDaemon(true);
        thread.start();
    }

    private void applySearch() {
        if (filteredItems == null) {
            return;
        }

        String search = searchField.getText();

        filteredItems.setPredicate(item -> {
            if (search == null || search.isBlank()) {
                return true;
            }

            return containsIgnoreCase(item.getPosSku(), search)
                    || containsIgnoreCase(item.getName(), search)
                    || containsIgnoreCase(item.getCategory(), search)
                    || containsIgnoreCase(item.getProductionProfileName(), search);
        });
    }

    private void addItem() {
        PosMenuItemDialog dialog = new PosMenuItemDialog(null);
        Optional<PosMenuItem> result = dialog.showAndWait();

        result.ifPresent(item -> {
            saveItem(item);
            loadItems();
        });
    }

    private void editSelectedItem() {
        PosMenuItem selected = table.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a POS menu item.");
            return;
        }

        editItem(selected);
    }

    private void editItem(PosMenuItem item) {
        PosMenuItemDialog dialog = new PosMenuItemDialog(item);
        Optional<PosMenuItem> result = dialog.showAndWait();

        result.ifPresent(updated -> {
            saveItem(updated);
            loadItems();
        });
    }

    private void deactivateSelectedItem() {
        PosMenuItem selected = table.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a POS menu item.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Deactivate POS Menu Item");
        confirm.setHeaderText(null);
        confirm.setContentText("Deactivate " + selected.getName() + "?");

        confirm.showAndWait().ifPresent(button -> {
            if (button == ButtonType.OK) {
                if (DatabaseManager.isApiDatabase()) {
                    apiClient.deactivatePosMenuItem(selected.getId());
                } else {
                    dao.deactivate(selected.getId());
                }
                loadItems();
            }
        });
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

            showAlert(
                    Alert.AlertType.INFORMATION,
                    "Usage Report Imported",
                    "Imported " + usageSummary.getTotalRows()
                            + " configured POS item rows from " + usageSummary.getSheetName() + ".\n"
                            + "Weekly quantity sold: " + formatNumber(usageSummary.getWeeklyQuantitySold())
                            + "\nProduction items generated: " + productionSummary.getLineCount()
            );

            new ProductionReportDialog(productionSummary).showAndWait();

        } catch (RuntimeException e) {
            showAlert(
                    Alert.AlertType.ERROR,
                    "Import Failed",
                    e.getMessage()
            );
        }
    }

    private void importMenuItems() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import POS Catalog — Name in A, PLU in B");

        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx")
        );

        File file = chooser.showOpenDialog(null);

        if (file == null) {
            return;
        }

        try {
            PosMenuItemImportSummary summary = posMenuItemImportService.importMenuItems(file);
            loadItems();

            showAlert(
                    Alert.AlertType.INFORMATION,
                    "POS Menu Items Imported",
                    "Read " + summary.getRowsRead() + " menu item rows from " + summary.getSheetName() + ".\n"
                            + "Inserted: " + summary.getInsertedCount() + "\n"
                            + "Updated: " + summary.getUpdatedCount() + "\n"
                            + "Skipped: " + summary.getSkippedCount()
            );

        } catch (RuntimeException e) {
            showAlert(
                    Alert.AlertType.ERROR,
                    "Import Failed",
                    e.getMessage()
            );
        }
    }

    private void deleteKdsItems() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Salesmix With KDS Section");

        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx")
        );

        File file = chooser.showOpenDialog(null);

        if (file == null) {
            return;
        }

        try {
            Set<String> kdsPosSkus = posMenuItemImportService.findKdsSectionPosSkus(file);

            if (kdsPosSkus.isEmpty()) {
                showAlert(
                        Alert.AlertType.INFORMATION,
                        "No KDS Items Found",
                        "No PLU/SKU values were found between the KDS marker rows."
                );
                return;
            }

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Delete KDS POS Menu Items");
            confirm.setHeaderText(null);
            confirm.setContentText(
                    "Found " + kdsPosSkus.size()
                            + " PLU/SKU values in the KDS section.\n\n"
                            + "Delete matching POS menu items from this app?"
            );

            confirm.showAndWait().ifPresent(button -> {
                if (button == ButtonType.OK) {
                    int deletedCount = DatabaseManager.isApiDatabase()
                            ? apiClient.deletePosMenuItemsBySkus(kdsPosSkus.stream().toList())
                            : dao.deleteByPosSkus(kdsPosSkus);
                    loadItems();

                    showAlert(
                            Alert.AlertType.INFORMATION,
                            "KDS Items Deleted",
                            "Deleted " + deletedCount + " matching POS menu items."
                    );
                }
            });

        } catch (RuntimeException e) {
            showAlert(
                    Alert.AlertType.ERROR,
                    "Delete Failed",
                    e.getMessage()
            );
        }
    }

    private String formatNumber(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }

        return String.valueOf(value);
    }

    private void saveItem(PosMenuItem item) {
        if (DatabaseManager.isApiDatabase()) {
            apiClient.savePosMenuItem(item);
        } else {
            dao.save(item);
        }
    }

    private String formatFailureDetails(Throwable exception) {
        if (exception == null) {
            return "";
        }

        Throwable rootCause = exception;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }

        String message = rootCause.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getMessage();
        }

        return message == null || message.isBlank()
                ? ""
                : "\n\nDetails: " + message;
    }

    @Override
    protected void showAlert(Alert.AlertType type, String title, String message) {
        Runnable show = () -> super.showAlert(type, title, message);

        if (Platform.isFxApplicationThread()) {
            show.run();
        } else {
            Platform.runLater(show);
        }
    }
}
