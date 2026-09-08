package ca.foodinventory.ui;

import ca.foodinventory.dao.InventoryCountDao;
import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.AlcoholVarianceRow;
import ca.foodinventory.model.InventoryCount;
import ca.foodinventory.service.AlcoholVarianceService;
import ca.foodinventory.service.InventoryApiClient;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.File;
import java.util.List;

public class AlcoholVarianceReportView {

    private final InventoryCountDao countDao = new InventoryCountDao();
    private final InventoryApiClient inventoryApiClient = new InventoryApiClient();
    private final AlcoholVarianceService varianceService = new AlcoholVarianceService();
    private final ComboBox<InventoryCount> openingCountBox = new ComboBox<>();
    private final ComboBox<InventoryCount> closingCountBox = new ComboBox<>();
    private final ComboBox<String> categoryBox = new ComboBox<>();
    private final TextField usageFileField = new TextField();
    private final Label statusLabel = new Label("");
    private final TableView<AlcoholVarianceRow> table = new TableView<>();

    private File selectedUsageFile;

    public BorderPane getView() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-dark");
        root.setPadding(new Insets(15));

        Label title = new Label("Alcohol Variance Report");
        title.getStyleClass().add("page-title");

        setupControls();
        setupTable();
        loadCounts();

        Button chooseFileButton = new Button("Choose POS Usage Report");
        chooseFileButton.getStyleClass().add("primary-button");
        chooseFileButton.setOnAction(e -> chooseUsageFile());

        Button generateButton = new Button("Generate");
        generateButton.getStyleClass().add("primary-button");
        generateButton.setOnAction(e -> generateReport());

        HBox filters = new HBox(
                10,
                new Label("Opening Count:"),
                openingCountBox,
                new Label("Closing Count:"),
                closingCountBox,
                new Label("Category:"),
                categoryBox
        );

        HBox fileRow = new HBox(10, usageFileField, chooseFileButton, generateButton);

        statusLabel.getStyleClass().add("muted-label");

        VBox top = new VBox(12, title, filters, fileRow, statusLabel);
        root.setTop(top);
        root.setCenter(table);

        return root;
    }

    private void setupControls() {
        StringConverter<InventoryCount> countConverter = new StringConverter<>() {
            @Override
            public String toString(InventoryCount count) {
                if (count == null) {
                    return "";
                }

                return count.getTemplateName() + " - " + count.getCountDate();
            }

            @Override
            public InventoryCount fromString(String string) {
                return null;
            }
        };

        openingCountBox.setConverter(countConverter);
        closingCountBox.setConverter(countConverter);
        openingCountBox.setPrefWidth(250);
        closingCountBox.setPrefWidth(250);

        categoryBox.getItems().setAll(
                "All Alcohol",
                "BEER",
                "WINE",
                "DRAUGHT",
                "IMPORT DRAUGHT",
                "LIQUOR"
        );
        categoryBox.setValue("All Alcohol");

        usageFileField.setEditable(false);
        usageFileField.setPromptText("POS usage report .xlsx");
        usageFileField.setPrefWidth(360);
    }

    private void setupTable() {
        TableColumn<AlcoholVarianceRow, String> productCol = new TableColumn<>("Product");
        productCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("product"));
        productCol.setPrefWidth(260);

        TableColumn<AlcoholVarianceRow, String> categoryCol = new TableColumn<>("Category");
        categoryCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("category"));
        categoryCol.setPrefWidth(140);

        TableColumn<AlcoholVarianceRow, String> unitCol = new TableColumn<>("Unit");
        unitCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("unit"));
        unitCol.setPrefWidth(80);

        TableColumn<AlcoholVarianceRow, Double> actualCol = new TableColumn<>("Actual Usage");
        actualCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("actualUsage"));
        actualCol.setPrefWidth(125);

        TableColumn<AlcoholVarianceRow, Double> soldCol = new TableColumn<>("Sold Usage");
        soldCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("soldUsage"));
        soldCol.setPrefWidth(125);

        TableColumn<AlcoholVarianceRow, Double> varianceCol = new TableColumn<>("Variance");
        varianceCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("variance"));
        varianceCol.setCellFactory(column -> varianceCell(false));
        varianceCol.setPrefWidth(110);

        TableColumn<AlcoholVarianceRow, Double> varianceDollarCol = new TableColumn<>("Variance $");
        varianceDollarCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("varianceDollars"));
        varianceDollarCol.setCellFactory(column -> varianceCell(true));
        varianceDollarCol.setPrefWidth(110);

        table.getColumns().setAll(
                productCol,
                categoryCol,
                unitCol,
                actualCol,
                soldCol,
                varianceCol,
                varianceDollarCol
        );
    }

    private TableCell<AlcoholVarianceRow, Double> varianceCell(boolean money) {
        return new TableCell<>() {
            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);

                if (empty || value == null) {
                    setText(null);
                    setStyle("");
                    return;
                }

                setText(money ? String.format("$%.2f", value) : String.format("%.2f", value));

                if (value > 0) {
                    setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
                } else if (value < 0) {
                    setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold;");
                } else {
                    setStyle("");
                }
            }
        };
    }

    private void loadCounts() {
        List<InventoryCount> alcoholCounts = DatabaseManager.isApiDatabase()
                ? inventoryApiClient.findCompletedCounts("ALCOHOL")
                : countDao.findAll().stream()
                .filter(this::isAlcoholCount)
                .filter(InventoryCount::isCompleted)
                .toList();

        openingCountBox.setItems(FXCollections.observableArrayList(alcoholCounts));
        closingCountBox.setItems(FXCollections.observableArrayList(alcoholCounts));
    }

    private boolean isAlcoholCount(InventoryCount count) {
        String templateName = count.getTemplateName() == null ? "" : count.getTemplateName().toUpperCase();
        return templateName.contains("ALCOHOL");
    }

    private void chooseUsageFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choose POS Usage Report");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx")
        );

        File file = fileChooser.showOpenDialog(table.getScene().getWindow());
        if (file != null) {
            selectedUsageFile = file;
            usageFileField.setText(file.getAbsolutePath());
        }
    }

    private void generateReport() {
        if (openingCountBox.getValue() == null || closingCountBox.getValue() == null) {
            showAlert(Alert.AlertType.WARNING, "Missing Counts", "Choose opening and closing alcohol counts.");
            return;
        }

        if (openingCountBox.getValue().getId() == closingCountBox.getValue().getId()) {
            showAlert(Alert.AlertType.WARNING, "Invalid Counts", "Choose different opening and closing counts.");
            return;
        }

        if (selectedUsageFile == null) {
            showAlert(Alert.AlertType.WARNING, "Missing POS Usage Report", "Choose the POS usage report for this period.");
            return;
        }

        try {
            AlcoholVarianceService.AlcoholVarianceResult result = varianceService.generate(
                    openingCountBox.getValue().getId(),
                    closingCountBox.getValue().getId(),
                    categoryBox.getValue(),
                    selectedUsageFile
            );

            table.setItems(FXCollections.observableArrayList(result.rows()));
            statusLabel.setText(
                    "Imported "
                            + result.importedUsageRows()
                            + " POS rows. Applied "
                            + result.activeMappingCount()
                            + " active sales mappings. Showing "
                            + result.rows().size()
                            + " variance rows."
            );
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Variance Report Failed", e.getMessage());
        }
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
