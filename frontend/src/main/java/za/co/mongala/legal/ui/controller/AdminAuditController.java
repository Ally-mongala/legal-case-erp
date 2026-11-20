package za.co.mongala.legal.ui.controller;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.FileChooser;
import za.co.mongala.legal.model.ActivityLog;
import za.co.mongala.legal.model.RejectedDocument;
import za.co.mongala.legal.ui.AppContext;
import za.co.mongala.legal.ui.Toast;

import java.io.File;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Admin portal - Audits &amp; reports (FR9). View the full audit trail and
 * download CSV reports (audit log + case register).
 */
public class AdminAuditController {

    private final AppContext ctx;

    @FXML private TableView<ActivityLog> auditTable;
    @FXML private TableColumn<ActivityLog, String> timeCol;
    @FXML private TableColumn<ActivityLog, String> userCol;
    @FXML private TableColumn<ActivityLog, String> actionCol;
    @FXML private TableColumn<ActivityLog, String> detailsCol;
    @FXML private TableColumn<ActivityLog, ActivityLog.Level> levelCol;
    @FXML private Button downloadAuditButton;
    @FXML private Button downloadCasesButton;
    @FXML private Button downloadRejectionsButton;
    @FXML private TableView<RejectedDocument> rejectionTable;
    @FXML private TableColumn<RejectedDocument, String> rejFileCol;
    @FXML private TableColumn<RejectedDocument, String> rejCaseCol;
    @FXML private TableColumn<RejectedDocument, String> rejByCol;
    @FXML private TableColumn<RejectedDocument, String> rejReasonCol;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss");

    public AdminAuditController(AppContext ctx) {
        this.ctx = ctx;
    }

    @FXML
    private void initialize() {
        timeCol.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getTimestamp().format(DT_FMT)));
        userCol.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getUserName()));
        actionCol.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getAction()));
        detailsCol.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getDetails()));
        levelCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getLevel()));
        levelCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(ActivityLog.Level level, boolean empty) {
                super.updateItem(level, empty);
                if (empty || level == null) { setGraphic(null); return; }
                Label chip = new Label(level.name());
                chip.getStyleClass().addAll("chip",
                        level == ActivityLog.Level.SUCCESS ? "chip-green"
                                : level == ActivityLog.Level.WARNING ? "chip-amber" : "chip-blue");
                setGraphic(chip);
            }
        });

        downloadAuditButton.setOnAction(e -> download("audit-report.csv", () -> ctx.dataStore().auditCsv()));
        downloadRejectionsButton.setOnAction(e -> download("rejection-reasons.csv", () -> ctx.dataStore().rejectionsCsv()));
        setupRejectionTable();
        downloadCasesButton.setOnAction(e -> download("cases-report.csv", () -> {
            // build a small case register CSV from the store
            StringBuilder sb = new StringBuilder("reference,title,client,opposing_party,status,matter_type,court,amount\n");
            ctx.dataStore().getAllCases().forEach(c -> sb.append('"').append(c.getReference()).append("\",\"")
                    .append(c.getTitle()).append("\",\"").append(c.getClientName()).append("\",\"")
                    .append(c.getOpposingParty()).append("\",\"")
                    .append(c.getStatus() == null ? "" : c.getStatus().name()).append("\",\"")
                    .append(c.getMatterType()).append("\",\"").append(c.getCourt()).append("\",")
                    .append(c.getAmountInvolved()).append('\n'));
            return sb.toString();
        }));

        refresh();
    }

    private void setupRejectionTable() {
        rejFileCol.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getFileName()));
        rejCaseCol.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getCaseReference()));
        rejByCol.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getUploadedBy()));
        rejReasonCol.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getRejectionReason()));
    }

    private void download(String defaultName, java.util.function.Supplier<String> content) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save report");
        chooser.setInitialFileName(defaultName);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        File target = chooser.showSaveDialog(downloadAuditButton.getScene().getWindow());
        if (target == null) return;
        try {
            Files.writeString(target.toPath(), content.get());
            ctx.auditService().info(ctx.currentUser().getName(), "REPORT_DOWNLOAD",
                    "Admin downloaded report: " + target.getName());
            Toast.success(ctx, "Report saved to " + target.getName() + " (FR9)");
        } catch (Exception ex) {
            Toast.error(ctx, "Could not save report: " + ex.getMessage());
        }
    }

    private void refresh() {
        try {
            List<ActivityLog> logs = ctx.dataStore().recentActivity(500);
            auditTable.getItems().setAll(logs);
            rejectionTable.getItems().setAll(ctx.dataStore().rejectionsList());
        } catch (Exception e) {
            Toast.error(ctx, "Could not load audit log: " + e.getMessage());
        }
    }
}
