package ca.foodinventory.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.util.StringConverter;

public final class SearchableComboBoxSupport {

    private SearchableComboBoxSupport() {
    }

    public static <T> void makeSearchable(
            ComboBox<T> comboBox,
            ObservableList<T> items,
            StringConverter<T> displayConverter
    ) {
        FilteredList<T> filteredItems = new FilteredList<>(items, item -> true);
        boolean[] selectingValue = {false};

        comboBox.setItems(filteredItems);
        comboBox.setEditable(true);
        comboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(T item) {
                return displayConverter.toString(item);
            }

            @Override
            public T fromString(String text) {
                if (text == null || text.isBlank()) {
                    return null;
                }

                for (T item : items) {
                    if (displayConverter.toString(item).equalsIgnoreCase(text.trim())) {
                        return item;
                    }
                }

                return comboBox.getValue();
            }
        });

        comboBox.setCellFactory(listView -> displayCell(displayConverter));
        comboBox.setButtonCell(displayCell(displayConverter));

        comboBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            selectingValue[0] = true;
            filteredItems.setPredicate(item -> true);
            Platform.runLater(() -> selectingValue[0] = false);
        });

        comboBox.getEditor().textProperty().addListener((obs, oldText, newText) -> {
            if (selectingValue[0]) {
                return;
            }

            String search = newText == null ? "" : newText.trim().toLowerCase();

            filteredItems.setPredicate(item -> {
                if (search.isBlank()) {
                    return true;
                }

                return displayConverter.toString(item)
                        .toLowerCase()
                        .contains(search);
            });

            if (!comboBox.isShowing()) {
                comboBox.show();
            }
        });

        comboBox.setOnHidden(event -> filteredItems.setPredicate(item -> true));
    }

    public static <T> void makeSearchable(
            ComboBox<T> comboBox,
            java.util.List<T> items,
            StringConverter<T> displayConverter
    ) {
        makeSearchable(comboBox, FXCollections.observableArrayList(items), displayConverter);
    }

    private static <T> ListCell<T> displayCell(StringConverter<T> displayConverter) {
        return new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : displayConverter.toString(item));
            }
        };
    }
}
