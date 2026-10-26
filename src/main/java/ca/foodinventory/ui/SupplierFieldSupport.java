package ca.foodinventory.ui;

import javafx.scene.control.ComboBox;

import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

final class SupplierFieldSupport {
    private SupplierFieldSupport() {
    }

    static void configure(ComboBox<String> field, List<String> existingNames) {
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String name : existingNames) {
            String normalized = normalize(name);
            if (!normalized.isEmpty()) {
                names.add(normalized);
            }
        }
        field.getItems().setAll(names);
        field.setEditable(true);
        field.setVisibleRowCount(12);
        field.getEditor().setPromptText("Select or enter supplier");
    }

    static String name(ComboBox<String> field) {
        String entered = normalize(field.getEditor().getText());
        for (String existing : field.getItems()) {
            if (existing.toLowerCase(Locale.ROOT).equals(entered.toLowerCase(Locale.ROOT))) {
                return existing;
            }
        }
        return entered;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }
}
