package ca.foodinventory.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.util.StringConverter;

import java.util.Locale;
import java.util.function.Predicate;

public final class SearchableComboBoxSupport {

    private static final int MAX_DISPLAYED_MATCHES = 75;

    private SearchableComboBoxSupport() {
    }

    public static <T> void makeSearchable(
            ComboBox<T> comboBox,
            ObservableList<T> items,
            StringConverter<T> displayConverter
    ) {
        ObservableList<T> displayedItems = FXCollections.observableArrayList();
        boolean[] selectingValue = {false};

        refreshDisplayedItems(displayedItems, items, item -> true, comboBox.getValue());
        comboBox.setItems(displayedItems);
        comboBox.setEditable(true);
        comboBox.setVisibleRowCount(12);
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
            refreshDisplayedItems(displayedItems, items, item -> true, newValue);
            Platform.runLater(() -> selectingValue[0] = false);
        });

        comboBox.getEditor().textProperty().addListener((obs, oldText, newText) -> {
            if (selectingValue[0]) {
                return;
            }

            String search = newText == null
                    ? ""
                    : newText.trim().toLowerCase(Locale.ROOT);

            refreshDisplayedItems(displayedItems, items, item -> {
                if (search.isBlank()) {
                    return true;
                }

                return displayConverter.toString(item)
                        .toLowerCase(Locale.ROOT)
                        .contains(search);
            }, comboBox.getValue());

            if (comboBox.isFocused() && !comboBox.isShowing() && !displayedItems.isEmpty()) {
                comboBox.show();
            }
        });

        comboBox.setOnHidden(event ->
                refreshDisplayedItems(displayedItems, items, item -> true, comboBox.getValue())
        );
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

    private static <T> void refreshDisplayedItems(
            ObservableList<T> displayedItems,
            ObservableList<T> sourceItems,
            Predicate<T> predicate,
            T selectedItem
    ) {
        displayedItems.clear();

        if (selectedItem != null) {
            displayedItems.add(selectedItem);
        }

        for (T item : sourceItems) {
            if (displayedItems.size() >= MAX_DISPLAYED_MATCHES) {
                break;
            }

            if (item == selectedItem || !predicate.test(item)) {
                continue;
            }

            displayedItems.add(item);
        }
    }
}
