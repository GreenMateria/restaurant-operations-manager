package ca.foodinventory.ui;

import ca.foodinventory.dao.InventoryCountDao;
import ca.foodinventory.model.InventoryCount;
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
    private final ComboBox<InventoryCount> openingCountBox = new ComboBox<>();
    private final ComboBox<InventoryCount> closingCountBox = new ComboBox<>();
    private final ComboBox<String> categoryBox = new ComboBox<>();
    private final TextField usageFileField = new TextField();
    private final TableView<VariancePreviewRow> table = new TableView<>();

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
        generateButton.setOnAction(e -> showGenerateNotice());

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

        VBox top = new VBox(12, title, filters, fileRow);
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
        TableColumn<VariancePreviewRow, String> productCol = new TableColumn<>("Product");
        productCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("product"));
        productCol.setPrefWidth(260);

        TableColumn<VariancePreviewRow, Double> actualCol = new TableColumn<>("Actual Usage");
        actualCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("actualUsage"));

        TableColumn<VariancePreviewRow, Double> soldCol = new TableColumn<>("Sold Usage");
        soldCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("soldUsage"));

        TableColumn<VariancePreviewRow, Double> varianceCol = new TableColumn<>("Variance");
        varianceCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("variance"));

        table.getColumns().setAll(productCol, actualCol, soldCol, varianceCol);
    }

    private void loadCounts() {
        List<InventoryCount> alcoholCounts = countDao.findAll().stream()
                .filter(this::isAlcoholCount)
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

    private void showGenerateNotice() {
        if (openingCountBox.getValue() == null || closingCountBox.getValue() == null) {
            showAlert(Alert.AlertType.WARNING, "Missing Counts", "Choose opening and closing alcohol counts.");
            return;
        }

        if (selectedUsageFile == null) {
            showAlert(Alert.AlertType.WARNING, "Missing POS Usage Report", "Choose the POS usage report for this period.");
            return;
        }

        showAlert(
                Alert.AlertType.INFORMATION,
                "Variance Report",
                "Alcohol variance calculation will use the saved Sales Mappings in the next build step."
        );
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static class VariancePreviewRow {
        private final String product;
        private final double actualUsage;
        private final double soldUsage;
        private final double variance;

        public VariancePreviewRow(String product, double actualUsage, double soldUsage, double variance) {
            this.product = product;
            this.actualUsage = actualUsage;
            this.soldUsage = soldUsage;
            this.variance = variance;
        }

        public String getProduct() {
            return product;
        }

        public double getActualUsage() {
            return actualUsage;
        }

        public double getSoldUsage() {
            return soldUsage;
        }

        public double getVariance() {
            return variance;
        }
    }
}
