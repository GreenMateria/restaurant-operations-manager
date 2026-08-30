package ca.foodinventory.ui;

import ca.foodinventory.dao.AlcoholSalesMappingDao;
import ca.foodinventory.model.AlcoholSalesMapping;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;

import java.util.Optional;

public class AlcoholSalesMappingsView extends ProductionModuleView<AlcoholSalesMapping> {

    private final AlcoholSalesMappingDao dao = new AlcoholSalesMappingDao();
    private FilteredList<AlcoholSalesMapping> filteredMappings;

    public AlcoholSalesMappingsView() {
        super(
                "Alcohol Sales Mappings",
                "Connect POS alcohol items to inventory products for variance reporting."
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
        TableColumn<AlcoholSalesMapping, String> posSkuCol = new TableColumn<>("POS SKU / PLU");
        posSkuCol.setCellValueFactory(new PropertyValueFactory<>("posSku"));

        TableColumn<AlcoholSalesMapping, String> posNameCol = new TableColumn<>("POS Item");
        posNameCol.setCellValueFactory(new PropertyValueFactory<>("posItemName"));
        posNameCol.setPrefWidth(220);

        TableColumn<AlcoholSalesMapping, String> categoryCol = new TableColumn<>("Category");
        categoryCol.setCellValueFactory(new PropertyValueFactory<>("reportingCategory"));

        TableColumn<AlcoholSalesMapping, String> productCol = new TableColumn<>("Inventory Product");
        productCol.setCellValueFactory(new PropertyValueFactory<>("productDescription"));
        productCol.setPrefWidth(260);

        TableColumn<AlcoholSalesMapping, String> skuCol = new TableColumn<>("Product SKU");
        skuCol.setCellValueFactory(new PropertyValueFactory<>("productSku"));

        TableColumn<AlcoholSalesMapping, Double> quantityCol = new TableColumn<>("Qty Per Sale");
        quantityCol.setCellValueFactory(new PropertyValueFactory<>("quantityPerSale"));

        TableColumn<AlcoholSalesMapping, String> unitCol = new TableColumn<>("Unit");
        unitCol.setCellValueFactory(new PropertyValueFactory<>("unit"));

        TableColumn<AlcoholSalesMapping, Boolean> activeCol = new TableColumn<>("Active");
        activeCol.setCellValueFactory(new PropertyValueFactory<>("active"));

        table.getColumns().setAll(
                posSkuCol,
                posNameCol,
                categoryCol,
                productCol,
                skuCol,
                quantityCol,
                unitCol,
                activeCol
        );

        table.setRowFactory(tv -> {
            TableRow<AlcoholSalesMapping> row = new TableRow<>();

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
                FXCollections.observableArrayList(dao.findAll()),
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

            return containsIgnoreCase(mapping.getPosSku(), search)
                    || containsIgnoreCase(mapping.getPosItemName(), search)
                    || containsIgnoreCase(mapping.getReportingCategory(), search)
                    || containsIgnoreCase(mapping.getProductSku(), search)
                    || containsIgnoreCase(mapping.getProductDescription(), search)
                    || containsIgnoreCase(mapping.getUnit(), search);
        });
    }

    private void addMapping() {
        AlcoholSalesMappingDialog dialog = new AlcoholSalesMappingDialog(null);
        Optional<AlcoholSalesMapping> result = dialog.showAndWait();

        result.ifPresent(mapping -> {
            dao.save(mapping);
            loadMappings();
        });
    }

    private void editSelectedMapping() {
        AlcoholSalesMapping selected = table.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select an alcohol sales mapping.");
            return;
        }

        editMapping(selected);
    }

    private void editMapping(AlcoholSalesMapping mapping) {
        AlcoholSalesMappingDialog dialog = new AlcoholSalesMappingDialog(mapping);
        Optional<AlcoholSalesMapping> result = dialog.showAndWait();

        result.ifPresent(updated -> {
            dao.save(updated);
            loadMappings();
        });
    }

    private void deactivateSelectedMapping() {
        AlcoholSalesMapping selected = table.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select an alcohol sales mapping.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Deactivate Alcohol Sales Mapping");
        confirm.setHeaderText(null);
        confirm.setContentText("Deactivate mapping for " + selected.getPosSku() + "?");

        confirm.showAndWait().ifPresent(button -> {
            if (button == ButtonType.OK) {
                dao.deactivate(selected.getId());
                loadMappings();
            }
        });
    }
}
