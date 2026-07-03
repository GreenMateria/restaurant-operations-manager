package ca.foodinventory.ui;

import ca.foodinventory.dao.SalesPeriodDao;
import ca.foodinventory.model.ImportedSalesSummary;
import ca.foodinventory.model.SalesPeriod;
import ca.foodinventory.service.PosSalesImportService;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class SalesEntryView {

    private final SalesPeriodDao salesPeriodDao = new SalesPeriodDao();

    private final DatePicker periodStartPicker = new DatePicker();
    private final DatePicker periodEndPicker = new DatePicker();

    private final TextField foodSalesField = new TextField("0.00");
    private final TextField beerSalesField = new TextField("0.00");
    private final TextField wineSalesField = new TextField("0.00");
    private final TextField draughtSalesField = new TextField("0.00");
    private final TextField importDraughtSalesField = new TextField("0.00");
    private final TextField liquorSalesField = new TextField("0.00");

    private final TableView<SalesPeriod> table = new TableView<>();

    public BorderPane getView() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-dark");

        setupTable();
        loadSalesPeriods();

        Label title = new Label("Sales Entry");
        title.getStyleClass().add("page-title");

        Button saveButton = new Button("Save Sales Period");
        saveButton.getStyleClass().add("primary-button");
        saveButton.setOnAction(e -> saveSalesPeriod());

        Button importButton = new Button("Import Sales Report");
        importButton.getStyleClass().add("primary-button");
        importButton.setOnAction(e -> importSalesReport());

        HBox topBar = new HBox(10, title, saveButton, importButton);

        GridPane form = buildForm();
        form.getStyleClass().add("content-area");

        Label historyTitle = new Label("Saved Sales Periods");
        historyTitle.getStyleClass().add("section-title");

        VBox centerContent = new VBox(15, form, historyTitle, table);
        centerContent.getStyleClass().add("content-area");

        root.setTop(topBar);
        root.setCenter(centerContent);

        return root;
    }

    private GridPane buildForm() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        int row = 0;

        grid.add(new Label("Period Start:"), 0, row);
        grid.add(periodStartPicker, 1, row);

        row++;

        grid.add(new Label("Period End:"), 0, row);
        grid.add(periodEndPicker, 1, row);

        row++;

        grid.add(new Label("Food Sales:"), 0, row);
        grid.add(foodSalesField, 1, row);

        row++;

        grid.add(new Label("Beer Sales:"), 0, row);
        grid.add(beerSalesField, 1, row);

        row++;

        grid.add(new Label("Wine Sales:"), 0, row);
        grid.add(wineSalesField, 1, row);

        row++;

        grid.add(new Label("Draught Sales:"), 0, row);
        grid.add(draughtSalesField, 1, row);

        row++;

        grid.add(new Label("Import Draught Sales:"), 0, row);
        grid.add(importDraughtSalesField, 1, row);

        row++;

        grid.add(new Label("Liquor Sales:"), 0, row);
        grid.add(liquorSalesField, 1, row);

        return grid;
    }

    private void setupTable() {
        TableColumn<SalesPeriod, String> startCol = new TableColumn<>("Period Start");
        startCol.setCellValueFactory(new PropertyValueFactory<>("periodStartDate"));

        TableColumn<SalesPeriod, String> endCol = new TableColumn<>("Period End");
        endCol.setCellValueFactory(new PropertyValueFactory<>("periodEndDate"));

        TableColumn<SalesPeriod, BigDecimal> foodCol = moneyColumn("Food Sales", "foodSales");
        TableColumn<SalesPeriod, BigDecimal> beerCol = moneyColumn("Beer Sales", "beerSales");
        TableColumn<SalesPeriod, BigDecimal> wineCol = moneyColumn("Wine Sales", "wineSales");
        TableColumn<SalesPeriod, BigDecimal> draughtCol = moneyColumn("Draught Sales", "draughtSales");
        TableColumn<SalesPeriod, BigDecimal> importDraughtCol = moneyColumn("Import Draught", "importDraughtSales");
        TableColumn<SalesPeriod, BigDecimal> liquorCol = moneyColumn("Liquor Sales", "liquorSales");

        table.getColumns().setAll(
                startCol,
                endCol,
                foodCol,
                beerCol,
                wineCol,
                draughtCol,
                importDraughtCol,
                liquorCol
        );

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    private TableColumn<SalesPeriod, BigDecimal> moneyColumn(String title, String propertyName) {
        TableColumn<SalesPeriod, BigDecimal> column = new TableColumn<>(title);
        column.setCellValueFactory(new PropertyValueFactory<>(propertyName));

        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : formatMoney(value));
            }
        });

        return column;
    }

    private void importSalesReport() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Sales Report");

        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx")
        );

        File file = chooser.showOpenDialog(null);

        if (file == null) {
            return;
        }

        try {
            PosSalesImportService service = new PosSalesImportService();
            ImportedSalesSummary summary = service.importSalesReport(file);

            foodSalesField.setText(summary.getFoodSales().toPlainString());
            beerSalesField.setText(summary.getBeerSales().toPlainString());
            wineSalesField.setText(summary.getWineSales().toPlainString());
            draughtSalesField.setText(summary.getDraughtSales().toPlainString());
            importDraughtSalesField.setText(summary.getImportDraughtSales().toPlainString());
            liquorSalesField.setText(summary.getLiquorSales().toPlainString());

            showAlert(
                    Alert.AlertType.INFORMATION,
                    "Import Complete",
                    "Sales report imported successfully. Review the totals, then click Save Sales Period."
            );

        } catch (Exception ex) {
            ex.printStackTrace();

            showAlert(
                    Alert.AlertType.ERROR,
                    "Import Failed",
                    ex.getMessage()
            );
        }

    }

    private void saveSalesPeriod() {
        if (periodStartPicker.getValue() == null || periodEndPicker.getValue() == null) {
            showAlert(
                    Alert.AlertType.WARNING,
                    "Missing Dates",
                    "Please select both a period start and period end date."
            );
            return;
        }

        try {
            SalesPeriod salesPeriod = new SalesPeriod(
                    0,
                    periodStartPicker.getValue().toString(),
                    periodEndPicker.getValue().toString(),
                    parseMoney(foodSalesField),
                    parseMoney(beerSalesField),
                    parseMoney(wineSalesField),
                    parseMoney(draughtSalesField),
                    parseMoney(importDraughtSalesField),
                    parseMoney(liquorSalesField)
            );

            salesPeriodDao.save(salesPeriod);
            loadSalesPeriods();

            showAlert(
                    Alert.AlertType.INFORMATION,
                    "Sales Saved",
                    "Sales period saved successfully."
            );

        } catch (NumberFormatException ex) {
            showAlert(
                    Alert.AlertType.ERROR,
                    "Invalid Sales Amount",
                    "Please enter valid numbers for all sales fields."
            );
        }
    }

    private void loadSalesPeriods() {
        table.setItems(FXCollections.observableArrayList(salesPeriodDao.findAll()));
    }

    private BigDecimal parseMoney(TextField field) {
        String value = field.getText().trim();

        if (value.isBlank()) {
            return BigDecimal.ZERO;
        }

        value = value.replace("$", "").replace(",", "");

        return new BigDecimal(value);
    }

    private String formatMoney(BigDecimal value) {
        if (value == null) {
            value = BigDecimal.ZERO;
        }

        return "$" + value.setScale(2, RoundingMode.HALF_UP);
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}