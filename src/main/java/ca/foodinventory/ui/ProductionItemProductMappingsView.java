package ca.foodinventory.ui;

import ca.foodinventory.dao.ProductionItemProductMappingDao;
import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.ProductionItemProductMapping;
import ca.foodinventory.service.ProductionApiClient;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;

import java.util.Optional;

public class ProductionItemProductMappingsView extends ProductionModuleView<ProductionItemProductMapping> {

    private final ProductionItemProductMappingDao dao = new ProductionItemProductMappingDao();
    private final ProductionApiClient apiClient = new ProductionApiClient();
    private FilteredList<ProductionItemProductMapping> filteredMappings;

    public ProductionItemProductMappingsView() {
        super(
                "Product Mappings",
                "Connect production items to the inventory products they consume."
        );

        setupTable();
        installToolbar();
        loadMappings();

        searchField.textProperty().addListener((obs, oldValue, newValue) -> applySearch());
    }

    @Override
    protected HBox buildToolbar() {
        Button addButton = createPrimaryButton("Add", this::addMapping);
        Button editButton = createPrimaryButton("Edit", this::editSelectedMapping);
        Button deactivateButton = createPrimaryButton("Deactivate", this::deactivateSelectedMapping);

        return new HBox(10, addButton, editButton, deactivateButton);
    }

    private void setupTable() {
        TableColumn<ProductionItemProductMapping, String> productionItemCol =
                new TableColumn<>("Production Item");
        productionItemCol.setCellValueFactory(new PropertyValueFactory<>("productionItemName"));

        TableColumn<ProductionItemProductMapping, String> skuCol = new TableColumn<>("SKU");
        skuCol.setCellValueFactory(new PropertyValueFactory<>("productSku"));

        TableColumn<ProductionItemProductMapping, String> productCol =
                new TableColumn<>("Inventory Product");
        productCol.setCellValueFactory(new PropertyValueFactory<>("productDescription"));

        TableColumn<ProductionItemProductMapping, Double> quantityCol =
                new TableColumn<>("Qty Per Unit");
        quantityCol.setCellValueFactory(new PropertyValueFactory<>("quantityPerUnit"));

        TableColumn<ProductionItemProductMapping, String> unitCol = new TableColumn<>("Unit");
        unitCol.setCellValueFactory(new PropertyValueFactory<>("unit"));

        TableColumn<ProductionItemProductMapping, Boolean> activeCol = new TableColumn<>("Active");
        activeCol.setCellValueFactory(new PropertyValueFactory<>("active"));

        table.getColumns().setAll(
                productionItemCol,
                skuCol,
                productCol,
                quantityCol,
                unitCol,
                activeCol
        );

        table.setRowFactory(tv -> {
            TableRow<ProductionItemProductMapping> row = new TableRow<>();

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
                    editMapping(row.getItem());
                }
            });

            return row;
        });
    }

    private void loadMappings() {
        filteredMappings = new FilteredList<>(
                FXCollections.observableArrayList(DatabaseManager.isApiDatabase()
                        ? apiClient.findProductMappings()
                        : dao.findAll()),
                mapping -> true
        );

        table.setItems(filteredMappings);
        applySearch();
    }

    private void applySearch() {
        if (filteredMappings == null) {
            return;
        }

        String search = searchField.getText();

        filteredMappings.setPredicate(mapping -> {
            if (search == null || search.isBlank()) {
                return true;
            }

            return containsIgnoreCase(mapping.getProductionItemName(), search)
                    || containsIgnoreCase(mapping.getProductSku(), search)
                    || containsIgnoreCase(mapping.getProductDescription(), search)
                    || containsIgnoreCase(mapping.getUnit(), search);
        });
    }

    private void addMapping() {
        ProductionItemProductMappingDialog dialog = new ProductionItemProductMappingDialog(null);
        Optional<ProductionItemProductMapping> result = dialog.showAndWait();

        result.ifPresent(mapping -> {
            saveMapping(mapping);
            loadMappings();
        });
    }

    private void editSelectedMapping() {
        ProductionItemProductMapping selected = table.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a product mapping.");
            return;
        }

        editMapping(selected);
    }

    private void editMapping(ProductionItemProductMapping mapping) {
        ProductionItemProductMappingDialog dialog = new ProductionItemProductMappingDialog(mapping);
        Optional<ProductionItemProductMapping> result = dialog.showAndWait();

        result.ifPresent(updated -> {
            saveMapping(updated);
            loadMappings();
        });
    }

    private void deactivateSelectedMapping() {
        ProductionItemProductMapping selected = table.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a product mapping.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Deactivate Product Mapping");
        confirm.setHeaderText(null);
        confirm.setContentText("Deactivate mapping for " + selected.getProductionItemName() + "?");

        confirm.showAndWait().ifPresent(button -> {
            if (button == ButtonType.OK) {
                if (DatabaseManager.isApiDatabase()) {
                    apiClient.deactivateProductMapping(selected.getId());
                } else {
                    dao.deactivate(selected.getId());
                }
                loadMappings();
            }
        });
    }

    private void saveMapping(ProductionItemProductMapping mapping) {
        if (DatabaseManager.isApiDatabase()) {
            apiClient.saveProductMapping(mapping);
        } else {
            dao.save(mapping);
        }
    }
}
