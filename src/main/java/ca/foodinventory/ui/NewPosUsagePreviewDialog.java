package ca.foodinventory.ui;

import ca.foodinventory.model.NewPosUsagePreview;
import ca.foodinventory.model.NewPosMenuItem;
import ca.foodinventory.service.NewPosProductionDryRunService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.concurrent.Task;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class NewPosUsagePreviewDialog extends Dialog<Void> {
    public NewPosUsagePreviewDialog(NewPosUsagePreview preview, List<NewPosMenuItem> catalog) {
        setTitle("New POS Usage Preview");
        setHeaderText("Selected Week: " + preview.startDate() + " to " + preview.endDate());
        ButtonType productionButton = new ButtonType("Preview Production Impact", ButtonBar.ButtonData.OTHER);
        getDialogPane().getButtonTypes().addAll(productionButton, ButtonType.CLOSE);
        Button action = (Button) getDialogPane().lookupButton(productionButton);
        action.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            action.setDisable(true);
            Task<ca.foodinventory.model.ProductionReportSummary> task = new Task<>() {
                @Override protected ca.foodinventory.model.ProductionReportSummary call() {
                    return new NewPosProductionDryRunService().generate(preview, catalog);
                }
            };
            task.setOnSucceeded(done -> {
                action.setDisable(false);
                ProductionReportDialog report = new ProductionReportDialog(task.getValue());
                report.setTitle("New POS Production Dry Run");
                report.setHeaderText("Calculated from mapped items for " + preview.startDate() + " to "
                        + preview.endDate() + ". No production week was saved.");
                report.showAndWait();
            });
            task.setOnFailed(failed -> {
                action.setDisable(false);
                new Alert(Alert.AlertType.ERROR, "Production dry run failed: "
                        + task.getException().getMessage()).showAndWait();
            });
            Thread thread = new Thread(task, "new-pos-production-dry-run");
            thread.setDaemon(true);
            thread.start();
        });
        getDialogPane().setPrefSize(1200, 700);

        int unmatched = (int) preview.rows().stream().filter(row -> !"Mapped".equals(row.status())).count();
        Label summary = new Label(preview.rows().size() + " distinct candidate items; "
                + preview.repeatedRows() + " repeated rows, " + preview.conflictingRows() + " conflicts, "
                + preview.dayPartRows() + " day-part rows, " + preview.otherRows() + " other rows. "
                + unmatched + " items need review or mapping.");
        Label caution = new Label("Read-only preview. Candidate items have a following day-part row; "
                + "other rows and conflicting repeats are excluded from daily totals.");
        TableView<NewPosUsagePreview.Row> table = new TableView<>(FXCollections.observableArrayList(preview.rows()));
        TableColumn<NewPosUsagePreview.Row, String> name = new TableColumn<>("Item Name");
        name.setCellValueFactory(value -> new SimpleStringProperty(value.getValue().name()));
        name.setPrefWidth(280);
        table.getColumns().add(name);
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("EEE M/d");
        StringBuilder totals = new StringBuilder("Daily candidate totals: ");
        for (int day = 0; day < 7; day++) {
            final int index = day;
            String heading = preview.dates().get(day).format(dateFormat);
            TableColumn<NewPosUsagePreview.Row, String> quantity = new TableColumn<>(heading);
            quantity.setCellValueFactory(value -> new SimpleStringProperty(format(value.getValue().quantities()[index])));
            quantity.setPrefWidth(90);
            table.getColumns().add(quantity);
            if (day > 0) totals.append("  |  ");
            totals.append(heading).append(": ").append(format(preview.dailyTotals()[day]));
        }
        TableColumn<NewPosUsagePreview.Row, String> week = new TableColumn<>("WTD");
        week.setCellValueFactory(value -> new SimpleStringProperty(format(value.getValue().weekToDate())));
        week.setPrefWidth(75);
        table.getColumns().add(week);
        TableColumn<NewPosUsagePreview.Row, String> repeats = new TableColumn<>("Rows");
        repeats.setCellValueFactory(value -> new SimpleStringProperty(String.valueOf(value.getValue().sourceRows())));
        repeats.setPrefWidth(60);
        table.getColumns().add(repeats);
        TableColumn<NewPosUsagePreview.Row, String> status = new TableColumn<>("Status");
        status.setCellValueFactory(value -> new SimpleStringProperty(value.getValue().status()));
        status.setPrefWidth(170);
        table.getColumns().add(status);

        VBox content = new VBox(10, summary, caution, new Label(totals.toString()), table);
        content.setPadding(new Insets(12));
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);
        getDialogPane().setContent(content);
    }

    private String format(double number) {
        if (number == Math.rint(number)) return String.valueOf((long) number);
        return String.format(java.util.Locale.ROOT, "%.2f", number);
    }
}
