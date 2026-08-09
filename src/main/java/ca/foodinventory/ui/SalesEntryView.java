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
    private final TextField foodNetSalesField = new TextField("0.00");
    private final TextField beerNetSalesField = new TextField("0.00");
    private final TextField wineNetSalesField = new TextField("0.00");
    private final TextField draughtNetSalesField = new TextField("0.00");
    private final TextField importDraughtNetSalesField = new TextField("0.00");
    private final TextField liquorNetSalesField = new TextField("0.00");

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

        grid.add(new Label("Category"), 0, row);
        grid.add(new Label("Gross Sales"), 1, row);
        grid.add(new Label("Net Sales"), 2, row);

        row++;

        addSalesRow(grid, row, "Food:", foodSalesField, foodNetSalesField);

        row++;

        addSalesRow(grid, row, "Beer:", beerSalesField, beerNetSalesField);

        row++;

        addSalesRow(grid, row, "Wine:", wineSalesField, wineNetSalesField);

        row++;

        addSalesRow(grid, row, "Draught:", draughtSalesField, draughtNetSalesField);

        row++;

        addSalesRow(grid, row, "Import Draught:", importDraughtSalesField, importDraughtNetSalesField);

        row++;

        addSalesRow(grid, row, "Liquor:", liquorSalesField, liquorNetSalesField);

        return grid;
    }

    private void addSalesRow(
            GridPane grid,
            int row,
            String label,
            TextField grossSalesField,
            TextField netSalesField
    ) {
        grid.add(new Label(label), 0, row);
        grid.add(grossSalesField, 1, row);
        grid.add(netSalesField, 2, row);
    }

    private void setupTable() {
        TableColumn<SalesPeriod, String> startCol = new TableColumn<>("Period Start");
        startCol.setCellValueFactory(new PropertyValueFactory<>("periodStartDate"));

        TableColumn<SalesPeriod, String> endCol = new TableColumn<>("Period End");
        endCol.setCellValueFactory(new PropertyValueFactory<>("periodEndDate"));

        TableColumn<SalesPeriod, BigDecimal> foodCol = moneyColumn("Food Gross", "foodSales");
        TableColumn<SalesPeriod, BigDecimal> foodNetCol = moneyColumn("Food Net", "foodNetSales");
        TableColumn<SalesPeriod, BigDecimal> beerCol = moneyColumn("Beer Gross", "beerSales");
        TableColumn<SalesPeriod, BigDecimal> beerNetCol = moneyColumn("Beer Net", "beerNetSales");
        TableColumn<SalesPeriod, BigDecimal> wineCol = moneyColumn("Wine Gross", "wineSales");
        TableColumn<SalesPeriod, BigDecimal> wineNetCol = moneyColumn("Wine Net", "wineNetSales");
        TableColumn<SalesPeriod, BigDecimal> draughtCol = moneyColumn("Draught Gross", "draughtSales");
        TableColumn<SalesPeriod, BigDecimal> draughtNetCol = moneyColumn("Draught Net", "draughtNetSales");
        TableColumn<SalesPeriod, BigDecimal> importDraughtCol = moneyColumn("Import Draught Gross", "importDraughtSales");
        TableColumn<SalesPeriod, BigDecimal> importDraughtNetCol = moneyColumn("Import Draught Net", "importDraughtNetSales");
        TableColumn<SalesPeriod, BigDecimal> liquorCol = moneyColumn("Liquor Gross", "liquorSales");
        TableColumn<SalesPeriod, BigDecimal> liquorNetCol = moneyColumn("Liquor Net", "liquorNetSales");

        table.getColumns().setAll(
                startCol,
                endCol,
                foodCol,
                foodNetCol,
                beerCol,
                beerNetCol,
                wineCol,
                wineNetCol,
                draughtCol,
                draughtNetCol,
                importDraughtCol,
                importDraughtNetCol,
                liquorCol,
                liquorNetCol
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
            foodNetSalesField.setText(summary.getFoodNetSales().toPlainString());
            beerNetSalesField.setText(summary.getBeerNetSales().toPlainString());
            wineNetSalesField.setText(summary.getWineNetSales().toPlainString());
            draughtNetSalesField.setText(summary.getDraughtNetSales().toPlainString());
            importDraughtNetSalesField.setText(summary.getImportDraughtNetSales().toPlainString());
            liquorNetSalesField.setText(summary.getLiquorNetSales().toPlainString());

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
                    parseMoney(liquorSalesField),
                    parseMoney(foodNetSalesField),
                    parseMoney(beerNetSalesField),
                    parseMoney(wineNetSalesField),
                    parseMoney(draughtNetSalesField),
                    parseMoney(importDraughtNetSalesField),
                    parseMoney(liquorNetSalesField)
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
