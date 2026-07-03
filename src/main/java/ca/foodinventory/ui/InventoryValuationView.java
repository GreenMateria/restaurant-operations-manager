package ca.foodinventory.ui;

import ca.foodinventory.dao.InventoryCountDao;
import ca.foodinventory.model.InventoryCount;
import ca.foodinventory.model.InventoryValuationLine;
import ca.foodinventory.service.InventoryValuationService;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ca.foodinventory.model.CategoryCostReportRow;
import ca.foodinventory.model.WeeklyCostReport;
import ca.foodinventory.service.InventoryCostService;

public class InventoryValuationView {

    private final InventoryCountDao countDao = new InventoryCountDao();
    private final InventoryValuationService valuationService = new InventoryValuationService();

    private final ComboBox<InventoryCount> countComboBox = new ComboBox<>();

    private final TableView<InventoryValuationLine> detailedTable = new TableView<>();
    private final TableView<CategorySummaryLine> summaryTable = new TableView<>();
    private final TextArea departmentReportArea = new TextArea();

    private final InventoryCostService costService = new InventoryCostService();

    private final ComboBox<InventoryCount> openingCountComboBox = new ComboBox<>();
    private final ComboBox<InventoryCount> closingCountComboBox = new ComboBox<>();

    private final TextArea weeklyCostReportArea = new TextArea();
    private final Label totalValueLabel = new Label("Total Inventory Value: $0.00");
    private final Label summaryTotalLabel = new Label("Total Inventory Value: $0.00");

    public BorderPane getView() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-dark");

        Label title = new Label("Inventory Valuation");
        title.getStyleClass().add("page-title");

        Button loadButton = new Button("Load Valuation");
        loadButton.getStyleClass().add("primary-button");
        loadButton.setOnAction(e -> loadValuation());

        countComboBox.setPromptText("Select completed count");
        countComboBox.setPrefWidth(350);

        HBox topBar = new HBox(15, title, countComboBox, loadButton);
        topBar.getStyleClass().add("content-area");

        setupDetailedTable();
        setupSummaryTable();
        loadCompletedCounts();

        totalValueLabel.getStyleClass().add("section-title");
        summaryTotalLabel.getStyleClass().add("section-title");

        VBox detailedContent = new VBox(10, detailedTable, totalValueLabel);
        detailedContent.getStyleClass().add("content-area");

        VBox summaryContent = new VBox(10, summaryTable, summaryTotalLabel);
        summaryContent.getStyleClass().add("content-area");

        departmentReportArea.setEditable(false);
        departmentReportArea.setWrapText(false);
        departmentReportArea.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 13px;");

        VBox departmentReportContent = new VBox(10, departmentReportArea);
        departmentReportContent.getStyleClass().add("content-area");

        openingCountComboBox.setPromptText("Select opening count");
        openingCountComboBox.setPrefWidth(450);

        closingCountComboBox.setPromptText("Select closing count");
        closingCountComboBox.setPrefWidth(450);

        Button generateCostReportButton = new Button("Generate Cost Report");
        generateCostReportButton.getStyleClass().add("primary-button");
        generateCostReportButton.setOnAction(e -> generateWeeklyCostReport());

        weeklyCostReportArea.setEditable(false);
        weeklyCostReportArea.setWrapText(false);
        weeklyCostReportArea.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 13px;");

        HBox costReportControls = new HBox(
                10,
                new Label("Opening Count:"),
                openingCountComboBox,
                new Label("Closing Count:"),
                closingCountComboBox,
                generateCostReportButton
        );

        VBox costReportContent = new VBox(10, costReportControls, weeklyCostReportArea);
        costReportContent.getStyleClass().add("content-area");

        TabPane tabPane = new TabPane();

        Tab detailedTab = new Tab("Detailed Valuation", detailedContent);
        detailedTab.setClosable(false);

        Tab summaryTab = new Tab("Category Summary", summaryContent);
        summaryTab.setClosable(false);

        Tab departmentReportTab = new Tab("Department Report", departmentReportContent);
        departmentReportTab.setClosable(false);

        Tab costReportTab = new Tab("Weekly Cost Report", costReportContent);
        costReportTab.setClosable(false);

        tabPane.getTabs().addAll(
                detailedTab,
                summaryTab,
                departmentReportTab,
                costReportTab
        );

        root.setTop(topBar);
        root.setCenter(tabPane);

        return root;
    }

    private void setupDetailedTable() {
        TableColumn<InventoryValuationLine, String> reportingCol = new TableColumn<>("Reporting Category");
        reportingCol.setCellValueFactory(new PropertyValueFactory<>("reportingCategory"));

        TableColumn<InventoryValuationLine, String> categoryCol = new TableColumn<>("Category");
        categoryCol.setCellValueFactory(new PropertyValueFactory<>("category"));

        TableColumn<InventoryValuationLine, String> skuCol = new TableColumn<>("SKU");
        skuCol.setCellValueFactory(new PropertyValueFactory<>("sku"));

        TableColumn<InventoryValuationLine, String> productCol = new TableColumn<>("Product");
        productCol.setCellValueFactory(new PropertyValueFactory<>("productDescription"));
        productCol.setPrefWidth(250);

        TableColumn<InventoryValuationLine, Double> qtyCol = new TableColumn<>("Counted Qty");
        qtyCol.setCellValueFactory(new PropertyValueFactory<>("countedQuantity"));

        TableColumn<InventoryValuationLine, BigDecimal> avgCostCol = new TableColumn<>("Avg Cost");
        avgCostCol.setCellValueFactory(new PropertyValueFactory<>("averageCost"));
        avgCostCol.setCellFactory(column -> moneyCell());

        TableColumn<InventoryValuationLine, BigDecimal> valueCol = new TableColumn<>("Inventory Value");
        valueCol.setCellValueFactory(new PropertyValueFactory<>("inventoryValue"));
        valueCol.setCellFactory(column -> moneyCell());

        TableColumn<InventoryValuationLine, String> sourceCol = new TableColumn<>("Cost Source");
        sourceCol.setCellValueFactory(new PropertyValueFactory<>("costSource"));

        detailedTable.getColumns().setAll(
                reportingCol,
                categoryCol,
                skuCol,
                productCol,
                qtyCol,
                avgCostCol,
                valueCol,
                sourceCol
        );

        detailedTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    private void setupSummaryTable() {
        TableColumn<CategorySummaryLine, String> categoryCol =
                new TableColumn<>("Reporting Category");
        categoryCol.setCellValueFactory(new PropertyValueFactory<>("reportingCategory"));
        categoryCol.setPrefWidth(250);

        TableColumn<CategorySummaryLine, BigDecimal> totalCol =
                new TableColumn<>("Total Value");
        totalCol.setCellValueFactory(new PropertyValueFactory<>("totalValue"));
        totalCol.setCellFactory(column -> moneyCell());

        TableColumn<CategorySummaryLine, Double> percentCol =
                new TableColumn<>("% of Inventory");
        percentCol.setCellValueFactory(new PropertyValueFactory<>("percentageOfTotal"));
        percentCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);

                if (empty || value == null) {
                    setText(null);
                } else {
                    setText(String.format("%.2f%%", value));
                }
            }
        });

        summaryTable.getColumns().setAll(
                categoryCol,
                totalCol,
                percentCol
        );

        summaryTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    private void loadCompletedCounts() {
        countComboBox.setItems(FXCollections.observableArrayList(
                countDao.findAll()
                        .stream()
                        .filter(InventoryCount::isCompleted)
                        .toList()
        ));

        countComboBox.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(InventoryCount count, boolean empty) {
                super.updateItem(count, empty);
                setText(empty || count == null ? null : formatCountLabel(count));
            }
        });

        countComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(InventoryCount count, boolean empty) {
                super.updateItem(count, empty);
                setText(empty || count == null ? null : formatCountLabel(count));
            }
        });
        openingCountComboBox.setItems(countComboBox.getItems());
        closingCountComboBox.setItems(countComboBox.getItems());

        openingCountComboBox.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(InventoryCount count, boolean empty) {
                super.updateItem(count, empty);
                setText(empty || count == null ? null : formatCountLabel(count));
            }
        });

        openingCountComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(InventoryCount count, boolean empty) {
                super.updateItem(count, empty);
                setText(empty || count == null ? null : formatCountLabel(count));
            }
        });

        closingCountComboBox.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(InventoryCount count, boolean empty) {
                super.updateItem(count, empty);
                setText(empty || count == null ? null : formatCountLabel(count));
            }
        });

        closingCountComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(InventoryCount count, boolean empty) {
                super.updateItem(count, empty);
                setText(empty || count == null ? null : formatCountLabel(count));
            }
        });
    }

    private void loadValuation() {
        InventoryCount selected = countComboBox.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showAlert(
                    Alert.AlertType.WARNING,
                    "No Count Selected",
                    "Please select a completed inventory count."
            );
            return;
        }

        List<InventoryValuationLine> lines = valuationService.calculateValuation(selected.getId());

        detailedTable.setItems(FXCollections.observableArrayList(lines));
        summaryTable.setItems(FXCollections.observableArrayList(buildCategorySummary(lines)));
        departmentReportArea.setText(buildDepartmentReport(lines));

        BigDecimal total = lines.stream()
                .map(InventoryValuationLine::getInventoryValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        totalValueLabel.setText("Total Inventory Value: " + formatMoney(total));
        summaryTotalLabel.setText("Total Inventory Value: " + formatMoney(total));
    }

    private List<CategorySummaryLine> buildCategorySummary(List<InventoryValuationLine> lines) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();

        for (InventoryValuationLine line : lines) {
            String reportingCategory = line.getReportingCategory();

            if (reportingCategory == null || reportingCategory.isBlank()) {
                reportingCategory = "OTHER";
            }

            totals.put(
                    reportingCategory,
                    totals.getOrDefault(reportingCategory, BigDecimal.ZERO)
                            .add(line.getInventoryValue())
            );
        }

        BigDecimal grandTotal = totals.values()
                .stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return totals.entrySet()
                .stream()
                .map(entry -> {

                    double percentage = 0;

                    if (grandTotal.compareTo(BigDecimal.ZERO) > 0) {
                        percentage = entry.getValue()
                                .multiply(BigDecimal.valueOf(100))
                                .divide(grandTotal, 4, RoundingMode.HALF_UP)
                                .doubleValue();
                    }

                    return new CategorySummaryLine(
                            entry.getKey(),
                            entry.getValue().setScale(2, RoundingMode.HALF_UP),
                            percentage
                    );
                })
                .toList();
    }

    private String formatCountLabel(InventoryCount count) {
        return count.getTemplateName()
                + " | Count Date: " + count.getCountDate()
                + " | Period: " + count.getPeriodStartDate()
                + " to " + count.getPeriodEndDate();
    }

    private String formatMoney(BigDecimal value) {
        if (value == null) {
            value = BigDecimal.ZERO;
        }

        return "$" + value.setScale(2, RoundingMode.HALF_UP);
    }

    private <T> TableCell<T, BigDecimal> moneyCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : formatMoney(value));
            }
        };
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();

    }
    private String buildDepartmentReport(List<InventoryValuationLine> lines) {
        StringBuilder report = new StringBuilder();

        BigDecimal grandTotal = BigDecimal.ZERO;

        Map<String, Map<String, List<InventoryValuationLine>>> grouped = new LinkedHashMap<>();

        for (InventoryValuationLine line : lines) {
            String reportingCategory = line.getReportingCategory();

            if (reportingCategory == null || reportingCategory.isBlank()) {
                reportingCategory = "OTHER";
            }

            String category = line.getCategory();

            if (category == null || category.isBlank()) {
                category = "Uncategorized";
            }

            grouped
                    .computeIfAbsent(reportingCategory, key -> new LinkedHashMap<>())
                    .computeIfAbsent(category, key -> new java.util.ArrayList<>())
                    .add(line);
        }

        report.append("INVENTORY VALUATION REPORT\n");
        report.append("============================================================\n\n");

        for (Map.Entry<String, Map<String, List<InventoryValuationLine>>> reportingEntry : grouped.entrySet()) {
            String reportingCategory = reportingEntry.getKey();

            BigDecimal reportingTotal = BigDecimal.ZERO;

            report.append(reportingCategory).append("\n");
            report.append("------------------------------------------------------------\n");

            for (Map.Entry<String, List<InventoryValuationLine>> categoryEntry : reportingEntry.getValue().entrySet()) {
                String category = categoryEntry.getKey();

                BigDecimal categoryTotal = BigDecimal.ZERO;

                report.append("\n");
                report.append("  ").append(category).append("\n");

                for (InventoryValuationLine line : categoryEntry.getValue()) {
                    BigDecimal value = line.getInventoryValue();

                    categoryTotal = categoryTotal.add(value);
                    reportingTotal = reportingTotal.add(value);
                    grandTotal = grandTotal.add(value);

                    report.append(String.format(
                            "    %-35s %12s\n",
                            trimForReport(line.getProductDescription(), 35),
                            formatMoney(value)
                    ));
                }

                report.append(String.format(
                        "  %-35s %12s\n",
                        category + " TOTAL",
                        formatMoney(categoryTotal)
                ));
            }

            report.append("\n");
            report.append(String.format(
                    "%-37s %12s\n",
                    reportingCategory + " TOTAL",
                    formatMoney(reportingTotal)
            ));
            report.append("\n\n");
        }

        report.append("============================================================\n");
        report.append(String.format(
                "%-37s %12s\n",
                "TOTAL INVENTORY",
                formatMoney(grandTotal)
        ));

        return report.toString();
    }
    private String trimForReport(String value, int maxLength) {
        if (value == null) {
            return "";
        }

        if (value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength - 3) + "...";
    }
    public static class CategorySummaryLine {

        private final String reportingCategory;
        private final BigDecimal totalValue;
        private final double percentageOfTotal;

        public CategorySummaryLine(
                String reportingCategory,
                BigDecimal totalValue,
                double percentageOfTotal
        ) {
            this.reportingCategory = reportingCategory;
            this.totalValue = totalValue;
            this.percentageOfTotal = percentageOfTotal;
        }

        public String getReportingCategory() {
            return reportingCategory;
        }

        public BigDecimal getTotalValue() {
            return totalValue;
        }

        public double getPercentageOfTotal() {
            return percentageOfTotal;
        }
    }
    private void generateWeeklyCostReport() {
        InventoryCount openingCount = openingCountComboBox.getSelectionModel().getSelectedItem();
        InventoryCount closingCount = closingCountComboBox.getSelectionModel().getSelectedItem();

        if (openingCount == null || closingCount == null) {
            showAlert(
                    Alert.AlertType.WARNING,
                    "Missing Counts",
                    "Please select both an opening count and a closing count."
            );
            return;
        }

        try {
            WeeklyCostReport report = costService.generateReport(
                    openingCount.getId(),
                    closingCount.getId()
            );

            weeklyCostReportArea.setText(buildWeeklyCostReportText(report));

        } catch (Exception ex) {
            ex.printStackTrace();

            showAlert(
                    Alert.AlertType.ERROR,
                    "Cost Report Failed",
                    ex.getMessage()
            );
        }
        if (openingCount.getId() == closingCount.getId()) {
            showAlert(
                    Alert.AlertType.WARNING,
                    "Invalid Count Selection",
                    "Opening count and closing count must be different counts."
            );
            return;
        }
    }

    private String buildWeeklyCostReportText(WeeklyCostReport report) {
        StringBuilder builder = new StringBuilder();

        builder.append("WEEKLY COST REPORT\n");
        builder.append("Period: ")
                .append(report.getPeriodStartDate())
                .append(" to ")
                .append(report.getPeriodEndDate())
                .append("\n\n");

        builder.append("-------------------------------------------------------------\n");
        builder.append(String.format("%-18s %13s %13s %10s\n", "CATEGORY", "SALES", "USAGE", "COST %"));
        builder.append("-------------------------------------------------------------\n\n");

        for (CategoryCostReportRow row : report.getCogsRows()) {
            builder.append(String.format(
                    "%-18s %13s %13s %9s%%\n",
                    row.getCategory(),
                    formatMoney(row.getSales()),
                    formatMoney(row.getUsage()),
                    row.getCostPercent()
            ));
        }

        builder.append("\n-------------------------------------------------------------\n");
        builder.append("TOTAL COST OF GOODS SOLD\n");
        builder.append("-------------------------------------------------------------\n\n");

        builder.append(String.format("%-18s %13s\n", "TOTAL SALES", formatMoney(report.getTotalSales())));
        builder.append(String.format("%-18s %13s\n", "TOTAL USAGE", formatMoney(report.getTotalUsage())));
        builder.append(String.format("%-18s %12s%%\n", "COGS %", report.getCogsPercent()));

        builder.append("\n\nOPERATING SUPPLIES\n");
        builder.append("-------------------------------------------------------------\n");
        builder.append(String.format("%-18s %13s %15s\n", "CATEGORY", "USAGE", "% OF REVENUE"));
        builder.append("-------------------------------------------------------------\n\n");

        for (CategoryCostReportRow row : report.getSuppliesRows()) {
            builder.append(String.format(
                    "%-18s %13s %14s%%\n",
                    row.getCategory(),
                    formatMoney(row.getUsage()),
                    row.getCostPercent()
            ));
        }

        builder.append("\n-------------------------------------------------------------\n");
        builder.append(String.format(
                "%-18s %13s %14s%%\n",
                "TOTAL SUPPLIES",
                formatMoney(report.getTotalSuppliesUsage()),
                report.getSuppliesPercent()
        ));

        return builder.toString();
    }
}