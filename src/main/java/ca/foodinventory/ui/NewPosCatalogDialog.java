package ca.foodinventory.ui;

import ca.foodinventory.dao.NewPosMenuItemDao;
import ca.foodinventory.dao.ProductionProfileDao;
import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.NewPosMenuItem;
import ca.foodinventory.model.ProductionProfile;
import ca.foodinventory.service.NewPosCatalogDiscoveryService;
import ca.foodinventory.service.ProductionApiClient;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.File;
import java.util.List;

public class NewPosCatalogDialog extends Dialog<Void> {
    private final NewPosMenuItemDao dao = new NewPosMenuItemDao();
    private final ProductionApiClient api = new ProductionApiClient();
    private final NewPosCatalogDiscoveryService discovery = new NewPosCatalogDiscoveryService();
    private final TableView<NewPosMenuItem> table = new TableView<>();

    public NewPosCatalogDialog() {
        setTitle("New POS Catalog");
        setHeaderText("New POS names and production profiles");
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        getDialogPane().setPrefSize(900, 650);

        TableColumn<NewPosMenuItem, String> name = new TableColumn<>("New POS Item Name");
        name.setCellValueFactory(value -> new javafx.beans.property.SimpleStringProperty(value.getValue().name()));
        name.setPrefWidth(440);
        TableColumn<NewPosMenuItem, String> profile = new TableColumn<>("Production Profile");
        profile.setCellValueFactory(value -> new javafx.beans.property.SimpleStringProperty(
                value.getValue().productionProfileName() == null ? "" : value.getValue().productionProfileName()));
        profile.setPrefWidth(320);
        table.getColumns().addAll(name, profile);
        table.setPlaceholder(new Label("Import a new POS report or add an item name."));

        Button importButton = new Button("Discover from Usage Report");
        importButton.setOnAction(event -> discover());
        Button addButton = new Button("Add Name");
        addButton.setOnAction(event -> addName());
        Button mapButton = new Button("Assign Profile");
        mapButton.setOnAction(event -> assignProfile());
        VBox content = new VBox(12, new Label("These names are separate from legacy PLUs. Review candidates before adding them."),
                new HBox(10, importButton, addButton, mapButton), table);
        content.setPadding(new Insets(12));
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);
        getDialogPane().setContent(content);
        refresh();
    }

    private void refresh() {
        try {
            List<NewPosMenuItem> items = DatabaseManager.isApiDatabase() ? api.findNewPosMenuItems() : dao.findAll();
            table.setItems(FXCollections.observableArrayList(items));
        } catch (RuntimeException e) {
            error(e);
        }
    }

    private void addName() {
        TextInputDialog input = new TextInputDialog();
        input.setTitle("Add New POS Item");
        input.setHeaderText("Enter the exact name shown in the new POS");
        input.showAndWait().ifPresent(name -> {
            if (name.isBlank()) return;
            addNames(List.of(name));
        });
    }

    private void discover() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select New POS Weekday Usage Report");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        File file = chooser.showOpenDialog(getDialogPane().getScene().getWindow());
        if (file == null) return;
        try {
            List<String> candidates = discovery.discoverCandidates(file);
            if (candidates.isEmpty()) {
                new Alert(Alert.AlertType.INFORMATION, "No candidate item names were found.").showAndWait();
                return;
            }
            ListView<String> list = new ListView<>(FXCollections.observableArrayList(candidates));
            list.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            list.setPrefHeight(480);
            Button selectAll = new Button("Select All");
            selectAll.setOnAction(event -> list.getSelectionModel().selectAll());
            Button clear = new Button("Clear Selection");
            clear.setOnAction(event -> list.getSelectionModel().clearSelection());
            Dialog<ButtonType> review = new Dialog<>();
            review.setTitle("Review New POS Names");
            review.setHeaderText(candidates.size() + " possible item names. Review and select names to add.");
            review.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            review.getDialogPane().setContent(new VBox(8,
                    new Label("This report also contains subtotals and repeated sections. Check the names before saving."),
                    new HBox(8, selectAll, clear), list));
            if (review.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                addNames(List.copyOf(list.getSelectionModel().getSelectedItems()));
            }
        } catch (RuntimeException e) {
            error(e);
        }
    }

    private void addNames(List<String> names) {
        if (names.isEmpty()) return;
        try {
            int inserted = DatabaseManager.isApiDatabase() ? api.addNewPosNames(names) : dao.addDiscoveredNames(names);
            refresh();
            new Alert(Alert.AlertType.INFORMATION, "Added " + inserted + " new POS names. Existing mappings were kept.").showAndWait();
        } catch (RuntimeException e) {
            error(e);
        }
    }

    private void assignProfile() {
        NewPosMenuItem item = table.getSelectionModel().getSelectedItem();
        if (item == null) {
            new Alert(Alert.AlertType.WARNING, "Select a new POS item first.").showAndWait();
            return;
        }
        try {
            List<ProductionProfile> profiles = DatabaseManager.isApiDatabase()
                    ? api.findProfiles() : new ProductionProfileDao().findAll();
            ComboBox<ProductionProfile> choice = new ComboBox<>();
            choice.setPrefWidth(350);
            SearchableComboBoxSupport.makeSearchable(choice, profiles, new StringConverter<>() {
                @Override public String toString(ProductionProfile profile) { return profile == null ? "" : profile.getName(); }
                @Override public ProductionProfile fromString(String value) { return null; }
            });
            profiles.stream().filter(profile -> profile.getId() == item.productionProfileId())
                    .findFirst().ifPresent(choice::setValue);
            Button clear = new Button("Clear Profile");
            clear.setOnAction(event -> choice.setValue(null));
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Assign Production Profile");
            dialog.setHeaderText(item.name());
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            dialog.getDialogPane().setContent(new HBox(8, choice, clear));
            if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            int profileId = choice.getValue() == null ? 0 : choice.getValue().getId();
            if (DatabaseManager.isApiDatabase()) api.assignNewPosProfile(item.id(), profileId);
            else dao.assignProfile(item.id(), profileId);
            refresh();
        } catch (RuntimeException e) {
            error(e);
        }
    }

    private void error(RuntimeException error) {
        new Alert(Alert.AlertType.ERROR, error.getMessage()).showAndWait();
    }
}
