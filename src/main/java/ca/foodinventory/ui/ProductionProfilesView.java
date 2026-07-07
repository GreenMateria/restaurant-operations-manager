package ca.foodinventory.ui;

import ca.foodinventory.dao.ProductionProfileDao;
import ca.foodinventory.dao.ProductionProfileLineDao;
import ca.foodinventory.model.ProductionProfile;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;

import java.util.Optional;

public class ProductionProfilesView extends ProductionModuleView<ProductionProfile> {

    private final ProductionProfileDao profileDao = new ProductionProfileDao();
    private final ProductionProfileLineDao lineDao = new ProductionProfileLineDao();
    private FilteredList<ProductionProfile> filteredProfiles;

    public ProductionProfilesView() {
        super(
                "Production Profiles",
                "Connect sold POS menu items to the prep items they consume."
        );

        setupTable();
        loadProfiles();

        searchField.textProperty().addListener((obs, oldValue, newValue) -> applySearch());
    }

    @Override
    protected HBox buildToolbar() {
        Button addButton = createPrimaryButton("Add", this::addProfile);
        Button editButton = createPrimaryButton("Edit", this::editSelectedProfile);
        Button deactivateButton = createPrimaryButton("Deactivate", this::deactivateSelectedProfile);

        return new HBox(10, addButton, editButton, deactivateButton);
    }

    private void setupTable() {
        TableColumn<ProductionProfile, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<ProductionProfile, String> categoryCol = new TableColumn<>("Category");
        categoryCol.setCellValueFactory(new PropertyValueFactory<>("category"));

        TableColumn<ProductionProfile, Boolean> activeCol = new TableColumn<>("Active");
        activeCol.setCellValueFactory(new PropertyValueFactory<>("active"));

        table.getColumns().setAll(
                nameCol,
                categoryCol,
                activeCol
        );

        table.setRowFactory(tv -> {
            TableRow<ProductionProfile> row = new TableRow<>();

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
                    editProfile(row.getItem());
                }
            });

            return row;
        });
    }

    private void loadProfiles() {
        filteredProfiles = new FilteredList<>(
                FXCollections.observableArrayList(profileDao.findAll()),
                profile -> true
        );

        table.setItems(filteredProfiles);
        applySearch();
    }

    private void applySearch() {
        if (filteredProfiles == null) {
            return;
        }

        String search = searchField.getText();

        filteredProfiles.setPredicate(profile -> {
            if (search == null || search.isBlank()) {
                return true;
            }

            return containsIgnoreCase(profile.getName(), search)
                    || containsIgnoreCase(profile.getCategory(), search);
        });
    }

    private void addProfile() {
        ProductionProfileDialog dialog = new ProductionProfileDialog(null);
        Optional<ProductionProfileDialog.Result> result = dialog.showAndWait();

        result.ifPresent(this::saveProfile);
    }

    private void editSelectedProfile() {
        ProductionProfile selected = table.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a production profile.");
            return;
        }

        editProfile(selected);
    }

    private void editProfile(ProductionProfile profile) {
        ProductionProfileDialog dialog = new ProductionProfileDialog(profile);
        Optional<ProductionProfileDialog.Result> result = dialog.showAndWait();

        result.ifPresent(this::saveProfile);
    }

    private void saveProfile(ProductionProfileDialog.Result result) {
        int profileId = profileDao.save(result.profile());
        lineDao.replaceForProfile(profileId, result.lines());
        loadProfiles();
    }

    private void deactivateSelectedProfile() {
        ProductionProfile selected = table.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a production profile.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Deactivate Production Profile");
        confirm.setHeaderText(null);
        confirm.setContentText("Deactivate " + selected.getName() + "?");

        confirm.showAndWait().ifPresent(button -> {
            if (button == ButtonType.OK) {
                profileDao.deactivate(selected.getId());
                loadProfiles();
            }
        });
    }
}
