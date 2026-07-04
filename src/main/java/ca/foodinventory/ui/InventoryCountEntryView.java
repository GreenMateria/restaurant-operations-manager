package ca.foodinventory.ui;

import ca.foodinventory.dao.InventoryCountDao;
import ca.foodinventory.dao.InventoryCountLineDao;
import ca.foodinventory.model.InventoryCount;
import ca.foodinventory.model.InventoryCountLine;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;

import java.util.List;

public class InventoryCountEntryView {

    private final InventoryCount count;
    private final InventoryCountLineDao lineDao = new InventoryCountLineDao();
    private final InventoryCountDao countDao = new InventoryCountDao();

    private final TableView<InventoryCountLine> table = new TableView<>();

    public InventoryCountEntryView(InventoryCount count) {
        this.count = count;
    }

    public BorderPane getView() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-dark");

        Label title = new Label("Inventory Count: " + count.getTemplateName());
        title.getStyleClass().add("page-title");

        Label dateLabel = new Label("Date: " + count.getCountDate());

        Button saveButton = new Button("Save Quantities");
        Button completeButton = new Button("Complete Count");
        Button refreshButton = new Button("Refresh");

        HBox buttons = new HBox(10, saveButton, completeButton, refreshButton);
        VBox top = new VBox(10, title, dateLabel, buttons);
        top.setStyle("-fx-padding: 15;");

        setupTable();

        saveButton.setOnAction(e -> saveQuantities());
        completeButton.setOnAction(e -> completeCount());
        refreshButton.setOnAction(e -> refreshTable());

        root.setTop(top);
        root.setCenter(table);

        refreshTable();

        return root;
    }

    private void setupTable() {
        table.setEditable(true);

        TableColumn<InventoryCountLine, String> sectionCol = new TableColumn<>("Section");
        sectionCol.setCellValueFactory(new PropertyValueFactory<>("sectionName"));
        sectionCol.setPrefWidth(160);

        TableColumn<InventoryCountLine, String> skuCol = new TableColumn<>("SKU");
        skuCol.setCellValueFactory(new PropertyValueFactory<>("sku"));
        skuCol.setPrefWidth(100);

        TableColumn<InventoryCountLine, String> productCol = new TableColumn<>("Product");
        productCol.setCellValueFactory(new PropertyValueFactory<>("productDescription"));
        productCol.setPrefWidth(300);

        TableColumn<InventoryCountLine, String> quantityCol = new TableColumn<>("Quantity");
        quantityCol.setCellValueFactory(data ->
                new SimpleStringProperty(String.valueOf(data.getValue().getQuantity()))
        );
        quantityCol.setPrefWidth(120);

        quantityCol.setCellFactory(col -> new TableCell<>() {
            private final TextField textField = new TextField();

            {
                textField.setOnAction(e -> {

                    commitEdit(textField.getText());

                    int nextRow = getIndex() + 1;

                    if (nextRow < getTableView().getItems().size()) {

                        getTableView().getSelectionModel().select(nextRow);

                        getTableView().scrollTo(nextRow);

                        getTableView().edit(
                                nextRow,
                                getTableColumn()
                        );

                    }

                });

                textField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                    if (!isFocused) {
                        commitEdit(textField.getText());
                    }
                });
            }

            @Override
            public void startEdit() {
                super.startEdit();
                textField.setText(getItem());
                setText(null);
                setGraphic(textField);
                textField.requestFocus();
                textField.selectAll();
            }

            @Override
            public void cancelEdit() {
                super.cancelEdit();
                setText(getItem());
                setGraphic(null);
            }

            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);

                if (empty) {
                    setText(null);
                    setGraphic(null);
                } else if (isEditing()) {
                    textField.setText(value);
                    setText(null);
                    setGraphic(textField);
                } else {
                    setText(value);
                    setGraphic(null);
                }
            }

            @Override
            public void commitEdit(String value) {
                super.commitEdit(value);

                InventoryCountLine line = getTableView().getItems().get(getIndex());

                try {
                    double quantity = Double.parseDouble(value);
                    double convertedQuantity = quantity * line.getConversionFactor();

                    line.setQuantity(quantity);
                    line.setConvertedQuantity(convertedQuantity);

                    setText(String.valueOf(quantity));
                } catch (NumberFormatException ex) {
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("Invalid Quantity");
                    alert.setHeaderText(null);
                    alert.setContentText("Please enter a valid number.");
                    alert.showAndWait();

                    setText(String.valueOf(line.getQuantity()));
                }

                setGraphic(null);
            }
        });

        TableColumn<InventoryCountLine, String> unitCol = new TableColumn<>("Unit");
        unitCol.setCellValueFactory(new PropertyValueFactory<>("countUnit"));
        unitCol.setPrefWidth(100);

        table.getColumns().setAll(
                sectionCol,
                skuCol,
                productCol,
                quantityCol,
                unitCol
        );
    }

    private void refreshTable() {
        List<InventoryCountLine> lines = lineDao.findByCount(count.getId());
        table.setItems(FXCollections.observableArrayList(lines));
    }

    private void saveQuantities() {
        for (InventoryCountLine line : table.getItems()) {
            lineDao.updateQuantity(
                    line.getId(),
                    line.getQuantity(),
                    line.getConvertedQuantity()
            );
        }

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Saved");
        alert.setHeaderText(null);
        alert.setContentText("Inventory quantities saved.");
        alert.showAndWait();
    }

    private void completeCount() {
        saveQuantities();
        countDao.markCompleted(count.getId());

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Completed");
        alert.setHeaderText(null);
        alert.setContentText("Inventory count marked as completed.");
        alert.showAndWait();
    }
}