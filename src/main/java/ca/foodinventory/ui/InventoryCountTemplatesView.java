package ca.foodinventory.ui;

import ca.foodinventory.dao.InventoryCountTemplateDao;
import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.InventoryCountTemplate;
import ca.foodinventory.service.InventoryApiClient;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class InventoryCountTemplatesView {

    private final InventoryCountTemplateDao dao = new InventoryCountTemplateDao();
    private final InventoryApiClient apiClient = new InventoryApiClient();
    private final TableView<InventoryCountTemplate> table = new TableView<>();

    private final String department;

    public InventoryCountTemplatesView() {
        this.department = null;
    }

    public InventoryCountTemplatesView(String department) {
        this.department = department;
    }

    public BorderPane getView() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-dark");

        Label title = new Label(getTitleText());
        title.getStyleClass().add("page-title");

        Button addButton = new Button("Add Template");
        addButton.getStyleClass().add("primary-button");
        addButton.setOnAction(e -> addTemplate());

        Button deactivateButton = new Button("Deactivate");
        deactivateButton.getStyleClass().add("primary-button");
        deactivateButton.setOnAction(e -> deactivateSelectedTemplate());

        Button editButton = new Button("Edit Template");
        editButton.getStyleClass().add("primary-button");
        editButton.setOnAction(e -> openTemplateEditor());

        Button duplicateButton = new Button("Duplicate Template");
        duplicateButton.getStyleClass().add("primary-button");
        duplicateButton.setOnAction(e -> duplicateSelectedTemplate());

        HBox topBar = new HBox(15, title, addButton, deactivateButton, editButton, duplicateButton);
        topBar.getStyleClass().add("top-bar");

        setupTable();
        loadTemplates();

        root.setTop(topBar);
        root.setCenter(table);

        return root;
    }

    private void setupTable() {
        TableColumn<InventoryCountTemplate, String> nameCol = new TableColumn<>("Template Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(350);

        TableColumn<InventoryCountTemplate, String> activeCol = new TableColumn<>("Active");
        activeCol.setCellValueFactory(new PropertyValueFactory<>("activeText"));

        table.getColumns().setAll(nameCol, activeCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    private void loadTemplates() {
        if (isMigratedDepartmentApiMode()) {
            table.setItems(FXCollections.observableArrayList(
                    apiClient.findActiveTemplates(department)
            ));
            return;
        }

        var templates = dao.findAllActive();

        if (department != null) {
            templates.removeIf(template -> {
                String name = template.getName() == null
                        ? ""
                        : template.getName().toUpperCase();

                return switch (department) {
                    case "FOOD" -> !name.contains("FOOD");
                    case "ALCOHOL" -> !name.contains("ALCOHOL");
                    case "SUPPLIES" -> !name.contains("SUPPLIES");
                    default -> false;
                };
            });
        }

        table.setItems(FXCollections.observableArrayList(templates));
    }

    private void addTemplate() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Count Template");
        dialog.setHeaderText("Create a new inventory count template.");
        dialog.setContentText("Template Name:");

        dialog.showAndWait().ifPresent(name -> {
            String cleanName = name.trim();

            if (cleanName.isEmpty()) {
                return;
            }

            if (isMigratedDepartmentApiMode()) {
                apiClient.addTemplate(department, cleanName);
            } else {
                dao.add(cleanName);
            }
            loadTemplates();
        });
    }

    private void deactivateSelectedTemplate() {
        InventoryCountTemplate selected = table.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showAlert(
                    Alert.AlertType.WARNING,
                    "No Template Selected",
                    "Please select a template first."
            );
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Deactivate Count Template");
        confirm.setHeaderText(null);
        confirm.setContentText(
                "Deactivate template \"" + selected.getName() + "\"?\n\n"
                        + "This template will no longer appear for new inventory counts."
        );

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        if (isMigratedDepartmentApiMode()) {
            apiClient.deactivateTemplate(selected.getId());
        } else {
            dao.deactivate(selected.getId());
        }
        loadTemplates();
    }

    private void openTemplateEditor() {
        InventoryCountTemplate selected = table.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showAlert(
                    Alert.AlertType.WARNING,
                    "No Template Selected",
                    "Please select a template."
            );
            return;
        }

        InventoryCountTemplateEditorView editor =
                new InventoryCountTemplateEditorView(selected);

        Stage stage = new Stage();
        stage.setTitle("Template Editor - " + selected.getName());

        Scene scene = new Scene(
                editor.getView(),
                1000,
                700
        );

        stage.setScene(scene);
        stage.show();
    }

    private void duplicateSelectedTemplate() {
        InventoryCountTemplate selected = table.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showAlert(
                    Alert.AlertType.WARNING,
                    "No Template Selected",
                    "Please select a template to duplicate."
            );
            return;
        }

        TextInputDialog dialog =
                new TextInputDialog(selected.getName() + " Copy");

        dialog.setTitle("Duplicate Template");
        dialog.setHeaderText("Duplicate inventory count template.");
        dialog.setContentText("New Template Name:");

        dialog.showAndWait().ifPresent(name -> {
            String cleanName = name.trim();

            if (cleanName.isEmpty()) {
                return;
            }

            if (isMigratedDepartmentApiMode()) {
                apiClient.duplicateTemplate(selected.getId(), cleanName);
            } else {
                dao.duplicateTemplate(
                        selected.getId(),
                        cleanName
                );
            }

            loadTemplates();

            showAlert(
                    Alert.AlertType.INFORMATION,
                    "Template Duplicated",
                    "Template duplicated successfully."
            );
        });
    }

    private String getTitleText() {
        if (department == null) {
            return "Inventory Count Templates";
        }

        return switch (department) {
            case "FOOD" -> "Food Count Templates";
            case "ALCOHOL" -> "Alcohol Count Templates";
            case "SUPPLIES" -> "Supplies Count Templates";
            default -> "Inventory Count Templates";
        };
    }

    private boolean isMigratedDepartmentApiMode() {
        return DatabaseManager.isApiDatabase()
                && ("FOOD".equals(department)
                || "ALCOHOL".equals(department)
                || "SUPPLIES".equals(department));
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
