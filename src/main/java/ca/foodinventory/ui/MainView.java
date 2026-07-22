package ca.foodinventory.ui;

import ca.foodinventory.service.AppVersionService;
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
import javafx.scene.layout.VBox;
import ca.foodinventory.dao.SettingsDao;
import javafx.scene.control.*;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;

public class MainView {

    private final BorderPane root = new BorderPane();
    private final SettingsDao settingsDao = new SettingsDao();

    private final Deque<Node> navigationHistory = new ArrayDeque<>();
    private Button backButton;

    public BorderPane getView() {
        root.getStyleClass().add("root-dark");

        root.setLeft(buildSidebar());
        root.setBottom(buildStatusBar());
        showHomeScreen();

        return root;
    }

    private HBox buildStatusBar() {
        HBox statusBar = new HBox(10);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(6, 12, 6, 12));

        Label statusLabel = new Label("Ready");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label versionLabel = new Label(
                AppVersionService.getDisplayVersion()
        );

        statusBar.getChildren().addAll(
                statusLabel,
                spacer,
                versionLabel
        );

        return statusBar;
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

        menu.getChildren().addAll(
                logoBox,
                homeButton,
                backButton
        );

        return menu;
    }

    private void showHomeScreen() {
        navigationHistory.clear();

        VBox page = buildHomeScreen();
        showView(page, false);
    }

    private VBox buildHomeScreen() {

        VBox page = new VBox(25);
        page.setPadding(new Insets(40));
        page.setAlignment(Pos.TOP_CENTER);

        Label title = new Label("East Side Marios Operations Manager");
        title.getStyleClass().add("page-title");

        GridPane dashboard = new GridPane();
        dashboard.setHgap(20);
        dashboard.setVgap(20);
        dashboard.setAlignment(Pos.CENTER);

        Button foodButton =
                createDashboardButton("Food Inventory", this::showFoodMenu);

        Button alcoholButton =
                createDashboardButton("Alcohol Inventory", this::showAlcoholMenu);

        Button suppliesButton =
                createDashboardButton("Supplies Inventory", this::showSuppliesMenu);

        createDashboardButton(
                "Production Stations",
                () -> showView(new ProductionStationsView())
        );

        Button productionButton =
                createDashboardButton("Production", this::showProductionMenu);

        Button reportingButton =
                createDashboardButton("Reporting", this::showReportingMenu);

        Button systemButton =
                createDashboardButton("System", this::showSystemMenu);

        dashboard.add(foodButton, 0, 0);
        dashboard.add(alcoholButton, 1, 0);
        dashboard.add(suppliesButton, 0, 1);
        dashboard.add(productionButton, 1, 1);
        dashboard.add(reportingButton, 0, 2);
        dashboard.add(systemButton, 1, 2);

        page.getChildren().addAll(title, dashboard);

        return page;
    }

    private void showFoodMenu() {

        showSectionMenu(
                "Food Inventory",

                createDashboardButton(
                        "Products",
                        () -> showView(new ProductsView("FOOD").getView())
                ),

                createDashboardButton(
                        "Import Invoice",
                        () -> showView(new ImportInvoiceView().getView())
                ),

                createDashboardButton(
                        "Manual Invoice",
                        () -> showView(new ManualInvoiceView("FOOD").getView())
                ),

                createDashboardButton(
                        "Count Templates",
                        () -> showView(new InventoryCountTemplatesView("FOOD").getView())
                ),

                createDashboardButton(
                        "Inventory Counts",
                        () -> showView(new InventoryCountsView("FOOD").getView())
                ),

                createDashboardButton(
                        "Order Guide",
                        () -> showView(new OrderGuideView("FOOD").getView())
                )
        );
    }

    private void showAlcoholMenu() {

        showSectionMenu(
                "Alcohol Inventory",

                createDashboardButton(
                        "Manual Invoice",
                        () -> showView(new ManualInvoiceView("ALCOHOL").getView())
                ),

                createDashboardButton(
                        "Products",
                        () -> showView(new ProductsView("ALCOHOL").getView())
                ),

                createDashboardButton(
                        "Count Templates",
                        () -> showView(new InventoryCountTemplatesView("ALCOHOL").getView())
                ),

                createDashboardButton(
                        "Inventory Counts",
                        () -> showView(new InventoryCountsView("ALCOHOL").getView())
                ),

                createDashboardButton(
                        "Order Guide",
                        () -> showView(new OrderGuideView("ALCOHOL").getView())
                )
        );
    }

    private void showSuppliesMenu() {

        showSectionMenu(
                "Supplies Inventory",

                createDashboardButton(
                        "Manual Invoice",
                        () -> showView(new ManualInvoiceView("SUPPLIES").getView())
                ),

                createDashboardButton(
                        "Products",
                        () -> showView(new ProductsView("SUPPLIES").getView())
                ),

                createDashboardButton(
                        "Count Templates",
                        () -> showView(new InventoryCountTemplatesView("SUPPLIES").getView())
                ),

                createDashboardButton(
                        "Inventory Counts",
                        () -> showView(new InventoryCountsView("SUPPLIES").getView())
                ),

                createDashboardButton(
                        "Order Guide",
                        () -> showView(new OrderGuideView("SUPPLIES").getView())
                )
        );
    }

    private void showProductionMenu() {

        showSectionMenu(
                "Production",

                createDashboardButton(
                        "Production Items",
                        () -> showView(new ProductionItemsView())
                ),

                createDashboardButton(
                        "Production Stations",
                        () -> showView(new ProductionStationsView())
                ),

                createDashboardButton(
                        "POS Menu Items",
                        () -> showView(new PosMenuItemsView())
                ),

                createDashboardButton(
                        "Production Profiles",
                        () -> showView(new ProductionProfilesView())
                ),

                createDashboardButton(
                        "Product Mappings",
                        () -> showView(new ProductionItemProductMappingsView())
                ),

                createDashboardButton(
                        "Weekly Production",
                        () -> showView(new WeeklyProductionView())
                ),

                createDashboardButton(
                        "Freezer Pull",
                        () -> showView(new FreezerPullView())
                ),

                createDashboardButton(
                        "Variance Reports",
                        () -> showComingSoon("Variance Reports")
                )
        );
    }

    private void showReportingMenu() {

        showSectionMenu(
                "Reporting",

                createDashboardButton(
                        "Invoice History",
                        () -> showView(new InvoiceHistoryView().getView())
                ),

                createDashboardButton(
                        "Inventory",
                        () -> showView(new InventoryValuationView().getView())
                ),

                createDashboardButton(
                        "Sales",
                        () -> showView(new SalesEntryView().getView())
                )
        );
    }

    private void showSectionMenu(String titleText, Button... buttons) {

        VBox page = new VBox(25);
        page.setPadding(new Insets(40));
        page.setAlignment(Pos.TOP_CENTER);

        Label title = new Label(titleText);
        title.getStyleClass().add("page-title");

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(20);
        grid.setAlignment(Pos.CENTER);

        int row = 0;
        int col = 0;

        for (Button button : buttons) {

            grid.add(button, col, row);

            col++;

            if (col > 1) {
                col = 0;
                row++;
            }
        }

        page.getChildren().addAll(title, grid);

        showView(page);
    }

    private Button createMenuButton(String text, Runnable action) {

        Button button = new Button(text);

        button.getStyleClass().add("menu-button");

        button.setOnAction(e -> action.run());

        return button;
    }

    private Button createDashboardButton(String text, Runnable action) {

        Button button = new Button(text);

        button.getStyleClass().add("dashboard-button");

        button.setPrefWidth(300);
        button.setPrefHeight(150);

        button.setOnAction(e -> action.run());

        return button;
    }

    private void showComingSoon(String featureName) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(featureName);
        alert.setHeaderText(featureName);
        alert.setContentText(featureName + " will be added in a future Production module update.");
        alert.showAndWait();
    }

    private void showSystemMenu() {

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

                showView(new SystemView().getView());
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

            if (password.equals(settingsDao.getPassword())) {

                showView(new SystemView().getView());

            } else {

                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setHeaderText("Invalid Password");
                alert.showAndWait();
            }
        });
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
}
