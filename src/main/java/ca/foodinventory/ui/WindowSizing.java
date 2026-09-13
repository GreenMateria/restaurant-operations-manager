package ca.foodinventory.ui;

import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

public final class WindowSizing {

    private static final double SCREEN_MARGIN = 40;
    private static final double MIN_WIDTH = 640;
    private static final double MIN_HEIGHT = 420;

    private WindowSizing() {
    }

    public static Scene scene(Parent root, double preferredWidth, double preferredHeight) {
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        return new Scene(
                root,
                clamp(preferredWidth, MIN_WIDTH, bounds.getWidth() - SCREEN_MARGIN),
                clamp(preferredHeight, MIN_HEIGHT, bounds.getHeight() - SCREEN_MARGIN)
        );
    }

    public static Scene scene(
            Parent root,
            double preferredWidth,
            double preferredHeight,
            Window owner
    ) {
        Rectangle2D bounds = visibleBounds(owner);
        return new Scene(
                root,
                clamp(preferredWidth, MIN_WIDTH, bounds.getWidth() - SCREEN_MARGIN),
                clamp(preferredHeight, MIN_HEIGHT, bounds.getHeight() - SCREEN_MARGIN)
        );
    }

    public static void centerOnVisibleScreen(Stage stage) {
        Rectangle2D bounds = visibleBounds(stage.getOwner());
        stage.setOnShown(event -> clampAndCenter(stage, bounds));
    }

    public static void fitAndCenter(Stage stage, double preferredWidth, double preferredHeight) {
        Rectangle2D bounds = visibleBounds(stage.getOwner());
        double width = clamp(preferredWidth, MIN_WIDTH, bounds.getWidth() - SCREEN_MARGIN);
        double height = clamp(preferredHeight, MIN_HEIGHT, bounds.getHeight() - SCREEN_MARGIN);
        stage.setWidth(width);
        stage.setHeight(height);
        stage.setOnShown(event -> clampAndCenter(stage, bounds));
    }

    public static double width(double preferredWidth) {
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        return clamp(preferredWidth, MIN_WIDTH, bounds.getWidth() - SCREEN_MARGIN);
    }

    public static double height(double preferredHeight) {
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        return clamp(preferredHeight, MIN_HEIGHT, bounds.getHeight() - SCREEN_MARGIN);
    }

    private static void clampAndCenter(Stage stage, Rectangle2D bounds) {
        double maxWidth = Math.max(MIN_WIDTH, bounds.getWidth() - SCREEN_MARGIN);
        double maxHeight = Math.max(MIN_HEIGHT, bounds.getHeight() - SCREEN_MARGIN);
        double width = Math.min(stage.getWidth(), maxWidth);
        double height = Math.min(stage.getHeight(), maxHeight);

        stage.setWidth(width);
        stage.setHeight(height);
        stage.setX(bounds.getMinX() + Math.max(0, (bounds.getWidth() - width) / 2));
        stage.setY(bounds.getMinY() + Math.max(0, (bounds.getHeight() - height) / 2));
    }

    private static Rectangle2D visibleBounds(Window owner) {
        if (owner == null) {
            return Screen.getPrimary().getVisualBounds();
        }

        return Screen.getScreensForRectangle(
                        owner.getX(),
                        owner.getY(),
                        owner.getWidth(),
                        owner.getHeight()
                )
                .stream()
                .findFirst()
                .orElse(Screen.getPrimary())
                .getVisualBounds();
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (maximum < minimum) {
            return Math.max(320, maximum);
        }
        return Math.max(minimum, Math.min(value, maximum));
    }
}
