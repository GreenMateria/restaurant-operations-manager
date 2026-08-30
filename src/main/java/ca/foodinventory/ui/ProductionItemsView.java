package ca.foodinventory.ui;

import ca.foodinventory.dao.ProductionItemDao;
import ca.foodinventory.model.ProductionItem;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;

import java.util.Optional;

public class ProductionItemsView extends ProductionModuleView<ProductionItem> {

    private final ProductionItemDao dao = new ProductionItemDao();
    private FilteredList<ProductionItem> filteredItems;

    public ProductionItemsView() {
        super(
                "Production Items",
                "Manage prep items used for weekly production sheets."
        );

        setupTable();
        installToolbar();
        loadItems();

        searchField.textProperty().addListener((obs, oldValue, newValue) -> applySearch());
    }

    @Override
    protected HBox buildToolbar() {
        Button addButton = createPrimaryButton("Add", this::addItem);
        Button editButton = createPrimaryButton("Edit", this::editSelectedItem);
        Button deactivateButton = createPrimaryButton("Deactivate", this::deactivateSelectedItem);
        Button importButton = createPrimaryButton("Import", () ->
                showAlert(Alert.AlertType.INFORMATION, "Import", "Production item import will be added later.")
        );
        Button printButton = createPrimaryButton("Print", () ->
                showAlert(Alert.AlertType.INFORMATION, "Print", "Production item printing will be added later.")
        );

        return new HBox(10, addButton, editButton, deactivateButton, importButton, printButton);
    }

    private void setupTable() {
        TableColumn<ProductionItem, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<ProductionItem, String> stationCol = new TableColumn<>("Station");
        stationCol.setCellValueFactory(new PropertyValueFactory<>("stationName"));

        TableColumn<ProductionItem, String> unitCol = new TableColumn<>("Unit");
        unitCol.setCellValueFactory(new PropertyValueFactory<>("unit"));

        TableColumn<ProductionItem, String> shelfLifeCol = new TableColumn<>("Shelf Life");
        shelfLifeCol.setCellValueFactory(new PropertyValueFactory<>("shelfLife"));

        TableColumn<ProductionItem, Double> yieldFactorCol = new TableColumn<>("Yield Factor");
        yieldFactorCol.setCellValueFactory(new PropertyValueFactory<>("yieldFactor"));

        TableColumn<ProductionItem, Integer> printOrderCol = new TableColumn<>("Print Order");
        printOrderCol.setCellValueFactory(new PropertyValueFactory<>("printOrder"));

        TableColumn<ProductionItem, Integer> permanentOverrideParCol =
                new TableColumn<>("Permanent Override Par");
        permanentOverrideParCol.setCellValueFactory(new PropertyValueFactory<>("permanentOverridePar"));

        TableColumn<ProductionItem, Boolean> activeCol = new TableColumn<>("Active");
        activeCol.setCellValueFactory(new PropertyValueFactory<>("active"));

        table.getColumns().setAll(
                nameCol,
                stationCol,
                unitCol,
                shelfLifeCol,
                yieldFactorCol,
                printOrderCol,
                permanentOverrideParCol,
                activeCol
        );

        table.setRowFactory(tv -> {
            TableRow<ProductionItem> row = new TableRow<>();

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

            return containsIgnoreCase(item.getName(), search)
                    || containsIgnoreCase(item.getStationName(), search)
                    || containsIgnoreCase(item.getUnit(), search)
                    || containsIgnoreCase(item.getShelfLife(), search)
                    || String.valueOf(item.getYieldFactor()).contains(search)
                    || String.valueOf(item.getPrintOrder()).contains(search)
                    || String.valueOf(item.getPermanentOverridePar()).contains(search);
        });
    }

    private void addItem() {
        ProductionItemDialog dialog = new ProductionItemDialog(null);
        Optional<ProductionItem> result = dialog.showAndWait();

        result.ifPresent(item -> {
            try {
                dao.save(item);
                loadItems();
            } catch (RuntimeException e) {
                showAlert(Alert.AlertType.ERROR, "Save Failed", "Could not save production item: " + e.getMessage());
            }
        });
    }

    private void editSelectedItem() {
        ProductionItem selected = table.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a production item.");
            return;
        }

        editItem(selected);
    }

    private void editItem(ProductionItem item) {
        ProductionItemDialog dialog = new ProductionItemDialog(item);
        Optional<ProductionItem> result = dialog.showAndWait();

        result.ifPresent(updated -> {
            try {
                dao.save(updated);
                loadItems();
            } catch (RuntimeException e) {
                showAlert(Alert.AlertType.ERROR, "Save Failed", "Could not save production item: " + e.getMessage());
            }
        });
    }

    private void deactivateSelectedItem() {
        ProductionItem selected = table.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a production item.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Deactivate Production Item");
        confirm.setHeaderText(null);
        confirm.setContentText("Deactivate " + selected.getName() + "?");

        confirm.showAndWait().ifPresent(button -> {
            if (button == ButtonType.OK) {
                dao.deactivate(selected.getId());
                loadItems();
            }
        });
    }
}
