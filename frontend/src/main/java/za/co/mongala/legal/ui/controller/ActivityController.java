package za.co.mongala.legal.ui.controller;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Button;
import javafx.stage.FileChooser;
import za.co.mongala.legal.model.ActivityLog;
import za.co.mongala.legal.ui.AppContext;
import za.co.mongala.legal.ui.Toast;

import java.time.format.DateTimeFormatter;

/**
 * Activity / audit screen (FXML: fxml/views/activity.fxml).
 *
 * Displays the tamper-evident audit trail required by FR10. Every sensitive
 * action (login, upload, AI processing, summary approval, calendar sync) is
 * recorded with the actor, the action and the outcome.
 */
public class ActivityController {

    private final AppContext ctx;

    @FXML private TableView<ActivityLog> activityTable;
    @FXML private Button downloadButton;
    @FXML private TableColumn<ActivityLog, String> timeCol;
    @FXML private TableColumn<ActivityLog, String> userCol;
    @FXML private TableColumn<ActivityLog, String> actionCol;
    @FXML private TableColumn<ActivityLog, String> detailsCol;
    @FXML private TableColumn<ActivityLog, ActivityLog.Level> levelCol;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss");

    public ActivityController(AppContext ctx) {
        this.ctx = ctx;
    }

    @FXML
    private void initialize() {
        timeCol.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getTimestamp().format(DT_FMT)));
        userCol.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getUserName()));
        actionCol.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getAction()));
        detailsCol.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getDetails()));
        levelCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getLevel()));
        downloadButton.setOnAction(e -> onDownload());

        levelCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(ActivityLog.Level level, boolean empty) {
                super.updateItem(level, empty);
                if (empty || level == null) { setGraphic(null); return; }
                Label chip = new Label(switch (level) {
                    case SUCCESS -> "SUCCESS";
                    case WARNING -> "WARNING";
                    default -> "INFO";
                });
                chip.getStyleClass().addAll("chip",
                        level == ActivityLog.Level.SUCCESS ? "chip-green"
                                : level == ActivityLog.Level.WARNING ? "chip-amber" : "chip-blue");
                setGraphic(chip);
            }
        });
        refresh();
    }

    /** Download the activity & audit history as CSV (lawyer/admin). */
    private void onDownload() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save audit history");
        chooser.setInitialFileName("activity-audit.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        java.io.File target = chooser.showSaveDialog(downloadButton.getScene().getWindow());
        if (target == null) return;
        try {
            byte[] bytes;
            if (ctx.isRemote()) {
                bytes = ctx.api().downloadBytes("/api/activity.csv");
            } else {
                bytes = ctx.dataStore().auditCsv().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }
            java.nio.file.Files.write(target.toPath(), bytes);
            ctx.auditService().info(ctx.currentUser().getName(), "ACTIVITY_DOWNLOAD",
                    "Downloaded activity & audit history: " + target.getName());
            Toast.success(ctx, "Audit history saved to " + target.getName());
        } catch (Exception ex) {
            Toast.error(ctx, "Could not download audit: " + ex.getMessage());
        }
    }

    private void refresh() {
        activityTable.getItems().setAll(ctx.dataStore().recentActivity(500));
    }
}
