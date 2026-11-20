package za.co.mongala.legal.ui.controller;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import za.co.mongala.legal.model.Case;
import za.co.mongala.legal.model.Rating;
import za.co.mongala.legal.ui.AppContext;
import za.co.mongala.legal.ui.Toast;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Lawyer view of client ratings (FR: view the ratings from a client).
 */
public class LawyerRatingsController {

    private final AppContext ctx;

    @FXML private HBox summaryRow;
    @FXML private TableView<Rating> ratingTable;
    @FXML private TableColumn<Rating, String> caseCol;
    @FXML private TableColumn<Rating, Integer> scoreCol;
    @FXML private TableColumn<Rating, String> commentCol;
    @FXML private TableColumn<Rating, String> dateCol;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("d MMM yyyy");

    public LawyerRatingsController(AppContext ctx) {
        this.ctx = ctx;
    }

    @FXML
    private void initialize() {
        caseCol.setCellValueFactory(d -> new ReadOnlyStringWrapper(caseRef(d.getValue().getCaseId())));
        scoreCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getScore()));
        scoreCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer score, boolean empty) {
                super.updateItem(score, empty);
                if (empty || score == null) { setGraphic(null); return; }
                Label stars = new Label("\u2605".repeat(Math.max(1, Math.min(5, score))));
                stars.getStyleClass().add("star-on");
                setGraphic(stars);
            }
        });
        commentCol.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getComment()));
        dateCol.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getCreatedAt().format(DT_FMT)));

        refresh();
    }

    private void refresh() {
        try {
            List<Rating> ratings = ctx.dataStore().ratingsList();
            ratingTable.getItems().setAll(ratings);

            summaryRow.getChildren().clear();
            double avg = ratings.stream().mapToInt(Rating::getScore).average().orElse(0);
            VBox card = new VBox(2);
            card.getStyleClass().add("card");
            card.setPrefWidth(220);
            Label title = new Label("AVERAGE RATING");
            title.getStyleClass().add("stat-title");
            Label value = new Label(String.format("%.1f / 5", avg));
            value.getStyleClass().add("stat-value");
            card.getChildren().addAll(title, value);
            summaryRow.getChildren().add(card);

            VBox countCard = new VBox(2);
            countCard.getStyleClass().add("card");
            countCard.setPrefWidth(220);
            Label cTitle = new Label("TOTAL RATINGS");
            cTitle.getStyleClass().add("stat-title");
            Label cValue = new Label(String.valueOf(ratings.size()));
            cValue.getStyleClass().add("stat-value");
            countCard.getChildren().addAll(cTitle, cValue);
            summaryRow.getChildren().add(countCard);
        } catch (Exception e) {
            Toast.error(ctx, "Could not load ratings: " + e.getMessage());
        }
    }

    private String caseRef(String caseId) {
        return ctx.dataStore().findCaseById(caseId).map(Case::getReference).orElse(caseId);
    }
}
