package ca.foodinventory.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public abstract class ProductionModuleView<T> extends BorderPane {

    protected final TableView<T> table = new TableView<>();
    protected final TextField searchField = new TextField();

    protected ProductionModuleView(String titleText, String subtitleText) {
        getStyleClass().add("root-dark");

        Label title = new Label(titleText);
        title.getStyleClass().add("page-title");

        Label subtitle = new Label(subtitleText);
        subtitle.getStyleClass().add("section-title");

        searchField.setPromptText("Search...");
        searchField.setPrefWidth(350);

        HBox toolbar = buildToolbar();

        HBox searchBar = new HBox(15, searchField, toolbar);
        searchBar.setAlignment(Pos.CENTER_LEFT);

        VBox top = new VBox(15, title, subtitle, searchBar);
        top.setPadding(new Insets(20));

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        VBox center = new VBox(10, table);
        center.getStyleClass().add("content-area");

        setTop(top);
        setCenter(center);
    }

    protected abstract HBox buildToolbar();

    protected Button createPrimaryButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("primary-button");
        button.setOnAction(e -> action.run());
        return button;
    }

    protected void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    protected boolean containsIgnoreCase(String value, String search) {
        return value != null && value.toLowerCase().contains(search.toLowerCase());
    }
}