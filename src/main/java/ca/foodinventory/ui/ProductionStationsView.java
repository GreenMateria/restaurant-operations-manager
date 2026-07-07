package ca.foodinventory.ui;

import ca.foodinventory.dao.ProductionStationDao;
import ca.foodinventory.model.ProductionStation;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;

import java.util.Optional;

public class ProductionStationsView extends ProductionModuleView<ProductionStation> {

    private final ProductionStationDao dao = new ProductionStationDao();
    private FilteredList<ProductionStation> filteredStations;

    public ProductionStationsView() {
        super(
                "Production Stations",
                "Manage kitchen stations and print order for production sheets."
        );

        setupTable();
        loadStations();

        searchField.textProperty().addListener((obs, oldValue, newValue) -> applySearch());
    }

    @Override
    protected HBox buildToolbar() {
        Button addButton = createPrimaryButton("Add", this::addStation);
        Button editButton = createPrimaryButton("Edit", this::editSelectedStation);
        Button deactivateButton = createPrimaryButton("Deactivate", this::deactivateSelectedStation);

        return new HBox(10, addButton, editButton, deactivateButton);
    }

    private void setupTable() {
        TableColumn<ProductionStation, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<ProductionStation, Integer> sortOrderCol = new TableColumn<>("Sort Order");
        sortOrderCol.setCellValueFactory(new PropertyValueFactory<>("sortOrder"));

        TableColumn<ProductionStation, Boolean> activeCol = new TableColumn<>("Active");
        activeCol.setCellValueFactory(new PropertyValueFactory<>("active"));

        table.getColumns().setAll(
                nameCol,
                sortOrderCol,
                activeCol
        );

        table.setRowFactory(tv -> {
            TableRow<ProductionStation> row = new TableRow<>();

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
                    editStation(row.getItem());
                }
            });

            return row;
        });
    }

    private void loadStations() {
        filteredStations = new FilteredList<>(
                FXCollections.observableArrayList(dao.findAll()),
                station -> true
        );

        table.setItems(filteredStations);
        applySearch();
    }

    private void applySearch() {
        if (filteredStations == null) {
            return;
        }

        String search = searchField.getText();

        filteredStations.setPredicate(station -> {
            if (search == null || search.isBlank()) {
                return true;
            }

            return containsIgnoreCase(station.getName(), search)
                    || String.valueOf(station.getSortOrder()).contains(search);
        });
    }

    private void addStation() {
        ProductionStationDialog dialog = new ProductionStationDialog(null);
        Optional<ProductionStation> result = dialog.showAndWait();

        result.ifPresent(station -> {
            dao.save(station);
            loadStations();
        });
    }

    private void editSelectedStation() {
        ProductionStation selected = table.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a production station.");
            return;
        }

        editStation(selected);
    }

    private void editStation(ProductionStation station) {
        ProductionStationDialog dialog = new ProductionStationDialog(station);
        Optional<ProductionStation> result = dialog.showAndWait();

        result.ifPresent(updated -> {
            dao.save(updated);
            loadStations();
        });
    }

    private void deactivateSelectedStation() {
        ProductionStation selected = table.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a production station.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Deactivate Production Station");
        confirm.setHeaderText(null);
        confirm.setContentText("Deactivate " + selected.getName() + "?");

        confirm.showAndWait().ifPresent(button -> {
            if (button == ButtonType.OK) {
                dao.deactivate(selected.getId());
                loadStations();
            }
        });
    }
}