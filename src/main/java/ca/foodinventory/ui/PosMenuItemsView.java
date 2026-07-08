package ca.foodinventory.ui;

import ca.foodinventory.dao.PosMenuItemDao;
import ca.foodinventory.model.ImportedUsageReportSummary;
import ca.foodinventory.model.PosMenuItem;
import ca.foodinventory.model.PosMenuItemImportSummary;
import ca.foodinventory.model.ProductionReportSummary;
import ca.foodinventory.service.PosMenuItemImportService;
import ca.foodinventory.service.ProductionReportService;
import ca.foodinventory.service.ProductionUsageReportImportService;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.Optional;
import java.util.Set;

public class PosMenuItemsView extends ProductionModuleView<PosMenuItem> {

    private final PosMenuItemDao dao = new PosMenuItemDao();
    private final ProductionUsageReportImportService usageReportImportService =
            new ProductionUsageReportImportService();
    private final PosMenuItemImportService posMenuItemImportService =
            new PosMenuItemImportService();
    private final ProductionReportService productionReportService = new ProductionReportService();
    private FilteredList<PosMenuItem> filteredItems;

    public PosMenuItemsView() {
        super(
                "POS Menu Items",
                "Manage POS menu items used by sales mix imports."
        );

        setupTable();
        loadItems();

        searchField.textProperty().addListener((obs, oldValue, newValue) -> applySearch());
    }

    @Override
    protected HBox buildToolbar() {
        Button addButton = createPrimaryButton("Add", this::addItem);
        Button editButton = createPrimaryButton("Edit", this::editSelectedItem);
        Button deactivateButton = createPrimaryButton("Deactivate", this::deactivateSelectedItem);
        Button setupImportButton = createPrimaryButton("Import Menu Items", this::importMenuItems);
        Button deleteKdsButton = createPrimaryButton("Delete KDS Items", this::deleteKdsItems);
        Button importButton = createPrimaryButton("Import Usage Report", this::importUsageReport);

        return new HBox(10, addButton, editButton, deactivateButton, setupImportButton, deleteKdsButton, importButton);
    }

    private void setupTable() {
        TableColumn<PosMenuItem, String> posNumberCol = new TableColumn<>("POS Number");
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
        filteredItems = new FilteredList<>(
                FXCollections.observableArrayList(dao.findAll()),
                item -> true
        );

        table.setItems(filteredItems);
        applySearch();
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
            dao.save(item);
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
            dao.save(updated);
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
                dao.deactivate(selected.getId());
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
        chooser.setTitle("Import POS Menu Items");

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
                    int deletedCount = dao.deleteByPosSkus(kdsPosSkus);
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
}
