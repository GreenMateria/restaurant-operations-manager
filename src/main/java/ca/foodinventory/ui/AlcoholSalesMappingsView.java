package ca.foodinventory.ui;

import ca.foodinventory.dao.AlcoholSalesMappingDao;
import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.AlcoholSalesMapping;
import ca.foodinventory.service.AlcoholSalesMappingApiClient;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;

import java.util.Optional;

public class AlcoholSalesMappingsView extends ProductionModuleView<AlcoholSalesMapping> {

    private final AlcoholSalesMappingDao dao = new AlcoholSalesMappingDao();
    private final AlcoholSalesMappingApiClient apiClient = new AlcoholSalesMappingApiClient();
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
        if (DatabaseManager.isApiDatabase()) {
            loadMappingsFromApi();
            return;
        }

        filteredMappings = new FilteredList<>(
                FXCollections.observableArrayList(dao.findAll()),
                mapping -> true
        );

        table.setItems(filteredMappings);
        applySearch();
    }

    private void loadMappingsFromApi() {
        table.setPlaceholder(new Label("Loading alcohol sales mappings..."));

        Task<java.util.List<AlcoholSalesMapping>> task = new Task<>() {
            @Override
            protected java.util.List<AlcoholSalesMapping> call() {
                return apiClient.findAll();
            }
        };

        task.setOnSucceeded(event -> {
            filteredMappings = new FilteredList<>(
                    FXCollections.observableArrayList(task.getValue()),
                    mapping -> true
            );
            table.setItems(filteredMappings);
            applySearch();
        });

        task.setOnFailed(event -> {
            Throwable exception = task.getException();
            if (exception != null) {
                exception.printStackTrace();
            }

            filteredMappings = new FilteredList<>(
                    FXCollections.observableArrayList(),
                    mapping -> true
            );
            table.setItems(filteredMappings);
            showAlert(
                    Alert.AlertType.ERROR,
                    "Alcohol Sales Mappings API Failed",
                    "Alcohol sales mappings could not be loaded from the API."
                            + formatFailureDetails(exception)
            );
        });

        Thread thread = new Thread(task, "alcohol-sales-mappings-api-load");
        thread.setDaemon(true);
        thread.start();
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
            saveMapping(mapping);
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
            saveMapping(updated);
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
                deactivateMapping(selected.getId());
                loadMappings();
            }
        });
    }

    private void saveMapping(AlcoholSalesMapping mapping) {
        if (DatabaseManager.isApiDatabase()) {
            apiClient.save(mapping);
            return;
        }

        dao.save(mapping);
    }

    private void deactivateMapping(int id) {
        if (DatabaseManager.isApiDatabase()) {
            apiClient.deactivate(id);
            return;
        }

        dao.deactivate(id);
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
