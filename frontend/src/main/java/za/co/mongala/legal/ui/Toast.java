package za.co.mongala.legal.ui;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Small helper that pops a transient notification at the bottom of the shell.
 * Used for success / error / info feedback across all screens.
 */
public final class Toast {

    private Toast() {}

    public static void show(AppContext ctx, String message, String styleClass) {
        if (ctx.toastPane() == null) return;
        StackPane pane = ctx.toastPane();

        Label toast = new Label(message);
        toast.getStyleClass().addAll("toast", styleClass);
        toast.setMaxWidth(pane.getWidth() - 80);

        StackPane.setAlignment(toast, javafx.geometry.Pos.BOTTOM_CENTER);
        StackPane.setMargin(toast, new javafx.geometry.Insets(0, 0, 28, 0));
        pane.getChildren().add(toast);
        toast.toFront();

        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), toast);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();

        PauseTransition hold = new PauseTransition(Duration.seconds(3.2));
        hold.setOnFinished(e -> {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(350), toast);
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(f -> pane.getChildren().remove(toast));
            fadeOut.play();
        });
        hold.play();
    }

    public static void success(AppContext ctx, String message) { show(ctx, message, "toast-success"); }
    public static void error(AppContext ctx, String message)   { show(ctx, message, "toast-error"); }
    public static void info(AppContext ctx, String message)    { show(ctx, message, "toast-info"); }
}
