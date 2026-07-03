package ca.foodinventory.ui;

import ca.foodinventory.model.Product;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.math.BigDecimal;
import java.util.Optional;

public class ProductDialog {

    private static final String[] CATEGORIES = {
            "MEAT", "POULTRY", "SEAFOOD", "DAIRY/CHEESE", "SOUP/DOUGH",
            "PASTA", "PASTA SAUCES", "FRIES/CANNED", "OILS",
            "SAUCE/DRESSINGS", "SPICES", "BEVERAGES", "PRODUCE",
            "BAR MIX", "PROMO"
    };

    public static final String[] REPORTING_CATEGORIES = {
            "FOOD",
            "PAPER",
            "TAKE OUT",
            "CLEANING",
            "DISHWASHING",
            "GUEST SUPPLIES",
            "WINE",
            "BEER",
            "DRAUGHT",
            "IMPORT DRAUGHT",
            "LIQUOR",
            "OTHER"
    };

    public Optional<Product> showAndWait() {
        Dialog<Product> dialog = new Dialog<>();
        dialog.setTitle("Add Product");
        dialog.setHeaderText("Enter product details");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        TextField skuField = new TextField();
        TextField descriptionField = new TextField();

        ComboBox<String> categoryBox = new ComboBox<>();
        categoryBox.getItems().addAll(CATEGORIES);
        categoryBox.setValue("MEAT");

        ComboBox<String> reportingCategoryBox = new ComboBox<>();
        reportingCategoryBox.getItems().addAll(REPORTING_CATEGORIES);
        reportingCategoryBox.setValue("OTHER");

        ComboBox<String> unitBox = new ComboBox<>();
        unitBox.getItems().addAll("EA", "KG", "LB", "L", "ML", "CASE", "BAG", "BOX", "GR");
        unitBox.setValue("EA");

        TextField conversionField = new TextField("1");
        TextField packSizeField = new TextField();
        TextField packCountField = new TextField();
        TextField lastCaseCostField = new TextField("0");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        grid.add(new Label("SKU:"), 0, 0);
        grid.add(skuField, 1, 0);

        grid.add(new Label("Description:"), 0, 1);
        grid.add(descriptionField, 1, 1);

        grid.add(new Label("Category:"), 0, 2);
        grid.add(categoryBox, 1, 2);

        grid.add(new Label("Reporting Category:"), 0, 3);
        grid.add(reportingCategoryBox, 1, 3);

        grid.add(new Label("Unit:"), 0, 4);
        grid.add(unitBox, 1, 4);

        grid.add(new Label("Units Per Purchased Unit:"), 0, 5);
        grid.add(conversionField, 1, 5);

        grid.add(new Label("Pack Size:"), 0, 6);
        grid.add(packSizeField, 1, 6);

        grid.add(new Label("Pack Count:"), 0, 7);
        grid.add(packCountField, 1, 7);

        grid.add(new Label("Last Case Cost:"), 0, 8);
        grid.add(lastCaseCostField, 1, 8);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> {
            if (button == saveButtonType) {
                double conversion = 1;

                try {
                    conversion = Double.parseDouble(conversionField.getText().trim());
                } catch (NumberFormatException ignored) {
                }

                BigDecimal lastCaseCost = BigDecimal.ZERO;

                try {
                    lastCaseCost = new BigDecimal(lastCaseCostField.getText().trim());
                } catch (NumberFormatException ignored) {
                }

                return new Product(
                        0,
                        skuField.getText().trim(),
                        descriptionField.getText().trim(),
                        categoryBox.getValue(),
                        reportingCategoryBox.getValue(),
                        unitBox.getValue(),
                        conversion,
                        packSizeField.getText().trim(),
                        packCountField.getText().trim(),
                        lastCaseCost,
                        null,
                        true
                );
            }

            return null;
        });

        return dialog.showAndWait();
    }
}