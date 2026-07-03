package ca.foodinventory.ui;

import ca.foodinventory.dao.InventoryCountDao;
import ca.foodinventory.dao.InventoryCountLineDao;
import ca.foodinventory.dao.InventoryCountTemplateDao;
import ca.foodinventory.model.InventoryCount;
import ca.foodinventory.model.InventoryCountLine;
import ca.foodinventory.model.InventoryCountTemplate;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PrinterJob;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.util.List;

public class InventoryCountsView {

    private final InventoryCountDao countDao = new InventoryCountDao();
    private final InventoryCountTemplateDao templateDao = new InventoryCountTemplateDao();
    private final InventoryCountLineDao lineDao = new InventoryCountLineDao();

    private final TableView<InventoryCount> table = new TableView<>();

    private final String department;

    public InventoryCountsView() {
        this.department = null;
    }

    public InventoryCountsView(String department) {
        this.department = department;
    }

    public BorderPane getView() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-dark");

        Label title = new Label(getTitleText());
        title.getStyleClass().add("page-title");

        Button startButton = new Button("Start Count");
        Button openButton = new Button("Open Count");
        Button printButton = new Button("Print Count Sheet");
        Button deleteButton = new Button("Delete Count");
        Button refreshButton = new Button("Refresh");

        startButton.getStyleClass().add("primary-button");
        openButton.getStyleClass().add("primary-button");
        printButton.getStyleClass().add("primary-button");
        deleteButton.getStyleClass().add("primary-button");
        refreshButton.getStyleClass().add("primary-button");

        HBox buttons = new HBox(10, startButton, openButton, printButton, deleteButton, refreshButton);
        VBox top = new VBox(10, title, buttons);
        top.setStyle("-fx-padding: 15;");

        setupTable();

        startButton.setOnAction(e -> showStartCountDialog());
        openButton.setOnAction(e -> openSelectedCount());
        refreshButton.setOnAction(e -> refreshTable());
        printButton.setOnAction(e -> printSelectedCountSheet());
        deleteButton.setOnAction(e -> deleteSelectedCount());

        root.setTop(top);
        root.setCenter(table);

        refreshTable();

        return root;
    }

    private void setupTable() {
        TableColumn<InventoryCount, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("countDate"));
        dateCol.setPrefWidth(140);

        TableColumn<InventoryCount, String> periodStartCol = new TableColumn<>("Period Start");
        periodStartCol.setCellValueFactory(new PropertyValueFactory<>("periodStartDate"));
        periodStartCol.setPrefWidth(140);

        TableColumn<InventoryCount, String> periodEndCol = new TableColumn<>("Period End");
        periodEndCol.setCellValueFactory(new PropertyValueFactory<>("periodEndDate"));
        periodEndCol.setPrefWidth(140);

        TableColumn<InventoryCount, String> templateCol = new TableColumn<>("Template");
        templateCol.setCellValueFactory(new PropertyValueFactory<>("templateName"));
        templateCol.setPrefWidth(220);

        TableColumn<InventoryCount, String> notesCol = new TableColumn<>("Notes");
        notesCol.setCellValueFactory(new PropertyValueFactory<>("notes"));
        notesCol.setPrefWidth(300);

        TableColumn<InventoryCount, Boolean> completedCol = new TableColumn<>("Completed");
        completedCol.setCellValueFactory(new PropertyValueFactory<>("completed"));
        completedCol.setPrefWidth(120);

        table.getColumns().setAll(
                dateCol,
                periodStartCol,
                periodEndCol,
                templateCol,
                notesCol,
                completedCol
        );
    }

    private void refreshTable() {
        List<InventoryCount> counts = countDao.findAll();

        if (department != null) {
            counts.removeIf(count -> {
                String templateName = count.getTemplateName() == null
                        ? ""
                        : count.getTemplateName().toUpperCase();

                return switch (department) {
                    case "FOOD" -> !templateName.contains("FOOD");
                    case "ALCOHOL" -> !templateName.contains("ALCOHOL");
                    case "SUPPLIES" -> !templateName.contains("SUPPLIES");
                    default -> false;
                };
            });
        }

        table.setItems(FXCollections.observableArrayList(counts));
    }

    private void showStartCountDialog() {
        Dialog<InventoryCountTemplate> dialog = new Dialog<>();
        dialog.setTitle("Start Inventory Count");

        ButtonType startButtonType = new ButtonType("Start", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(startButtonType, ButtonType.CANCEL);

        ComboBox<InventoryCountTemplate> templateBox = new ComboBox<>();
        templateBox.setItems(FXCollections.observableArrayList(getFilteredTemplates()));
        templateBox.setPrefWidth(350);

        templateBox.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(InventoryCountTemplate template, boolean empty) {
                super.updateItem(template, empty);
                setText(empty || template == null ? null : template.getName());
            }
        });

        templateBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(InventoryCountTemplate template, boolean empty) {
                super.updateItem(template, empty);
                setText(empty || template == null ? null : template.getName());
            }
        });

        DatePicker datePicker = new DatePicker(LocalDate.now());
        DatePicker periodStartPicker = new DatePicker(LocalDate.now().minusDays(6));
        DatePicker periodEndPicker = new DatePicker(LocalDate.now());

        TextArea notesArea = new TextArea();
        notesArea.setPromptText("Optional notes...");
        notesArea.setPrefRowCount(3);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setStyle("-fx-padding: 15;");

        grid.add(new Label("Template:"), 0, 0);
        grid.add(templateBox, 1, 0);

        grid.add(new Label("Count Date:"), 0, 1);
        grid.add(datePicker, 1, 1);

        grid.add(new Label("Period Start:"), 0, 2);
        grid.add(periodStartPicker, 1, 2);

        grid.add(new Label("Period End:"), 0, 3);
        grid.add(periodEndPicker, 1, 3);

        grid.add(new Label("Notes:"), 0, 4);
        grid.add(notesArea, 1, 4);

        dialog.getDialogPane().setContent(grid);

        Button startButton = (Button) dialog.getDialogPane().lookupButton(startButtonType);
        startButton.disableProperty().bind(templateBox.valueProperty().isNull());

        dialog.setResultConverter(button -> {
            if (button == startButtonType) {
                return templateBox.getSelectionModel().getSelectedItem();
            }
            return null;
        });

        dialog.showAndWait().ifPresent(template -> {
            int countId = countDao.createCount(
                    template.getId(),
                    datePicker.getValue().toString(),
                    periodStartPicker.getValue().toString(),
                    periodEndPicker.getValue().toString(),
                    notesArea.getText()
            );

            if (countId > 0) {
                countDao.createCountLinesFromTemplate(countId, template.getId());
                refreshTable();
            }
        });
    }

    private List<InventoryCountTemplate> getFilteredTemplates() {
        List<InventoryCountTemplate> templates = templateDao.findAllActive();

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

        return templates;
    }

    private void openSelectedCount() {
        InventoryCount selected =
                table.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showAlert(
                    Alert.AlertType.WARNING,
                    "No Count Selected",
                    "Please select a count."
            );
            return;
        }

        InventoryCountEntryView view =
                new InventoryCountEntryView(selected);

        Stage stage = new Stage();

        stage.setTitle(
                "Inventory Count - " +
                        selected.getTemplateName()
        );

        Scene scene = new Scene(
                view.getView(),
                1200,
                800
        );

        stage.setScene(scene);
        stage.show();
    }

    private void deleteSelectedCount() {
        InventoryCount selected = table.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showAlert(
                    Alert.AlertType.WARNING,
                    "No Count Selected",
                    "Please select a count to delete."
            );
            return;
        }

        TextInputDialog dialog = new TextInputDialog();

        dialog.setTitle("Delete Inventory Count");
        dialog.setHeaderText(
                "WARNING\n\n" +
                        "This will permanently delete:\n" +
                        "- The inventory count\n" +
                        "- All inventory count lines\n\n" +
                        "This may affect:\n" +
                        "- Weekly Cost Reports\n" +
                        "- Order Guides\n" +
                        "- Historical Inventory Records"
        );

        dialog.setContentText("Type DELETE to continue:");

        dialog.showAndWait().ifPresent(response -> {
            if (!"DELETE".equals(response.trim())) {
                showAlert(
                        Alert.AlertType.INFORMATION,
                        "Delete Cancelled",
                        "Delete operation cancelled.\n\n" +
                                "You must type DELETE exactly to remove a count."
                );
                return;
            }

            countDao.deleteCount(selected.getId());
            refreshTable();

            showAlert(
                    Alert.AlertType.INFORMATION,
                    "Count Deleted",
                    "Inventory count deleted successfully."
            );
        });
    }

    private void printSelectedCountSheet() {
        InventoryCount selected = table.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showAlert(
                    Alert.AlertType.WARNING,
                    "No Count Selected",
                    "Please select a count to print."
            );
            return;
        }

        List<InventoryCountLine> lines = lineDao.findByCount(selected.getId());

        PrinterJob job = PrinterJob.createPrinterJob();

        if (job != null && job.showPrintDialog(table.getScene().getWindow())) {
            int linesPerPage = 40;

            for (int i = 0; i < lines.size(); i += linesPerPage) {
                int end = Math.min(i + linesPerPage, lines.size());

                List<InventoryCountLine> pageLines = lines.subList(i, end);

                VBox printPage = buildCountSheetPrintLayout(selected, pageLines);

                boolean success = job.printPage(printPage);

                if (!success) {
                    break;
                }
            }

            job.endJob();
        }
    }

    private VBox buildCountSheetPrintLayout(
            InventoryCount count,
            List<InventoryCountLine> lines
    ) {
        VBox page = new VBox(3);
        page.setPadding(new Insets(15));
        page.setStyle("-fx-background-color: white;");

        Label title = new Label("INVENTORY COUNT SHEET");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: black;");

        Label details = new Label(
                count.getTemplateName() +
                        " | Date: " + count.getCountDate() +
                        " | Period: " + count.getPeriodStartDate() +
                        " to " + count.getPeriodEndDate()
        );
        details.setStyle("-fx-font-size: 8px; -fx-text-fill: black;");

        GridPane grid = new GridPane();
        grid.setHgap(0);
        grid.setVgap(0);
        grid.setStyle("-fx-border-color: black; -fx-border-width: 1;");

        addCountHeaderRow(grid);

        int rowIndex = 1;
        String currentSection = "";

        for (InventoryCountLine line : lines) {
            String section = line.getSectionName() == null ? "" : line.getSectionName();

            if (!section.equals(currentSection)) {
                currentSection = section;
                addSectionRow(grid, rowIndex++, currentSection, 4);
            }

            addCountLineRow(grid, rowIndex++, line);
        }

        page.getChildren().addAll(title, details, grid);

        return page;
    }

    private void addCountHeaderRow(GridPane grid) {
        addPrintCell(grid, "Product", 0, 0, 260, true);
        addPrintCell(grid, "Unit", 1, 0, 55, true);
        addPrintCell(grid, "Count", 2, 0, 80, true);
        addPrintCell(grid, "Notes", 3, 0, 120, true);
    }

    private void addCountLineRow(GridPane grid, int row, InventoryCountLine line) {
        addPrintCell(grid, line.getProductDescription(), 0, row, 260, false);
        addPrintCell(grid, line.getCountUnit(), 1, row, 55, false);
        addPrintCell(grid, "", 2, row, 80, false);
        addPrintCell(grid, "", 3, row, 120, false);
    }

    private void addSectionRow(GridPane grid, int row, String section, int columns) {
        Label label = new Label(section == null || section.isBlank() ? "Unassigned" : section);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setStyle(
                "-fx-font-weight: bold;" +
                        "-fx-text-fill: black;" +
                        "-fx-background-color: white;" +
                        "-fx-border-color: black;" +
                        "-fx-border-width: 0 0 1 0;" +
                        "-fx-padding: 4;"
        );

        grid.add(label, 0, row, columns, 1);
    }

    private void addPrintCell(
            GridPane grid,
            String text,
            int col,
            int row,
            double width,
            boolean header
    ) {
        Label label = new Label(text == null ? "" : text);
        label.setMinWidth(width);
        label.setPrefWidth(width);
        label.setMaxWidth(width);
        label.setMinHeight(16);
        label.setAlignment(Pos.CENTER_LEFT);
        label.setStyle(
                "-fx-text-fill: black;" +
                        "-fx-background-color: white;" +
                        "-fx-border-color: black;" +
                        "-fx-border-width: 0 1 1 0;" +
                        "-fx-padding: 1 3 1 3;" +
                        (header ? "-fx-font-weight: bold;" : "")
        );

        grid.add(label, col, row);
    }

    private String getTitleText() {
        if (department == null) {
            return "Inventory Counts";
        }

        return switch (department) {
            case "FOOD" -> "Food Inventory Counts";
            case "ALCOHOL" -> "Alcohol Inventory Counts";
            case "SUPPLIES" -> "Supplies Inventory Counts";
            default -> "Inventory Counts";
        };
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}