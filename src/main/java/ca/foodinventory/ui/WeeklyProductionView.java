package ca.foodinventory.ui;

import ca.foodinventory.dao.ProductionWeekDao;
import ca.foodinventory.dao.ProductionItemDao;
import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.ImportedUsageReportSummary;
import ca.foodinventory.model.ProductionReportSummary;
import ca.foodinventory.model.ProductionWeek;
import ca.foodinventory.model.ProductionWeekDay;
import ca.foodinventory.model.ProductionWeekLine;
import ca.foodinventory.service.ProductionReportService;
import ca.foodinventory.service.ProductionUsageReportImportService;
import ca.foodinventory.service.ProductionApiClient;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.transform.Scale;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WeeklyProductionView extends BorderPane {

    private static final double DEFAULT_PAR_MULTIPLIER = 1.25;

    private final ProductionWeekDao productionWeekDao = new ProductionWeekDao();
    private final ProductionItemDao productionItemDao = new ProductionItemDao();
    private final ProductionApiClient productionApiClient = new ProductionApiClient();
    private final ProductionUsageReportImportService usageReportImportService =
            new ProductionUsageReportImportService();
    private final ProductionReportService productionReportService = new ProductionReportService();

    private final ComboBox<ProductionWeek> weekComboBox = new ComboBox<>();
    private final DatePicker weekStartDatePicker = new DatePicker();
    private final Spinner<Double> parMultiplierSpinner =
            new Spinner<>(0.0, 10.0, DEFAULT_PAR_MULTIPLIER, 0.05);
    private final CheckBox includeAllProductionItemsCheckBox =
            new CheckBox("Include all active production items");
    private final ComboBox<String> prepSheetComboBox = new ComboBox<>();
    private final Label summaryLabel = new Label("Select or import a production week.");
    private final TabPane dayTabs = new TabPane();

    public WeeklyProductionView() {
        getStyleClass().add("root-dark");
        buildLayout();
        loadWeeks();
    }

    private void buildLayout() {
        Label title = new Label("Weekly Production");
        title.getStyleClass().add("page-title");

        weekComboBox.setPromptText("Select production week");
        weekComboBox.setPrefWidth(300);
        weekComboBox.setCellFactory(listView -> weekCell());
        weekComboBox.setButtonCell(weekCell());
        weekComboBox.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldWeek, newWeek) -> loadWeek(newWeek)
        );

        weekStartDatePicker.setPromptText("Week start date");
        weekStartDatePicker.setValue(LocalDate.now());

        parMultiplierSpinner.setEditable(true);
        parMultiplierSpinner.setPrefWidth(100);
        includeAllProductionItemsCheckBox.setSelected(true);
        prepSheetComboBox.setPromptText("Prep sheet");
        prepSheetComboBox.setPrefWidth(150);
        prepSheetComboBox.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldPrepSheet, newPrepSheet) -> applyPrepSheetFilterToTabs()
        );

        Button importButton = new Button("Import Usage Report");
        configureToolbarButton(importButton);
        importButton.setOnAction(e -> importUsageReport());

        Button refreshButton = new Button("Refresh Week");
        configureToolbarButton(refreshButton);
        refreshButton.setOnAction(e -> refreshSelectedWeek());

        Button saveButton = new Button("Save Overrides");
        configureToolbarButton(saveButton);
        saveButton.setOnAction(e -> saveOverrides());

        Button savePermanentOverrideButton = new Button("Save Permanent Override");
        configureToolbarButton(savePermanentOverrideButton);
        savePermanentOverrideButton.setOnAction(e -> saveSelectedPermanentOverride());

        Button previewButton = new Button("Preview Prep Sheet");
        configureToolbarButton(previewButton);
        previewButton.setOnAction(e -> previewSelectedDay());

        Button printButton = new Button("Print Prep Sheet");
        configureToolbarButton(printButton);
        printButton.setOnAction(e -> printSelectedDay());

        Button printAllButton = new Button("Print All");
        configureToolbarButton(printAllButton);
        printAllButton.setOnAction(e -> printAllDays());

        FlowPane controls = new FlowPane(
                6,
                6,
                new Label("Week:"),
                weekComboBox,
                new Label("New Week Start:"),
                weekStartDatePicker,
                new Label("Par Multiplier:"),
                parMultiplierSpinner,
                includeAllProductionItemsCheckBox,
                importButton,
                refreshButton,
                saveButton,
                savePermanentOverrideButton,
                new Label("Prep Sheet:"),
                prepSheetComboBox,
                previewButton,
                printButton,
                printAllButton
        );
        controls.setAlignment(Pos.CENTER_LEFT);

        VBox top = new VBox(15, title, controls, summaryLabel);
        top.setPadding(new Insets(20));
        top.getStyleClass().add("content-area");

        dayTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        setTop(top);
        setCenter(dayTabs);
    }

    private void configureToolbarButton(Button button) {
        button.getStyleClass().add("primary-button");
        button.setStyle("-fx-font-size: 11px; -fx-padding: 4 8 4 8;");
    }

    private ListCell<ProductionWeek> weekCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(ProductionWeek week, boolean empty) {
                super.updateItem(week, empty);
                setText(empty || week == null ? null : formatWeek(week));
            }
        };
    }

    private void loadWeeks() {
        List<ProductionWeek> weeks = DatabaseManager.isApiDatabase()
                ? productionApiClient.findWeeks()
                : productionWeekDao.findAll();
        weekComboBox.setItems(FXCollections.observableArrayList(weeks));

        if (!weeks.isEmpty()) {
            weekComboBox.getSelectionModel().selectFirst();
        }
    }

    private void loadWeek(ProductionWeek week) {
        dayTabs.getTabs().clear();

        if (week == null) {
            summaryLabel.setText("Select or import a production week.");
            prepSheetComboBox.getItems().clear();
            parMultiplierSpinner.getValueFactory().setValue(DEFAULT_PAR_MULTIPLIER);
            return;
        }

        parMultiplierSpinner.getValueFactory().setValue(week.getParMultiplier());

        summaryLabel.setText("Loading " + formatWeek(week) + "...");
        dayTabs.setDisable(true);

        Task<WeekLoadData> task = new Task<>() {
            @Override
            protected WeekLoadData call() {
                List<ProductionWeekDay> days = DatabaseManager.isApiDatabase()
                        ? productionApiClient.findWeekDays(week.getId())
                        : productionWeekDao.findDaysByWeekId(week.getId());
                Map<Integer, List<ProductionWeekLine>> linesByDayId =
                        DatabaseManager.isApiDatabase()
                                ? productionApiClient.findWeekLinesByDayId(week.getId())
                                : productionWeekDao.findLinesByWeekId(week.getId());
                return new WeekLoadData(week, days, linesByDayId);
            }
        };

        task.setOnSucceeded(event -> {
            renderLoadedWeek(task.getValue());
            dayTabs.setDisable(false);
        });

        task.setOnFailed(event -> {
            dayTabs.setDisable(false);
            summaryLabel.setText("The selected production week could not be loaded.");
            showAlert(Alert.AlertType.ERROR, "Load Failed", task.getException().getMessage());
        });

        Thread thread = new Thread(task, "weekly-production-loader");
        thread.setDaemon(true);
        thread.start();
    }

    private void renderLoadedWeek(WeekLoadData data) {
        ProductionWeek week = data.week();
        List<String> prepSheets = new ArrayList<>();
        dayTabs.getTabs().clear();

        for (ProductionWeekDay day : data.days()) {
            TableView<ProductionWeekLine> table = buildDayTable();
            List<ProductionWeekLine> dayLines = data.linesByDayId().getOrDefault(day.getId(), List.of());

            for (ProductionWeekLine line : dayLines) {
                String prepSheet = getPrepSheetName(line);
                if (!prepSheets.contains(prepSheet)) {
                    prepSheets.add(prepSheet);
                }
            }

            table.setItems(FXCollections.observableArrayList(dayLines));

            Tab tab = new Tab(day.getDayName() + " " + day.getPrepDate(), table);
            tab.setUserData(new DayTabData(dayLines));
            dayTabs.getTabs().add(tab);
        }

        prepSheetComboBox.setItems(FXCollections.observableArrayList(prepSheets));
        if (!prepSheets.isEmpty()) {
            prepSheetComboBox.getSelectionModel().selectFirst();
            applyPrepSheetFilterToTabs();
        }

        summaryLabel.setText(
                "Showing " + formatWeek(week)
                        + " | Par multiplier: " + formatNumber(week.getParMultiplier())
        );
    }

    private TableView<ProductionWeekLine> buildDayTable() {
        TableView<ProductionWeekLine> table = new TableView<>();
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<ProductionWeekLine, String> stationCol = new TableColumn<>("Station");
        stationCol.setCellValueFactory(new PropertyValueFactory<>("stationName"));

        TableColumn<ProductionWeekLine, String> itemCol = new TableColumn<>("Production Item");
        itemCol.setCellValueFactory(new PropertyValueFactory<>("productionItemName"));
        itemCol.setPrefWidth(260);

        TableColumn<ProductionWeekLine, Double> salesQtyCol = new TableColumn<>("Prior Sales Qty");
        salesQtyCol.setCellValueFactory(new PropertyValueFactory<>("previousSalesQuantity"));
        salesQtyCol.setCellFactory(column -> numberCell());

        TableColumn<ProductionWeekLine, Integer> generatedParCol = new TableColumn<>("Generated Par");
        generatedParCol.setCellValueFactory(new PropertyValueFactory<>("generatedPar"));

        TableColumn<ProductionWeekLine, Integer> overrideParCol = new TableColumn<>("Override Par");
        overrideParCol.setCellValueFactory(new PropertyValueFactory<>("overridePar"));
        overrideParCol.setCellFactory(TextFieldTableCell.forTableColumn(nullableIntegerConverter()));
        overrideParCol.setEditable(true);
        overrideParCol.setOnEditCommit(event -> {
            ProductionWeekLine line = event.getRowValue();
            line.setOverridePar(event.getNewValue());
            table.refresh();
        });

        TableColumn<ProductionWeekLine, Integer> finalParCol = new TableColumn<>("Final Par");
        finalParCol.setCellValueFactory(new PropertyValueFactory<>("finalPar"));

        TableColumn<ProductionWeekLine, String> unitCol = new TableColumn<>("Unit");
        unitCol.setCellValueFactory(new PropertyValueFactory<>("unit"));

        table.getColumns().setAll(
                stationCol,
                itemCol,
                salesQtyCol,
                generatedParCol,
                overrideParCol,
                finalParCol,
                unitCol
        );

        return table;
    }

    private StringConverter<Integer> nullableIntegerConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(Integer value) {
                return value == null ? "" : String.valueOf(value);
            }

            @Override
            public Integer fromString(String value) {
                if (value == null || value.isBlank()) {
                    return null;
                }

                try {
                    int parsed = Integer.parseInt(value.trim());
                    return Math.max(parsed, 0);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        };
    }

    private TableCell<ProductionWeekLine, Double> numberCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : formatNumber(value));
            }
        };
    }

    private void importUsageReport() {
        LocalDate weekStartDate = weekStartDatePicker.getValue();

        if (weekStartDate == null) {
            showAlert(
                    Alert.AlertType.WARNING,
                    "Missing Week Start",
                    "Please select the production week start date."
            );
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Usage Report");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx")
        );

        File file = chooser.showOpenDialog(null);

        if (file == null) {
            return;
        }

        double parMultiplier = getCommittedParMultiplier();
        boolean includeAllProductionItems = includeAllProductionItemsCheckBox.isSelected();
        setDisable(true);
        summaryLabel.setText("Importing usage report...");

        Task<ProductionImportResult> task = new Task<>() {
            @Override
            protected ProductionImportResult call() {
                ImportedUsageReportSummary usageSummary = usageReportImportService.importUsageReport(file);
                ProductionReportSummary productionSummary =
                        productionReportService.generateReport(usageSummary);

                int productionWeekId = DatabaseManager.isApiDatabase()
                        ? productionApiClient.saveGeneratedWeek(
                        weekStartDate,
                        parMultiplier,
                        productionSummary,
                        includeAllProductionItems
                )
                        : productionWeekDao.saveGeneratedWeek(
                        weekStartDate,
                        parMultiplier,
                        productionSummary,
                        includeAllProductionItems
                );

                return new ProductionImportResult(
                        productionWeekId,
                        DatabaseManager.isApiDatabase()
                                ? productionApiClient.findWeeks()
                                : productionWeekDao.findAll()
                );
            }
        };

        task.setOnSucceeded(event -> {
            setDisable(false);
            ProductionImportResult result = task.getValue();
            weekComboBox.setItems(FXCollections.observableArrayList(result.weeks()));
            selectWeek(result.productionWeekId());

            showAlert(
                    Alert.AlertType.INFORMATION,
                    "Weekly Production Generated",
                    "Generated weekly production across separate daily sheets."
            );
        });

        task.setOnFailed(event -> {
            setDisable(false);
            showAlert(
                    Alert.AlertType.ERROR,
                    "Import Failed",
                    task.getException().getMessage()
            );
        });

        Thread thread = new Thread(task, "weekly-production-import");
        thread.setDaemon(true);
        thread.start();
    }

    private void saveOverrides() {
        List<ProductionWeekLine> lines = new ArrayList<>();

        for (Tab tab : dayTabs.getTabs()) {
            if (tab.getUserData() instanceof DayTabData dayTabData) {
                lines.addAll(dayTabData.lines());
            }
        }

        if (lines.isEmpty()) {
            showAlert(
                    Alert.AlertType.WARNING,
                    "Nothing To Save",
                    "Select or import a production week before saving overrides."
            );
            return;
        }

        try {
            if (DatabaseManager.isApiDatabase()) {
                productionApiClient.updateLineOverrides(lines);
            } else {
                productionWeekDao.updateLineOverrides(lines);
            }

            showAlert(
                    Alert.AlertType.INFORMATION,
                    "Overrides Saved",
                    "Saved override and final par values for the selected production week."
            );

        } catch (RuntimeException e) {
            showAlert(
                    Alert.AlertType.ERROR,
                    "Save Failed",
                    e.getMessage()
            );
        }
    }

    private void refreshSelectedWeek() {
        ProductionWeek selectedWeek = weekComboBox.getSelectionModel().getSelectedItem();

        if (selectedWeek == null) {
            showAlert(
                    Alert.AlertType.WARNING,
                    "No Week Selected",
                    "Select a production week before refreshing."
            );
            return;
        }

        try {
            int selectedWeekId = selectedWeek.getId();
            if (DatabaseManager.isApiDatabase()) {
                productionApiClient.refreshWeek(
                        selectedWeek,
                        getCommittedParMultiplier(),
                        includeAllProductionItemsCheckBox.isSelected()
                );
            } else {
                productionWeekDao.refreshWeekLines(
                        selectedWeek,
                        getCommittedParMultiplier(),
                        includeAllProductionItemsCheckBox.isSelected()
                );
            }
            loadWeeks();
            selectWeek(selectedWeekId);

            showAlert(
                    Alert.AlertType.INFORMATION,
                    "Week Refreshed",
                    "Updated the selected week from the current par multiplier, Production Item settings, and permanent overrides."
            );
        } catch (RuntimeException e) {
            showAlert(
                    Alert.AlertType.ERROR,
                    "Refresh Failed",
                    e.getMessage()
            );
        }
    }

    private double getCommittedParMultiplier() {
        String editorText = parMultiplierSpinner.getEditor().getText();
        StringConverter<Double> converter = parMultiplierSpinner.getValueFactory().getConverter();

        if (editorText != null && !editorText.isBlank() && converter != null) {
            try {
                Double value = converter.fromString(editorText);

                if (value != null) {
                    parMultiplierSpinner.getValueFactory().setValue(value);
                }
            } catch (RuntimeException ignored) {
                parMultiplierSpinner.getEditor().setText(
                        converter.toString(parMultiplierSpinner.getValue())
                );
            }
        }

        return parMultiplierSpinner.getValue();
    }

    private void saveSelectedPermanentOverride() {
        ProductionWeekLine selectedLine = getSelectedProductionWeekLine();

        if (selectedLine == null) {
            showAlert(
                    Alert.AlertType.WARNING,
                    "No Line Selected",
                    "Select a production item line before saving a permanent override."
            );
            return;
        }

        if (selectedLine.getOverridePar() == null) {
            showAlert(
                    Alert.AlertType.WARNING,
                    "No Override Par",
                    "Enter an Override Par for the selected line before saving it permanently."
            );
            return;
        }

        try {
            if (DatabaseManager.isApiDatabase()) {
                productionApiClient.updatePermanentOverridePar(
                        selectedLine.getProductionItemId(),
                        selectedLine.getOverridePar()
                );
            } else {
                productionItemDao.updatePermanentOverridePar(
                        selectedLine.getProductionItemId(),
                        selectedLine.getOverridePar()
                );
            }

            showAlert(
                    Alert.AlertType.INFORMATION,
                    "Permanent Override Saved",
                    "Saved " + selectedLine.getOverridePar()
                            + " as the permanent par for " + selectedLine.getProductionItemName() + "."
            );
        } catch (RuntimeException e) {
            showAlert(
                    Alert.AlertType.ERROR,
                    "Save Failed",
                    e.getMessage()
            );
        }
    }

    private ProductionWeekLine getSelectedProductionWeekLine() {
        Tab selectedTab = dayTabs.getSelectionModel().getSelectedItem();

        if (selectedTab == null || !(selectedTab.getContent() instanceof TableView<?> rawTable)) {
            return null;
        }

        Object selectedItem = rawTable.getSelectionModel().getSelectedItem();

        if (selectedItem instanceof ProductionWeekLine line) {
            return line;
        }

        return null;
    }


    private DaySheet getSelectedDaySheet() {
        ProductionWeek selectedWeek = weekComboBox.getSelectionModel().getSelectedItem();
        Tab selectedTab = dayTabs.getSelectionModel().getSelectedItem();

        if (selectedWeek == null
                || selectedTab == null
                || !(selectedTab.getContent() instanceof TableView<?> table)) {
            return null;
        }

        List<ProductionWeekLine> lines = new ArrayList<>();

        if (selectedTab.getUserData() instanceof DayTabData dayTabData) {
            lines.addAll(dayTabData.lines());
        } else {
            for (Object item : table.getItems()) {
                if (item instanceof ProductionWeekLine line) {
                    lines.add(line);
                }
            }
        }

        String selectedPrepSheet = getSelectedPrepSheet();

        return new DaySheet(
                selectedTab.getText(),
                formatWeek(selectedWeek),
                selectedPrepSheet,
                filterLinesByPrepSheet(lines, selectedPrepSheet)
        );
    }

    private List<DaySheet> getAllDaySheets() {
        ProductionWeek selectedWeek = weekComboBox.getSelectionModel().getSelectedItem();

        if (selectedWeek == null || dayTabs.getTabs().isEmpty()) {
            return List.of();
        }

        String selectedPrepSheet = getSelectedPrepSheet();
        List<DaySheet> daySheets = new ArrayList<>();

        for (Tab tab : dayTabs.getTabs()) {
            if (!(tab.getUserData() instanceof DayTabData dayTabData)) {
                continue;
            }

            daySheets.add(new DaySheet(
                    tab.getText(),
                    formatWeek(selectedWeek),
                    selectedPrepSheet,
                    filterLinesByPrepSheet(dayTabData.lines(), selectedPrepSheet)
            ));
        }

        return daySheets;
    }

    private void applyPrepSheetFilterToTabs() {
        String selectedPrepSheet = getSelectedPrepSheet();

        for (Tab tab : dayTabs.getTabs()) {
            if (!(tab.getContent() instanceof TableView<?> rawTable)
                    || !(tab.getUserData() instanceof DayTabData dayTabData)) {
                continue;
            }

            @SuppressWarnings("unchecked")
            TableView<ProductionWeekLine> table = (TableView<ProductionWeekLine>) rawTable;
            table.setItems(FXCollections.observableArrayList(
                    filterLinesByPrepSheet(dayTabData.lines(), selectedPrepSheet)
            ));
        }
    }

    private String getSelectedPrepSheet() {
        String selectedPrepSheet = prepSheetComboBox.getSelectionModel().getSelectedItem();

        if (selectedPrepSheet == null || selectedPrepSheet.isBlank()) {
            return "Main Line";
        }

        return selectedPrepSheet;
    }

    private List<ProductionWeekLine> filterLinesByPrepSheet(
            List<ProductionWeekLine> lines,
            String prepSheet
    ) {
        List<ProductionWeekLine> filtered = new ArrayList<>();

        for (ProductionWeekLine line : lines) {
            if (getPrepSheetName(line).equalsIgnoreCase(prepSheet)) {
                filtered.add(line);
            }
        }

        return filtered;
    }

    private String getPrepSheetName(ProductionWeekLine line) {
        String prepSheet = line.getPrepSheet();

        if (prepSheet == null || prepSheet.isBlank()) {
            return "Main Line";
        }

        return prepSheet;
    }

    private void previewSelectedDay() {
        DaySheet daySheet = getSelectedDaySheet();

        if (daySheet == null) {
            showAlert(
                    Alert.AlertType.WARNING,
                    "No Day Selected",
                    "Select a production day before previewing."
            );
            return;
        }

        VBox pages = new VBox(20);
        pages.setPadding(new Insets(16));
        pages.setStyle("-fx-background-color: #d0d0d0;");

        for (VBox page : buildPrintablePages(daySheet)) {
            pages.getChildren().add(page);
        }

        ScrollPane scrollPane = new ScrollPane(pages);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefSize(WindowSizing.width(900), WindowSizing.height(700));

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Daily Production Preview");
        dialog.setHeaderText(daySheet.title());
        dialog.getDialogPane().setContent(scrollPane);
        dialog.getDialogPane().getButtonTypes().addAll(
                new ButtonType("Print", ButtonBar.ButtonData.OK_DONE),
                ButtonType.CLOSE
        );
        dialog.setResultConverter(button -> {
            if (button != null && button.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                printDaySheet(daySheet);
            }

            return null;
        });
        dialog.showAndWait();
    }

    private void printSelectedDay() {
        DaySheet daySheet = getSelectedDaySheet();

        if (daySheet == null) {
            showAlert(
                    Alert.AlertType.WARNING,
                    "No Day Selected",
                    "Select a production day before printing."
            );
            return;
        }

        printDaySheet(daySheet);
    }

    private void printAllDays() {
        List<DaySheet> daySheets = getAllDaySheets();

        if (daySheets.isEmpty()) {
            showAlert(
                    Alert.AlertType.WARNING,
                    "No Week Selected",
                    "Select a production week before printing all prep sheets."
            );
            return;
        }

        printDaySheets(daySheets);
    }

    private void printDaySheet(DaySheet daySheet) {
        printDaySheets(List.of(daySheet));
    }

    private void printDaySheets(List<DaySheet> daySheets) {
        PrinterJob job = PrinterJob.createPrinterJob();

        if (job == null || !job.showPrintDialog(getScene().getWindow())) {
            return;
        }

        PageLayout pageLayout = createPrintPageLayout(job);
        boolean success = true;

        for (DaySheet daySheet : daySheets) {
            for (VBox page : buildPrintablePages(daySheet)) {
                success = success && job.printPage(pageLayout, createScaledPrintNode(page, pageLayout));
            }
        }

        if (success) {
            job.endJob();
        } else {
            showAlert(
                    Alert.AlertType.ERROR,
                    "Print Failed",
                    "The selected daily production list could not be printed."
            );
        }
    }

    private PageLayout createPrintPageLayout(PrinterJob job) {
        Printer printer = job.getPrinter();
        PageLayout pageLayout = printer.createPageLayout(
                Paper.NA_LETTER,
                PageOrientation.PORTRAIT,
                Printer.MarginType.HARDWARE_MINIMUM
        );
        job.getJobSettings().setPageLayout(pageLayout);
        return pageLayout;
    }

    private Node createScaledPrintNode(VBox page, PageLayout pageLayout) {
        page.applyCss();
        page.layout();

        double pageWidth = page.prefWidth(-1);
        double pageHeight = page.prefHeight(pageWidth);

        if (pageWidth <= 0) {
            pageWidth = page.getLayoutBounds().getWidth();
        }
        if (pageHeight <= 0) {
            pageHeight = page.getLayoutBounds().getHeight();
        }

        double scaleX = pageLayout.getPrintableWidth() / pageWidth;
        double scaleY = pageLayout.getPrintableHeight() / pageHeight;
        double scale = Math.min(scaleX, scaleY);

        Group scaledPage = new Group(page);
        scaledPage.getTransforms().add(new Scale(scale, scale));

        double scaledWidth = pageWidth * scale;
        double scaledHeight = pageHeight * scale;

        scaledPage.relocate(
                Math.max((pageLayout.getPrintableWidth() - scaledWidth) / 2, 0),
                Math.max((pageLayout.getPrintableHeight() - scaledHeight) / 2, 0)
        );

        Pane printRoot = new Pane(scaledPage);
        printRoot.setPrefSize(pageLayout.getPrintableWidth(), pageLayout.getPrintableHeight());
        printRoot.setMinSize(pageLayout.getPrintableWidth(), pageLayout.getPrintableHeight());
        printRoot.setMaxSize(pageLayout.getPrintableWidth(), pageLayout.getPrintableHeight());
        return printRoot;
    }

    private List<VBox> buildPrintablePages(DaySheet daySheet) {
        List<VBox> pages = new ArrayList<>();
        List<PrintableRow> rows = buildPrintableRows(daySheet.lines());

        VBox page = createPrintablePage(daySheet, 1);

        for (PrintableRow row : rows) {
            page.getChildren().add(row.stationHeader()
                    ? createStationHeader(row.text())
                    : createProductionLine(row.line(), getDayColumnTitle(daySheet.title())));
        }

        pages.add(page);
        return pages;
    }

    private List<PrintableRow> buildPrintableRows(List<ProductionWeekLine> lines) {
        Map<String, List<ProductionWeekLine>> linesByStation = new LinkedHashMap<>();

        for (ProductionWeekLine line : lines) {
            String stationName = line.getStationName();

            if (stationName == null || stationName.isBlank()) {
                stationName = "Unassigned";
            }

            linesByStation
                    .computeIfAbsent(stationName, ignored -> new ArrayList<>())
                    .add(line);
        }

        List<PrintableRow> rows = new ArrayList<>();

        for (Map.Entry<String, List<ProductionWeekLine>> entry : linesByStation.entrySet()) {
            rows.add(PrintableRow.station(entry.getKey()));

            for (ProductionWeekLine line : entry.getValue()) {
                rows.add(PrintableRow.line(line));
            }
        }

        return rows;
    }

    private VBox createPrintablePage(DaySheet daySheet, int pageNumber) {
        Label title = new Label(daySheet.prepSheet().toUpperCase() + " PREP");
        title.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");

        Label dayLabel = new Label(daySheet.title());
        dayLabel.setStyle("-fx-font-size: 8px; -fx-font-weight: bold;");

        Label weekLabel = new Label("Week: " + daySheet.weekLabel());
        weekLabel.setStyle("-fx-font-size: 6.5px;");

        Label pageLabel = new Label("Page " + pageNumber);
        pageLabel.setStyle("-fx-font-size: 6.5px;");

        VBox titleBlock = new VBox(0, title, dayLabel, weekLabel);
        HBox header = new HBox(18, titleBlock, pageLabel);
        header.setAlignment(Pos.TOP_LEFT);
        header.setMinHeight(36);
        header.setPrefHeight(36);
        header.setMaxHeight(36);

        GridPane columns = new GridPane();
        columns.setHgap(0);
        columns.setStyle("-fx-border-color: black; -fx-border-width: 1 0 1 0;");
        addHeaderCell(columns, "ITEM", 0, 182);
        addHeaderCell(columns, "UNIT", 1, 56);
        addHeaderCell(columns, "LIFE", 2, 48);
        addHeaderCell(columns, getDayColumnTitle(daySheet.title()), 3, 42);
        addHeaderCell(columns, "COUNT", 4, 50);
        addHeaderCell(columns, "TO DO", 5, 48);
        addHeaderCell(columns, "INITIAL", 6, 50);

        VBox page = new VBox(1, header, columns);
        page.setPrefWidth(500);
        page.setMinWidth(500);
        page.setMaxWidth(500);
        page.setPadding(new Insets(6));
        page.setStyle("-fx-background-color: white; -fx-text-fill: black;");

        return page;
    }

    private void addHeaderCell(GridPane grid, String text, int column, double width) {
        Label label = new Label(text);
        label.setPrefWidth(width);
        label.setMinHeight(12);
        label.setPrefHeight(12);
        label.setMaxHeight(12);
        label.setAlignment(Pos.CENTER);
        label.setStyle(
                "-fx-font-weight: bold;"
                        + "-fx-font-size: 5.8px;"
                        + "-fx-border-color: black;"
                        + "-fx-border-width: 0 1 0 0;"
                        + "-fx-padding: 0 1 0 1;"
        );
        grid.add(label, column, 0);
    }

    private Node createStationHeader(String stationName) {
        Label label = new Label(stationName);
        label.setPrefWidth(476);
        label.setMinHeight(10);
        label.setPrefHeight(10);
        label.setMaxHeight(10);
        label.setAlignment(Pos.CENTER);
        label.setStyle(
                "-fx-background-color: #e8e8e8;"
                        + "-fx-border-color: black;"
                        + "-fx-border-width: 1 0 1 0;"
                        + "-fx-font-size: 5.8px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-padding: 0 1 0 1;"
        );
        return label;
    }

    private Node createProductionLine(ProductionWeekLine line, String dayColumnTitle) {
        GridPane row = new GridPane();
        row.setHgap(0);
        row.setStyle("-fx-border-color: #bbbbbb; -fx-border-width: 0 0 1 0;");

        addLineCell(row, line.getProductionItemName(), 0, 182, Pos.CENTER_LEFT);
        addLineCell(row, line.getUnit(), 1, 56, Pos.CENTER);
        addLineCell(row, line.getShelfLife(), 2, 48, Pos.CENTER);
        addLineCell(row, String.valueOf(line.getFinalPar()), 3, 42, Pos.CENTER);
        addLineCell(row, "", 4, 50, Pos.CENTER);
        addLineCell(row, "", 5, 48, Pos.CENTER);
        addLineCell(row, "", 6, 50, Pos.CENTER);

        return row;
    }

    private void addLineCell(GridPane row, String text, int column, double width, Pos alignment) {
        Label label = new Label(text == null ? "" : text);
        label.setPrefWidth(width);
        label.setMinHeight(9.5);
        label.setPrefHeight(9.5);
        label.setMaxHeight(9.5);
        label.setAlignment(alignment);
        label.setStyle(
                "-fx-font-size: 5.5px;"
                        + "-fx-border-color: #bbbbbb;"
                        + "-fx-border-width: 0 1 0 0;"
                        + "-fx-padding: 0 1 0 1;"
        );
        row.add(label, column, 0);
    }

    private String getDayColumnTitle(String dayTitle) {
        if (dayTitle == null || dayTitle.isBlank()) {
            return "DAY";
        }

        String dayName = dayTitle.split("\\s+")[0].toUpperCase();

        return switch (dayName) {
            case "MONDAY" -> "MON";
            case "TUESDAY" -> "TUES";
            case "WEDNESDAY" -> "WED";
            case "THURSDAY" -> "THURS";
            case "FRIDAY" -> "FRIDAY";
            case "SATURDAY" -> "SAT";
            case "SUNDAY" -> "SUN";
            default -> dayName;
        };
    }

    private void selectWeek(int productionWeekId) {
        for (ProductionWeek week : weekComboBox.getItems()) {
            if (week.getId() == productionWeekId) {
                weekComboBox.getSelectionModel().select(week);
                return;
            }
        }
    }

    private String formatWeek(ProductionWeek week) {
        return week.getWeekStartDate() + " to " + week.getWeekEndDate();
    }

    private String formatNumber(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }

        return String.format("%.2f", value);
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private record DaySheet(
            String title,
            String weekLabel,
            String prepSheet,
            List<ProductionWeekLine> lines
    ) {
    }

    private record DayTabData(List<ProductionWeekLine> lines) {
    }

    private record WeekLoadData(
            ProductionWeek week,
            List<ProductionWeekDay> days,
            Map<Integer, List<ProductionWeekLine>> linesByDayId
    ) {
    }

    private record ProductionImportResult(
            int productionWeekId,
            List<ProductionWeek> weeks
    ) {
    }

    private record PrintableRow(String text, ProductionWeekLine line, boolean stationHeader) {

        private static PrintableRow station(String stationName) {
            return new PrintableRow(stationName, null, true);
        }

        private static PrintableRow line(ProductionWeekLine line) {
            return new PrintableRow(null, line, false);
        }
    }
}
