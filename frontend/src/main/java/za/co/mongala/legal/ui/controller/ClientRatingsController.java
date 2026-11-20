package za.co.mongala.legal.ui.controller;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import za.co.mongala.legal.model.Case;
import za.co.mongala.legal.model.Rating;
import za.co.mongala.legal.ui.AppContext;
import za.co.mongala.legal.ui.Toast;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Client "Rate your lawyer" tab (FR7). The client picks 1-5 stars, adds an
 * optional comment and submits; the rating is stored (backend) and shown in
 * the firm's ratings list below.
 */
public class ClientRatingsController {

    private final AppContext ctx;

    @FXML private HBox starRow;
    @FXML private TextArea commentArea;
    @FXML private Label statusLabel;
    @FXML private Button submitButton;
    @FXML private VBox ratingsBox;

    private final List<Button> stars = new java.util.ArrayList<>();
    private int selected = 0;
    private Case myCase;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("d MMM yyyy");

    public ClientRatingsController(AppContext ctx) {
        this.ctx = ctx;
    }

    @FXML
    private void initialize() {
        myCase = ctx.dataStore().findCasesForClient(ctx.currentUser().getId()).stream().findFirst().orElse(null);
        if (myCase == null) {
            statusLabel.setText("No case linked to your account yet.");
            submitButton.setDisable(true);
            return;
        }

        // 5 star buttons (filled/empty via unicode)
        for (int i = 1; i <= 5; i++) {
            int score = i;
            Button b = new Button("\u2606");
            b.getStyleClass().add("star-button");
            b.setPrefSize(44, 44);
            b.setOnAction(e -> select(score));
            stars.add(b);
            starRow.getChildren().add(b);
        }
        select(3); // default 3/5

        submitButton.setOnAction(e -> onSubmit());
        refreshRatings();
    }

    private void select(int score) {
        selected = score;
        for (int i = 0; i < stars.size(); i++) {
            stars.get(i).setText(i < score ? "\u2605" : "\u2606");
            stars.get(i).getStyleClass().setAll("star-button", i < score ? "star-on" : "star-off");
        }
    }

    private void onSubmit() {
        try {
            Rating r = new Rating("r" + System.currentTimeMillis(), myCase.getId(),
                    ctx.currentUser().getId(), "Assigned attorney", selected,
                    commentArea.getText() == null ? "" : commentArea.getText().trim(),
                    java.time.LocalDateTime.now());
            ctx.dataStore().createRating(r);
            ctx.auditService().success(ctx.currentUser().getName(), "RATING_SUBMITTED",
                    "Client rated the lawyer " + selected + "/5 on " + myCase.getReference());
            Toast.success(ctx, "Thank you! Your rating of " + selected + "/5 has been submitted (FR7).");
            commentArea.clear();
            refreshRatings();
        } catch (Exception ex) {
            Toast.error(ctx, "Could not submit rating: " + ex.getMessage());
        }
    }

    private void refreshRatings() {
        ratingsBox.getChildren().clear();
        try {
            List<Rating> all = ctx.dataStore().ratingsList();
            if (all.isEmpty()) {
                ratingsBox.getChildren().add(new Label("No ratings yet."));
                return;
            }
            all.forEach(r -> {
                HBox row = new HBox(10);
                row.getStyleClass().add("list-row");
                row.setAlignment(Pos.CENTER_LEFT);

                Label starsLabel = new Label("\u2605".repeat(Math.max(1, Math.min(5, r.getScore()))));
                starsLabel.getStyleClass().add("star-on");
                Label by = new Label("on " + r.getCaseId() + " \u00B7 " + r.getCreatedAt().format(DT_FMT));
                by.getStyleClass().add("row-sub");
                Label comment = new Label(r.getComment() == null || r.getComment().isBlank()
                        ? "(no comment)" : r.getComment());
                comment.getStyleClass().add("row-sub");
                comment.setWrapText(true);
                HBox.setHgrow(comment, javafx.scene.layout.Priority.ALWAYS);
                row.getChildren().addAll(starsLabel, by, comment);
                ratingsBox.getChildren().add(row);
            });
        } catch (Exception e) {
            ratingsBox.getChildren().add(new Label("Ratings are shown to the firm - " + e.getMessage()));
        }
    }
}
