package ca.foodinventory.ui;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.service.AppVersionService;
import ca.foodinventory.service.ProductApiClient;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import ca.foodinventory.dao.SettingsDao;
import javafx.scene.control.*;

import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

public class MainView {

    private final BorderPane root = new BorderPane();
    private final SettingsDao settingsDao = new SettingsDao();
    private final ProductApiClient productApiClient = new ProductApiClient();
    private final Runnable switchStoreAction;

    private final Deque<Node> navigationHistory = new ArrayDeque<>();
    private final Circle databaseStatusDot = new Circle(6);
    private final Label databaseStatusLabel = new Label("Database: Checking...");
    private Button backButton;

    public MainView() {
        this(null);
    }

    public MainView(Runnable switchStoreAction) {
        this.switchStoreAction = switchStoreAction;
    }

    public BorderPane getView() {
        root.getStyleClass().add("root-dark");

        root.setLeft(buildSidebar());
        root.setBottom(buildStatusBar());
        showHomeScreen();
        refreshDatabaseConnectionStatus();

        return root;
    }

    private HBox buildStatusBar() {
        HBox statusBar = new HBox(10);
        statusBar.getStyleClass().add("status-bar");
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(6, 12, 6, 12));

        Label statusLabel = new Label("Ready");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        databaseStatusDot.getStyleClass().add("database-status-checking");
        databaseStatusLabel.getStyleClass().add("database-status-label");
        HBox databaseStatus = new HBox(7, databaseStatusDot, databaseStatusLabel);
        databaseStatus.setAlignment(Pos.CENTER_LEFT);

        Label versionLabel = new Label(
                AppVersionService.getDisplayVersion()
        );

        statusBar.getChildren().addAll(
                statusLabel,
                spacer,
                databaseStatus,
                versionLabel
        );

        return statusBar;
    }

    private void refreshDatabaseConnectionStatus() {
        setDatabaseStatus("database-status-checking", "Database: Checking...");

        Task<DatabaseStatus> task = new Task<>() {
            @Override
            protected DatabaseStatus call() throws Exception {
                return checkDatabaseConnection();
            }
        };

        task.setOnSucceeded(event -> {
            DatabaseStatus status = task.getValue();
            setDatabaseStatus(
                    status.connected()
                            ? "database-status-connected"
                            : "database-status-disconnected",
                    status.message()
            );
        });

        task.setOnFailed(event -> setDatabaseStatus(
                "database-status-disconnected",
                "Database: Not connected"
        ));

        Thread thread = new Thread(task, "database-status-check");
        thread.setDaemon(true);
        thread.start();
    }

    private DatabaseStatus checkDatabaseConnection() throws Exception {
        if (DatabaseManager.isApiDatabase()) {
            productApiClient.findAllActiveProducts();
            return new DatabaseStatus(true, "Database: Cloud API connected");
        }

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1");
             ResultSet ignored = statement.executeQuery()) {

            return new DatabaseStatus(
                    true,
                    "Database: " + DatabaseManager.getActiveDatabaseModeLabel() + " connected"
            );
        }
    }

    private void setDatabaseStatus(String styleClass, String message) {
        databaseStatusDot.getStyleClass().removeAll(
                "database-status-checking",
                "database-status-connected",
                "database-status-disconnected"
        );
        databaseStatusDot.getStyleClass().add(styleClass);
        databaseStatusLabel.setText(message);
    }

    private VBox buildSidebar() {
        VBox menu = new VBox(10);
        menu.getStyleClass().add("side-menu");

        InputStream logoStream = getClass().getResourceAsStream("/images/logo.png");

        ImageView logo = new ImageView();

        if (logoStream != null) {
            logo.setImage(new Image(logoStream));
            logo.setFitWidth(110);
            logo.setPreserveRatio(true);
            logo.setSmooth(true);
        }

        VBox logoBox = new VBox(logo);
        logoBox.setAlignment(Pos.CENTER);
        logoBox.setPadding(new Insets(0, 0, 15, 0));

        Button homeButton = createMenuButton("Home", this::goHome);

        backButton = createMenuButton("Back", this::goBack);
        backButton.setDisable(true);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        menu.getChildren().addAll(
                logoBox,
                homeButton,
                backButton,
                spacer
        );

        if (switchStoreAction != null && DatabaseManager.isLocationLoginRequired()) {
            Button switchStoreButton = createMenuButton("Switch Store", this::confirmSwitchStore);
            switchStoreButton.getStyleClass().add("menu-secondary-button");
            menu.getChildren().add(switchStoreButton);
        }

        return menu;
    }

    private void showHomeScreen() {
        navigationHistory.clear();

        VBox page = buildHomeScreen();
        showView(createDashboardScrollPane(page), false);
    }

    private VBox buildHomeScreen() {

        VBox page = new VBox(24);
        page.getStyleClass().add("dashboard-home");
        page.setPadding(new Insets(34, 42, 34, 42));
        page.setAlignment(Pos.TOP_LEFT);

        Label title = new Label("StoreOps Manager");
        title.getStyleClass().add("page-title");

        Label subtitle = new Label(
                "Back-office workflows for inventory, purchasing, production, reporting, and labour."
        );
        subtitle.getStyleClass().add("dashboard-subtitle");
        subtitle.setWrapText(true);

        HBox statusRow = new HBox(
                10,
                createStatusPill("Version " + AppVersionService.getDisplayVersion()),
                createStatusPill(DatabaseManager.getActiveDatabaseModeLabel()),
                createStatusPill(getLocationStatusText())
        );
        statusRow.getStyleClass().add("dashboard-status-row");
        statusRow.setAlignment(Pos.CENTER_LEFT);

        page.getChildren().addAll(
                title,
                subtitle,
                statusRow,
                createDashboardSection(
                        "Inventory & Purchasing",
                        createDashboardCard(
                                "Food Department",
                                "Products, invoices, counts, valuation, and order guides.",
                                this::showFoodMenu
                        ),
                        createDashboardCard(
                                "Alcohol Department",
                                "Inventory, invoices, profiles, sales mappings, and variance.",
                                this::showAlcoholMenu
                        ),
                        createDashboardCard(
                                "Supplies Department",
                                "Supplies products, purchasing, counts, and ordering.",
                                this::showSuppliesMenu
                        )
                ),
                createDashboardSection(
                        "Operations Planning",
                        createDashboardCard(
                                "Production",
                                "Prep planning, POS usage imports, freezer pull, and setup.",
                                this::showProductionMenu
                        ),
                        createDashboardCard(
                                "Reporting",
                                "Sales entry, invoice history, valuation, and weekly cost reports.",
                                this::showReportingMenu
                        )
                ),
                createDashboardSection(
                        "Labour",
                        createDashboardCard(
                                "Labour Management",
                                "Hours, daily labour cost, tip pool, and payout breakdowns.",
                                this::showLabourMenu
                        )
                ),
                createDashboardSection(
                        "Administration",
                        createDashboardCard(
                                "System",
                                "Backup, restore, update checks, and administrator tools.",
                                this::showSystemMenu
                        ),
                        createDashboardCard(
                                "About",
                                "Version, database mode, and signed-in store details.",
                                this::showAboutDialog
                        )
                )
        );

        return page;
    }

    private Label createStatusPill(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("dashboard-status-pill");
        return label;
    }

    private VBox createDashboardSection(String titleText, Button... cards) {
        Label title = new Label(titleText);
        title.getStyleClass().add("dashboard-section-title");

        TilePane tilePane = new TilePane();
        tilePane.getStyleClass().add("dashboard-card-grid");
        tilePane.setHgap(14);
        tilePane.setVgap(14);
        tilePane.setPrefColumns(3);
        tilePane.getChildren().addAll(cards);

        VBox section = new VBox(10, title, tilePane);
        section.getStyleClass().add("dashboard-section");
        section.setMaxWidth(Double.MAX_VALUE);
        return section;
    }

    private Button createDashboardCard(String titleText, String descriptionText, Runnable action) {
        Label title = new Label(titleText);
        title.getStyleClass().add("dashboard-card-title");
        title.setWrapText(true);

        Label description = new Label(descriptionText);
        description.getStyleClass().add("dashboard-card-description");
        description.setWrapText(true);

        VBox content = new VBox(8, title, description);
        content.setAlignment(Pos.TOP_LEFT);
        content.setMouseTransparent(true);

        Button button = new Button();
        button.getStyleClass().add("dashboard-card");
        button.setGraphic(content);
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        button.setPrefSize(280, 118);
        button.setMinSize(260, 112);
        button.setMaxSize(Double.MAX_VALUE, 126);
        button.setOnAction(e -> runUiAction(titleText, action));
        return button;
    }

    private String getLocationStatusText() {
        String locationName = DatabaseManager.getConfiguredLocationName();
        if (locationName == null || locationName.isBlank()) {
            return "Store not signed in";
        }
        return "Store: " + locationName;
    }

    private void showFoodMenu() {

        showSectionMenu(
                "Food Department",

                createDashboardCard(
                        "Products",
                        "Maintain active products, pack sizes, units, categories, and costs.",
                        () -> showView(new ProductsView("FOOD").getView())
                ),

                createDashboardCard(
                        "Import Invoice",
                        "Import supplier invoices, review lines, and update product costs.",
                        () -> showView(new ImportInvoiceView().getView())
                ),

                createDashboardCard(
                        "Manual Invoice",
                        "Enter purchases manually when an import file is not available.",
                        () -> showView(new ManualInvoiceView("FOOD").getView())
                ),

                createDashboardCard(
                        "Count Templates",
                        "Build and maintain count sheets by section and product order.",
                        () -> showView(new InventoryCountTemplatesView("FOOD").getView())
                ),

                createDashboardCard(
                        "Inventory Counts",
                        "Start counts, enter quantities, complete counts, and print sheets.",
                        () -> showView(new InventoryCountsView("FOOD").getView())
                ),

                createDashboardCard(
                        "Order Guide",
                        "Generate ordering needs from the current inventory cycle.",
                        () -> showView(new OrderGuideView("FOOD").getView())
                )
        );
    }

    private void showAlcoholMenu() {

        showSectionMenu(
                "Alcohol Department",

                createDashboardCard(
                        "Manual Invoice",
                        "Enter alcohol invoices with HST, deposits, and adjustments.",
                        () -> showView(new AlcoholManualInvoiceView().getView())
                ),

                createDashboardCard(
                        "Products",
                        "Maintain alcohol products, profiles, costs, and count settings.",
                        () -> showView(new ProductsView("ALCOHOL").getView())
                ),

                createDashboardCard(
                        "Count Templates",
                        "Organize alcohol count sheets by area, shelf, and product.",
                        () -> showView(new InventoryCountTemplatesView("ALCOHOL").getView())
                ),

                createDashboardCard(
                        "Inventory Counts",
                        "Enter bottle, keg, weighted, and standard alcohol counts.",
                        () -> showView(new InventoryCountsView("ALCOHOL").getView())
                ),

                createDashboardCard(
                        "Order Guide",
                        "Build alcohol order guides from completed inventory counts.",
                        () -> showView(new OrderGuideView("ALCOHOL").getView())
                ),

                createDashboardCard(
                        "Sales Mappings",
                        "Map POS sales items to inventory usage for variance reporting.",
                        () -> showView(new AlcoholSalesMappingsView())
                ),

                createDashboardCard(
                        "Variance Report",
                        "Compare actual alcohol usage against mapped POS sales usage.",
                        () -> showView(new AlcoholVarianceReportView().getView())
                )
        );
    }

    private void showSuppliesMenu() {

        showSectionMenu(
                "Supplies Department",

                createDashboardCard(
                        "Manual Invoice",
                        "Enter supplies invoices and update product purchase costs.",
                        () -> showView(new ManualInvoiceView("SUPPLIES").getView())
                ),

                createDashboardCard(
                        "Products",
                        "Maintain supplies products, categories, units, and costs.",
                        () -> showView(new ProductsView("SUPPLIES").getView())
                ),

                createDashboardCard(
                        "Count Templates",
                        "Build reusable count sheets for supplies categories.",
                        () -> showView(new InventoryCountTemplatesView("SUPPLIES").getView())
                ),

                createDashboardCard(
                        "Inventory Counts",
                        "Enter supplies counts and complete count periods.",
                        () -> showView(new InventoryCountsView("SUPPLIES").getView())
                ),

                createDashboardCard(
                        "Order Guide",
                        "Generate supplies ordering needs from current counts.",
                        () -> showView(new OrderGuideView("SUPPLIES").getView())
                )
        );
    }

    private void showProductionMenu() {

        showSectionMenu(
                "Production",

                createDashboardCard(
                        "Production Items",
                        "Maintain prep items, units, stations, and yield factors.",
                        () -> showView(new ProductionItemsView())
                ),

                createDashboardCard(
                        "Production Stations",
                        "Organize prep work by station for clearer production sheets.",
                        () -> showView(new ProductionStationsView())
                ),

                createDashboardCard(
                        "POS Menu Items",
                        "Import and maintain POS items used for production planning.",
                        () -> showView(new PosMenuItemsView())
                ),

                createDashboardCard(
                        "Production Profiles",
                        "Map sold items to prep requirements and quantities.",
                        () -> showView(new ProductionProfilesView())
                ),

                createDashboardCard(
                        "Product Mappings",
                        "Connect production items to inventory products for usage reporting.",
                        () -> showView(new ProductionItemProductMappingsView())
                ),

                createDashboardCard(
                        "Weekly Production",
                        "Generate and review weekly prep from imported POS usage.",
                        () -> showView(new WeeklyProductionView())
                ),

                createDashboardCard(
                        "Freezer Pull",
                        "Plan freezer pull quantities across the operating week.",
                        () -> showView(new FreezerPullView())
                )
        );
    }

    private void showLabourMenu() {

        showSectionMenu(
                "Labour Management",

                createDashboardCard(
                        "Labour Hours",
                        "Open saved weeks and enter shift hours in a spreadsheet layout.",
                        () -> showView(new LabourHoursView())
                ),

                createDashboardCard(
                        "Daily Labour Cost",
                        "Track daily net sales, labour dollars, and labour percentages.",
                        () -> showView(new DailyLabourView())
                ),

                createDashboardCard(
                        "Tip Pool",
                        "Allocate daily tip pool amounts from eligible employee hours.",
                        () -> showView(new TipPoolView())
                ),

                createDashboardCard(
                        "Tip Pool Breakdown",
                        "Calculate payout totals, deductions, and printable summaries.",
                        () -> showView(new TipPoolBreakdownView())
                ),

                createDashboardCard(
                        "Labour Setup",
                        "Configure positions, employees, wages, targets, and deductions.",
                        () -> showAdminProtectedView(
                                "Labour Setup",
                                () -> new LabourSetupView()
                        )
                )
        );
    }

    private void showReportingMenu() {

        showSectionMenu(
                "Reporting",

                createDashboardCard(
                        "Invoice History",
                        "Review saved invoices, category breakdowns, and purchase history.",
                        () -> showView(new InvoiceHistoryView().getView())
                ),

                createDashboardCard(
                        "Inventory Valuation",
                        "Value completed counts by department, category, and period.",
                        () -> showView(new InventoryValuationView().getView())
                ),

                createDashboardCard(
                        "Sales",
                        "Import POS sales reports and maintain reporting-period totals.",
                        () -> showView(new SalesEntryView().getView())
                )
        );
    }

    private void showSectionMenu(String titleText, Button... buttons) {

        VBox page = new VBox(20);
        page.getStyleClass().add("dashboard-home");
        page.setPadding(new Insets(34, 42, 34, 42));
        page.setAlignment(Pos.TOP_LEFT);

        Label title = new Label(titleText);
        title.getStyleClass().add("page-title");

        Label subtitle = new Label(sectionSubtitle(titleText));
        subtitle.getStyleClass().add("dashboard-subtitle");
        subtitle.setWrapText(true);

        TilePane grid = new TilePane();
        grid.getStyleClass().add("dashboard-card-grid");
        grid.setHgap(14);
        grid.setVgap(14);
        grid.setPrefColumns(3);
        grid.getChildren().addAll(buttons);

        page.getChildren().addAll(title, subtitle, grid);

        showView(createDashboardScrollPane(page));
    }

    private ScrollPane createDashboardScrollPane(Node content) {
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.getStyleClass().add("dashboard-home-scroll");
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        return scrollPane;
    }

    private String sectionSubtitle(String titleText) {
        return switch (titleText) {
            case "Food Department" -> "Manage food products, invoices, inventory counts, valuation, and ordering.";
            case "Alcohol Department" -> "Manage alcohol purchasing, inventory, profiles, order guides, and variance.";
            case "Supplies Department" -> "Manage supplies purchasing, counts, products, and order guides.";
            case "Production" -> "Maintain production setup and generate prep planning from POS usage.";
            case "Labour Management" -> "Track hours, labour cost, tip pool allocation, and payout breakdowns.";
            case "Reporting" -> "Review invoices, import sales, and produce cost-control reporting.";
            default -> "Choose a workflow to continue.";
        };
    }

    private Button createMenuButton(String text, Runnable action) {

        Button button = new Button(text);

        button.getStyleClass().add("menu-button");

        button.setOnAction(e -> runUiAction(text, action));

        return button;
    }

    private Button createDashboardButton(String text, Runnable action) {

        Button button = new Button(text);

        button.getStyleClass().add("dashboard-button");

        button.setPrefWidth(300);
        button.setPrefHeight(150);

        button.setOnAction(e -> runUiAction(text, action));

        return button;
    }

    private void runUiAction(String actionName, Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            ex.printStackTrace();

            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(actionName);
            alert.setHeaderText("Unable to open " + actionName);
            alert.setContentText(getRootCauseMessage(ex));

            TextArea details = new TextArea(getStackTrace(ex));
            details.setEditable(false);
            details.setWrapText(false);
            details.setMaxWidth(Double.MAX_VALUE);
            details.setMaxHeight(Double.MAX_VALUE);
            GridPane.setVgrow(details, Priority.ALWAYS);
            GridPane.setHgrow(details, Priority.ALWAYS);

            GridPane expandableContent = new GridPane();
            expandableContent.setMaxWidth(Double.MAX_VALUE);
            expandableContent.add(new Label("Details:"), 0, 0);
            expandableContent.add(details, 0, 1);

            alert.getDialogPane().setExpandableContent(expandableContent);
            alert.getDialogPane().setExpanded(true);
            alert.getDialogPane().setPrefWidth(760);
            alert.showAndWait();
        }
    }

    private String getRootCauseMessage(Throwable throwable) {
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }

        String message = rootCause.getMessage();
        if (message == null || message.isBlank()) {
            return rootCause.getClass().getSimpleName();
        }

        return rootCause.getClass().getSimpleName() + ": " + message;
    }

    private String getStackTrace(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private void showComingSoon(String featureName) {
        showComingSoon(featureName, "Operations Manager");
    }

    private void showComingSoon(String featureName, String areaName) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(featureName);
        alert.setHeaderText(featureName);
        alert.setContentText(featureName + " will be added in a future " + areaName + " update.");
        alert.showAndWait();
    }

    private void showAboutDialog() {
        Dialog<Void> dialog = new Dialog<>();
        if (root.getScene() != null && root.getScene().getWindow() != null) {
            dialog.initOwner(root.getScene().getWindow());
        }
        dialog.setTitle("About StoreOps Manager");
        applyDialogStyles(dialog.getDialogPane());
        dialog.setHeaderText("StoreOps Manager");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        GridPane details = new GridPane();
        details.setHgap(16);
        details.setVgap(10);
        details.setPadding(new Insets(8, 0, 0, 0));

        addAboutRow(details, 0, "Version", AppVersionService.getDisplayVersion());
        addAboutRow(details, 1, "Database", DatabaseManager.getActiveDatabaseModeLabel());
        addAboutRow(details, 2, "Store", getLocationStatusText().replace("Store: ", ""));
        addAboutRow(details, 3, "Scope", "Inventory, purchasing, production, reporting, and labour operations");

        Label note = new Label(
                "Built to standardize recurring back-office workflows and reduce manual spreadsheet work."
        );
        note.setWrapText(true);
        note.setMaxWidth(420);
        note.setStyle("-fx-text-fill: #202020;");

        VBox content = new VBox(14, details, note);
        content.setPrefWidth(WindowSizing.width(460));
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    private void confirmSwitchStore() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        if (root.getScene() != null && root.getScene().getWindow() != null) {
            alert.initOwner(root.getScene().getWindow());
        }
        alert.setTitle("Switch Store");
        applyDialogStyles(alert.getDialogPane());
        alert.setHeaderText("Switch to a different store?");
        alert.setContentText("You will return to Store Login. Unsaved work on the current screen should be saved first.");

        ButtonType switchButton = new ButtonType("Switch Store", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(switchButton, cancelButton);

        if (alert.showAndWait().orElse(cancelButton) == switchButton) {
            switchStoreAction.run();
        }
    }

    private void applyDialogStyles(DialogPane pane) {
        String stylesheet = getClass().getResource("/style.css") == null
                ? null
                : getClass().getResource("/style.css").toExternalForm();
        if (stylesheet != null && !pane.getStylesheets().contains(stylesheet)) {
            pane.getStylesheets().add(stylesheet);
        }
        if (!pane.getStyleClass().contains("standard-dialog")) {
            pane.getStyleClass().add("standard-dialog");
        }
    }

    private void addAboutRow(GridPane details, int row, String labelText, String valueText) {
        Label label = new Label(labelText + ":");
        label.setStyle("-fx-text-fill: #202020; -fx-font-weight: bold;");

        Label value = new Label(valueText == null || valueText.isBlank() ? "Not configured" : valueText);
        value.setWrapText(true);
        value.setStyle("-fx-text-fill: #202020;");

        details.add(label, 0, row);
        details.add(value, 1, row);
    }

    private void showSystemMenu() {
        showAdminProtectedView("System", () -> new SystemView().getView());
    }

    private void showAdminProtectedView(String viewName, Supplier<Node> protectedViewSupplier) {

        if (!settingsDao.isPasswordInitialized()) {

            TextInputDialog dialog = new TextInputDialog();

            dialog.setTitle("Create Administrator Password");
            dialog.setHeaderText("First Time Setup");
            dialog.setContentText("Create Password:");

            dialog.showAndWait().ifPresent(password -> {

                if (password.isBlank()) {

                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setHeaderText("Password cannot be blank");
                    alert.showAndWait();
                    return;
                }

                settingsDao.setPassword(password);

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setHeaderText("Password Created");
                alert.showAndWait();

                showView(protectedViewSupplier.get());
            });

            return;
        }

        PasswordField passwordField = new PasswordField();

        Dialog<String> dialog = new Dialog<>();

        dialog.setTitle("Administrator Login");

        ButtonType loginButton =
                new ButtonType("Login", ButtonBar.ButtonData.OK_DONE);

        dialog.getDialogPane().getButtonTypes().addAll(
                loginButton,
                ButtonType.CANCEL
        );

        VBox box = new VBox(10);

        box.getChildren().addAll(
                new Label("Password"),
                passwordField
        );

        dialog.getDialogPane().setContent(box);

        dialog.setResultConverter(button -> {

            if (button == loginButton) {
                return passwordField.getText();
            }

            return null;
        });

        dialog.showAndWait().ifPresent(password -> {

            if (settingsDao.verifyPassword(password)) {

                showView(protectedViewSupplier.get());

            } else {

                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setHeaderText("Invalid Password");
                alert.showAndWait();
            }
        });
    }

    private VBox buildPlaceholderView(String titleText, String messageText) {
        VBox page = new VBox(18);
        page.setPadding(new Insets(40));
        page.setAlignment(Pos.TOP_CENTER);

        Label title = new Label(titleText);
        title.getStyleClass().add("page-title");

        Label message = new Label(messageText);
        message.getStyleClass().add("section-title");

        page.getChildren().addAll(title, message);
        return page;
    }

    private void goHome() {
        navigationHistory.clear();
        showView(buildHomeScreen(), false);
    }

    private void goBack() {
        if (navigationHistory.isEmpty()) {
            updateBackButton();
            return;
        }

        Node previousView = navigationHistory.pop();
        root.setCenter(previousView);
        updateBackButton();
    }

    private void showView(Node view) {
        showView(view, true);
    }

    private void showView(Node view, boolean rememberCurrentView) {
        Node currentView = root.getCenter();

        if (rememberCurrentView && currentView != null) {
            navigationHistory.push(currentView);
        }

        root.setCenter(view);
        updateBackButton();
    }

    private void updateBackButton() {
        if (backButton != null) {
            backButton.setDisable(navigationHistory.isEmpty());
        }
    }

    private record DatabaseStatus(boolean connected, String message) {
    }
}
