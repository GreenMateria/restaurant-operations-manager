package ca.foodinventory.ui;

import ca.foodinventory.dao.ProductDao;
import ca.foodinventory.model.AlcoholProductProfile;
import ca.foodinventory.model.Product;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.Node;

import java.math.BigDecimal;

public class ProductEditorView {

    private static final String[] CATEGORIES = {
            "MEAT", "POULTRY", "SEAFOOD", "DAIRY/CHEESE", "SOUP/DOUGH",
            "PASTA", "PASTA SAUCES", "FRIES/CANNED", "OILS",
            "SAUCE/DRESSINGS", "SPICES", "BEVERAGES", "PRODUCE",
            "BAR MIX", "PROMO", "Uncategorized"
    };

    private static final String[] UNITS = {
            "EA", "KG", "LB", "L", "ML", "CASE", "BAG", "BOX", "GR"
    };

    private final ProductDao productDao = new ProductDao();
    private final Product product;
    private final Runnable onSave;

    private TextField skuField;
    private TextField descriptionField;
    private ComboBox<String> categoryBox;
    private ComboBox<String> reportingCategoryBox;
    private ComboBox<String> unitBox;
    private TextField conversionFactorField;
    private TextField packSizeField;
    private TextField packCountField;
    private TextField lastCaseCostField;

    private VBox alcoholSection;
    private ToggleGroup countMethodGroup;
    private RadioButton weightRadio;
    private RadioButton eachRadio;
    private ComboBox<ContainerOption> containerTypeBox;
    private TextField tareWeightField;
    private Label containerTypeLabel;
    private Label tareWeightLabel;
    private String pendingContainerTypeCode;

    public ProductEditorView(Product product, Runnable onSave) {
        this.product = product;
        this.onSave = onSave;
    }

    public void show() {
        Stage stage = new Stage();
        boolean isNew = product == null || product.getId() == 0;

        stage.setTitle(isNew ? "Add Product" : "Edit Product");
        stage.initModality(Modality.APPLICATION_MODAL);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-dark");

        Label title = new Label(isNew ? "Add Product" : "Edit Product");
        title.getStyleClass().add("page-title");

        VBox content = new VBox(18);
        content.setPadding(new Insets(20));
        content.getChildren().addAll(
                title,
                buildGeneralSection(),
                buildAlcoholSection()
        );

        root.setCenter(content);
        root.setBottom(buildFooter(stage));

        populateFields();

        reportingCategoryBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            applyDefaultCountMethodForCategory(newValue);
            updateAlcoholSection();
        });
        countMethodGroup.selectedToggleProperty().addListener((obs, oldValue, newValue) -> updateAlcoholSection());

        updateAlcoholSection();

        Scene scene = new Scene(root, 860, 620);

        String css = getClass().getResource("/style.css") == null
                ? null
                : getClass().getResource("/style.css").toExternalForm();

        if (css != null) {
            scene.getStylesheets().add(css);
        }

        stage.setScene(scene);
        stage.showAndWait();
    }

    private VBox buildGeneralSection() {
        Label sectionTitle = new Label("General Information");
        sectionTitle.getStyleClass().add("section-title");

        skuField = new TextField();
        descriptionField = new TextField();

        categoryBox = new ComboBox<>();
        categoryBox.getItems().addAll(CATEGORIES);
        categoryBox.setMaxWidth(Double.MAX_VALUE);

        reportingCategoryBox = new ComboBox<>();
        reportingCategoryBox.getItems().addAll(ProductDialog.REPORTING_CATEGORIES);
        reportingCategoryBox.setMaxWidth(Double.MAX_VALUE);

        unitBox = new ComboBox<>();
        unitBox.getItems().addAll(UNITS);
        unitBox.setMaxWidth(Double.MAX_VALUE);

        conversionFactorField = new TextField("1");
        packSizeField = new TextField();
        packCountField = new TextField();
        lastCaseCostField = new TextField("0");

        GridPane grid = new GridPane();
        grid.setHgap(18);
        grid.setVgap(12);

        addField(grid, "SKU:", skuField, 0, 0);
        addField(grid, "Description:", descriptionField, 1, 0);

        addField(grid, "Category:", categoryBox, 0, 1);
        addField(grid, "Reporting Category:", reportingCategoryBox, 1, 1);

        addField(grid, "Unit:", unitBox, 0, 2);
        addField(grid, "Units Per Purchased Unit:", conversionFactorField, 1, 2);

        addField(grid, "Pack Size:", packSizeField, 0, 3);
        addField(grid, "Pack Count:", packCountField, 1, 3);

        addField(grid, "Last Case Cost:", lastCaseCostField, 0, 4);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(50);

        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(50);

        grid.getColumnConstraints().addAll(col1, col2);

        return new VBox(10, sectionTitle, grid);
    }

    private VBox buildAlcoholSection() {
        Label sectionTitle = new Label("Alcohol Inventory");
        sectionTitle.getStyleClass().add("section-title");

        countMethodGroup = new ToggleGroup();

        weightRadio = new RadioButton("Weight");
        weightRadio.setUserData("WEIGHT");
        weightRadio.setToggleGroup(countMethodGroup);

        eachRadio = new RadioButton("Each Count");
        eachRadio.setUserData("EACH");
        eachRadio.setToggleGroup(countMethodGroup);

        weightRadio.setSelected(true);

        HBox countMethodControls = new HBox(16, weightRadio, eachRadio);
        countMethodControls.setAlignment(Pos.CENTER_LEFT);

        containerTypeBox = new ComboBox<>();
        containerTypeBox.setMaxWidth(Double.MAX_VALUE);

        containerTypeLabel = new Label("Container Type:");
        tareWeightLabel = new Label("Tare Weight:");

        tareWeightField = new TextField("0");

        GridPane grid = new GridPane();
        grid.setHgap(18);
        grid.setVgap(12);

        addField(grid, "Count Method:", countMethodControls, 0, 0);
        addField(grid, containerTypeLabel, containerTypeBox, 1, 0);
        addField(grid, tareWeightLabel, tareWeightField, 0, 1);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(50);

        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(50);

        grid.getColumnConstraints().addAll(col1, col2);

        alcoholSection = new VBox(10, sectionTitle, grid);
        return alcoholSection;
    }

    private HBox buildFooter(Stage stage) {
        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(e -> stage.close());

        Button saveButton = new Button("Save");
        saveButton.getStyleClass().add("primary-button");
        saveButton.setOnAction(e -> save(stage));

        HBox footer = new HBox(10, cancelButton, saveButton);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(14, 20, 18, 20));

        return footer;
    }

    private void addField(GridPane grid, String labelText, Node field, int col, int row) {
        addField(grid, new Label(labelText), field, col, row);
    }

    private void addField(GridPane grid, Label label, Node field, int col, int row) {
        VBox box = new VBox(5, label, field);
        grid.add(box, col, row);
    }

    private void populateFields() {
        if (product == null) {
            skuField.setText("");
            descriptionField.setText("");
            categoryBox.setValue("Uncategorized");
            reportingCategoryBox.setValue("OTHER");
            unitBox.setValue("EA");
            conversionFactorField.setText("1");
            packSizeField.setText("");
            packCountField.setText("");
            lastCaseCostField.setText("0");
            weightRadio.setSelected(true);
            return;
        }

        skuField.setText(nullToBlank(product.getSku()));
        descriptionField.setText(nullToBlank(product.getDescription()));
        categoryBox.setValue(valueOrDefault(product.getCategory(), "Uncategorized"));
        reportingCategoryBox.setValue(valueOrDefault(product.getReportingCategory(), "OTHER"));
        unitBox.setValue(valueOrDefault(product.getUnit(), "EA"));
        conversionFactorField.setText(String.valueOf(product.getConversionFactor()));
        packSizeField.setText(nullToBlank(product.getPackSize()));
        packCountField.setText(nullToBlank(product.getPackCount()));
        lastCaseCostField.setText(
                product.getLastCaseCost() == null
                        ? "0"
                        : product.getLastCaseCost().toPlainString()
        );

        if (product.hasAlcoholProfile()) {
            AlcoholProductProfile profile = product.getAlcoholProfile();
            pendingContainerTypeCode = profile.getContainerType();

            if ("EACH".equals(profile.getCountMethod())) {
                eachRadio.setSelected(true);
            } else {
                weightRadio.setSelected(true);
            }

            tareWeightField.setText(String.valueOf(profile.getTareWeight()));
        } else {
            if ("BEER".equals(product.getReportingCategory())) {
                eachRadio.setSelected(true);
            } else if (product.isAlcoholProduct()) {
                weightRadio.setSelected(true);
            }
        }
    }

    private void applyDefaultCountMethodForCategory(String category) {
        if (!isAlcoholCategory(category)) {
            return;
        }

        if ("BEER".equals(category)) {
            eachRadio.setSelected(true);
        } else if (countMethodGroup.getSelectedToggle() == null) {
            weightRadio.setSelected(true);
        }
    }

    private void updateAlcoholSection() {
        String category = reportingCategoryBox.getValue();
        boolean alcohol = isAlcoholCategory(category);

        alcoholSection.setVisible(alcohol);
        alcoholSection.setManaged(alcohol);

        if (!alcohol) {
            return;
        }

        String previousCode = getSelectedContainerCode();
        if (previousCode == null) {
            previousCode = pendingContainerTypeCode;
        }

        containerTypeBox.getItems().clear();
        containerTypeBox.getItems().addAll(containerOptionsForCategory(category));

        selectContainer(previousCode);
        pendingContainerTypeCode = null;

        boolean each = "EACH".equals(getSelectedCountMethod());

        containerTypeBox.setVisible(!each);
        containerTypeBox.setManaged(!each);
        containerTypeLabel.setVisible(!each);
        containerTypeLabel.setManaged(!each);

        tareWeightField.setVisible(!each);
        tareWeightField.setManaged(!each);
        tareWeightLabel.setVisible(!each);
        tareWeightLabel.setManaged(!each);

        String unit = getMeasurementUnit(getSelectedCountMethod(), getSelectedContainerCode());
        tareWeightLabel.setText("Tare Weight (" + unit.toLowerCase() + "):");

        if (each) {
            tareWeightField.setText("0");
        }
    }

    private void save(Stage stage) {
        if (!validateForm()) {
            return;
        }

        try {
            double conversionFactor = Double.parseDouble(conversionFactorField.getText().trim());
            BigDecimal lastCaseCost = new BigDecimal(lastCaseCostField.getText().trim());

            Product savedProduct = new Product(
                    product == null ? 0 : product.getId(),
                    skuField.getText().trim(),
                    descriptionField.getText().trim(),
                    categoryBox.getValue(),
                    reportingCategoryBox.getValue(),
                    unitBox.getValue(),
                    conversionFactor,
                    packSizeField.getText().trim(),
                    packCountField.getText().trim(),
                    lastCaseCost,
                    product == null ? null : product.getLastPurchasedDate(),
                    product == null || product.isActive()
            );

            if (savedProduct.isAlcoholProduct()) {
                savedProduct.setAlcoholProfile(buildAlcoholProfile(savedProduct));
            }

            productDao.save(savedProduct);

            if (onSave != null) {
                onSave.run();
            }

            stage.close();

        } catch (NumberFormatException ex) {
            showAlert(
                    Alert.AlertType.ERROR,
                    "Invalid Number",
                    "Please check Conversion Factor, Last Case Cost, and Tare Weight."
            );
        }
    }

    private boolean validateForm() {
        if (isBlank(skuField.getText())) {
            showAlert(Alert.AlertType.WARNING, "Missing SKU", "Please enter a SKU.");
            skuField.requestFocus();
            return false;
        }

        if (isBlank(descriptionField.getText())) {
            showAlert(Alert.AlertType.WARNING, "Missing Description", "Please enter a product description.");
            descriptionField.requestFocus();
            return false;
        }

        if (categoryBox.getValue() == null) {
            showAlert(Alert.AlertType.WARNING, "Missing Category", "Please choose a category.");
            categoryBox.requestFocus();
            return false;
        }

        if (reportingCategoryBox.getValue() == null) {
            showAlert(Alert.AlertType.WARNING, "Missing Reporting Category", "Please choose a reporting category.");
            reportingCategoryBox.requestFocus();
            return false;
        }

        if (unitBox.getValue() == null) {
            showAlert(Alert.AlertType.WARNING, "Missing Unit", "Please choose a unit.");
            unitBox.requestFocus();
            return false;
        }

        try {
            Double.parseDouble(conversionFactorField.getText().trim());
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.WARNING, "Invalid Conversion Factor", "Units Per Purchased Unit must be a valid number.");
            conversionFactorField.requestFocus();
            return false;
        }

        try {
            new BigDecimal(lastCaseCostField.getText().trim());
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.WARNING, "Invalid Last Case Cost", "Last Case Cost must be a valid number.");
            lastCaseCostField.requestFocus();
            return false;
        }

        if (isAlcoholCategory(reportingCategoryBox.getValue()) && !"EACH".equals(getSelectedCountMethod())) {
            if (containerTypeBox.getValue() == null) {
                showAlert(Alert.AlertType.WARNING, "Missing Container Type", "Please choose a bottle or keg type.");
                containerTypeBox.requestFocus();
                return false;
            }

            try {
                Double.parseDouble(tareWeightField.getText().trim());
            } catch (NumberFormatException e) {
                showAlert(Alert.AlertType.WARNING, "Invalid Tare Weight", "Tare Weight must be a valid number.");
                tareWeightField.requestFocus();
                return false;
            }
        }

        return true;
    }

    private AlcoholProductProfile buildAlcoholProfile(Product savedProduct) {
        String countMethod = getSelectedCountMethod();
        String containerType = "EACH".equals(countMethod) ? "EACH" : getSelectedContainerCode();

        String measurementUnit = getMeasurementUnit(countMethod, containerType);
        double fullContentWeight = getFullContentWeight(countMethod, containerType);
        double tareWeight = "EACH".equals(countMethod)
                ? 0
                : Double.parseDouble(tareWeightField.getText().trim());

        return new AlcoholProductProfile(
                0,
                savedProduct.getId(),
                countMethod,
                containerType,
                measurementUnit,
                tareWeight,
                fullContentWeight,
                true
        );
    }

    private String getSelectedCountMethod() {
        Toggle selected = countMethodGroup.getSelectedToggle();
        return selected == null ? "WEIGHT" : selected.getUserData().toString();
    }

    private String getSelectedContainerCode() {
        ContainerOption selected = containerTypeBox.getValue();
        return selected == null ? null : selected.code();
    }

    private void selectContainer(String code) {
        if (code != null) {
            for (ContainerOption option : containerTypeBox.getItems()) {
                if (option.code().equals(code)) {
                    containerTypeBox.setValue(option);
                    return;
                }
            }
        }

        if (!containerTypeBox.getItems().isEmpty()) {
            containerTypeBox.setValue(containerTypeBox.getItems().get(0));
        }
    }

    private ContainerOption[] containerOptionsForCategory(String category) {
        if (category == null) {
            return new ContainerOption[]{new ContainerOption("CUSTOM", "Custom")};
        }

        return switch (category) {
            case "LIQUOR" -> new ContainerOption[]{
                    new ContainerOption("750ML_BOTTLE", "750 mL Bottle"),
                    new ContainerOption("1_14L_BOTTLE", "1.14 L Bottle"),
                    new ContainerOption("1_5L_BOTTLE", "1.5 L Bottle"),
                    new ContainerOption("CUSTOM", "Custom")
            };
            case "WINE" -> new ContainerOption[]{
                    new ContainerOption("750ML_BOTTLE", "750 mL Bottle"),
                    new ContainerOption("1_5L_BOTTLE", "1.5 L Bottle"),
                    new ContainerOption("CUSTOM", "Custom")
            };
            case "DRAUGHT", "IMPORT DRAUGHT" -> new ContainerOption[]{
                    new ContainerOption("FULL_KEG", "Full Keg"),
                    new ContainerOption("MEDIUM_KEG", "Medium Keg"),
                    new ContainerOption("SMALL_KEG", "Small Keg"),
                    new ContainerOption("CUSTOM", "Custom")
            };
            case "BEER" -> new ContainerOption[]{
                    new ContainerOption("EACH", "Each Count")
            };
            default -> new ContainerOption[]{
                    new ContainerOption("CUSTOM", "Custom")
            };
        };
    }

    private boolean isAlcoholCategory(String category) {
        if (category == null) {
            return false;
        }

        return switch (category) {
            case "LIQUOR", "WINE", "BEER", "DRAUGHT", "IMPORT DRAUGHT" -> true;
            default -> false;
        };
    }

    private String getMeasurementUnit(String countMethod, String containerType) {
        if ("EACH".equals(countMethod)) {
            return "EACH";
        }

        if (containerType != null && containerType.contains("KEG")) {
            return "LB";
        }

        return "OZ";
    }

    private double getFullContentWeight(String countMethod, String containerType) {
        if ("EACH".equals(countMethod)) {
            return 1;
        }

        return switch (containerType) {
            case "750ML_BOTTLE" -> 25.36;
            case "1_14L_BOTTLE" -> 38.5;
            case "1_5L_BOTTLE" -> 50.72;
            case "FULL_KEG" -> 130.0;
            case "MEDIUM_KEG" -> 80.0;
            case "SMALL_KEG" -> 45.0;
            default -> 1.0;
        };
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private record ContainerOption(String code, String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}
