package ca.foodinventory.ui;

import ca.foodinventory.model.ProductionReportLine;
import ca.foodinventory.model.ProductionReportSummary;
import javafx.collections.FXCollections;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;

public class ProductionReportDialog extends Dialog<Void> {

    private final ProductionReportSummary summary;
    private final TableView<ProductionReportLine> table = new TableView<>();

    public ProductionReportDialog(ProductionReportSummary summary) {
        this.summary = summary;

        setTitle("Generated Production Report");
        setHeaderText("Production totals generated from usage report");

        setupTable();

        Label summaryLabel = new Label(buildSummaryText());
        VBox content = new VBox(10, summaryLabel, table);
        content.setPrefSize(WindowSizing.width(1000), WindowSizing.height(600));

        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
    }

    private void setupTable() {
        TableColumn<ProductionReportLine, String> stationCol = new TableColumn<>("Station");
        stationCol.setCellValueFactory(new PropertyValueFactory<>("stationName"));

        TableColumn<ProductionReportLine, String> itemCol = new TableColumn<>("Production Item");
        itemCol.setCellValueFactory(new PropertyValueFactory<>("productionItemName"));
        itemCol.setPrefWidth(220);

        TableColumn<ProductionReportLine, String> unitCol = new TableColumn<>("Unit");
        unitCol.setCellValueFactory(new PropertyValueFactory<>("unit"));

        TableColumn<ProductionReportLine, Double> mondayCol = quantityColumn("Mon", "mondayQuantity");
        TableColumn<ProductionReportLine, Double> tuesdayCol = quantityColumn("Tue", "tuesdayQuantity");
        TableColumn<ProductionReportLine, Double> wednesdayCol = quantityColumn("Wed", "wednesdayQuantity");
        TableColumn<ProductionReportLine, Double> thursdayCol = quantityColumn("Thu", "thursdayQuantity");
        TableColumn<ProductionReportLine, Double> fridayCol = quantityColumn("Fri", "fridayQuantity");
        TableColumn<ProductionReportLine, Double> saturdayCol = quantityColumn("Sat", "saturdayQuantity");
        TableColumn<ProductionReportLine, Double> sundayCol = quantityColumn("Sun", "sundayQuantity");
        TableColumn<ProductionReportLine, Double> weeklyCol = quantityColumn("Week", "weeklyQuantity");

        table.getColumns().setAll(
                stationCol,
                itemCol,
                unitCol,
                mondayCol,
                tuesdayCol,
                wednesdayCol,
                thursdayCol,
                fridayCol,
                saturdayCol,
                sundayCol,
                weeklyCol
        );
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setItems(FXCollections.observableArrayList(summary.getLines()));
    }

    private TableColumn<ProductionReportLine, Double> quantityColumn(String title, String property) {
        TableColumn<ProductionReportLine, Double> column = new TableColumn<>(title);
        column.setCellValueFactory(new PropertyValueFactory<>(property));
        column.setCellFactory(value -> new TableCell<>() {
            @Override
            protected void updateItem(Double quantity, boolean empty) {
                super.updateItem(quantity, empty);
                setText(empty || quantity == null ? null : formatNumber(quantity));
            }
        });
        return column;
    }

    private String buildSummaryText() {
        String text = "Imported POS rows: " + summary.getUsageReportSummary().getTotalRows()
                + " | Production items: " + summary.getLineCount()
                + " | Weekly production quantity: " + formatNumber(summary.getWeeklyQuantity());

        if (summary.getSkippedRowsWithoutProfile() > 0) {
            text += " | POS rows missing profile: " + summary.getSkippedRowsWithoutProfile();
        }

        return text;
    }

    private String formatNumber(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }

        return String.format("%.2f", value);
    }
}
