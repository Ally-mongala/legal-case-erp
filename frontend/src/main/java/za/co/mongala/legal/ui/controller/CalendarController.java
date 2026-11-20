package za.co.mongala.legal.ui.controller;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import za.co.mongala.legal.model.Deadline;
import za.co.mongala.legal.ui.AppContext;
import za.co.mongala.legal.ui.Toast;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Calendar sync screen (FXML: fxml/views/calendar.fxml).
 *
 * Implements FR4 and US5: extracted deadlines are pushed to Google Calendar
 * via the (simulated) API with configurable reminders. The screen shows the
 * entire firm's deadline book with sync state per event.
 */
public class CalendarController {

    private final AppContext ctx;

    @FXML private TableView<Deadline> deadlineTable;
    @FXML private TableColumn<Deadline, String> caseCol;
    @FXML private TableColumn<Deadline, String> titleCol;
    @FXML private TableColumn<Deadline, String> dateCol;
    @FXML private TableColumn<Deadline, Long> daysCol;
    @FXML private TableColumn<Deadline, Integer> reminderCol;
    @FXML private TableColumn<Deadline, Boolean> syncCol;
    @FXML private TableColumn<Deadline, Deadline> actionCol;
    @FXML private Button syncAllButton;
    @FXML private Button unsyncAllButton;
    @FXML private Button exportIcsButton;
    @FXML private Label syncSummaryLabel;

    private static final DateTimeFormatter D_FMT = DateTimeFormatter.ofPattern("d MMM yyyy");

    public CalendarController(AppContext ctx) {
        this.ctx = ctx;
    }

    @FXML
    private void initialize() {
        caseCol.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getCaseReference()));
        titleCol.setCellValueFactory(new PropertyValueFactory<>("title"));
        dateCol.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getDate().format(D_FMT)));
        daysCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().daysLeft()));
        daysCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Long days, boolean empty) {
                super.updateItem(days, empty);
                if (empty || days == null) { setGraphic(null); return; }
                Label l = new Label(days < 0 ? (days + "d overdue") : days + "d");
                l.getStyleClass().addAll("chip", days < 0 ? "chip-red" : days <= 7 ? "chip-amber" : "chip-blue");
                setGraphic(l);
            }
        });
        reminderCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().getReminderDays()));
        syncCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().isSynced()));
        syncCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Boolean synced, boolean empty) {
                super.updateItem(synced, empty);
                if (empty || synced == null) { setGraphic(null); return; }
                Label l = new Label(synced ? "SYNCED \u2713" : "PENDING");
                l.getStyleClass().addAll("chip", synced ? "chip-green" : "chip-gray");
                setGraphic(l);
            }
        });
        actionCol.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button();
            @Override
            protected void updateItem(Deadline d, boolean empty) {
                super.updateItem(d, empty);
                if (empty || d == null) { setGraphic(null); return; }
                btn.getStyleClass().setAll("btn-ghost");
                btn.setText(d.isSynced() ? "Unsync" : "Sync now");
                btn.setOnAction(e -> { if (d.isSynced()) unsyncOne(d); else syncOne(d); });
                setGraphic(btn);
            }
        });

        syncAllButton.setOnAction(e -> syncAll());
        unsyncAllButton.setOnAction(e -> unsyncAll());
        exportIcsButton.setOnAction(e -> onExportIcs());
        refresh();
    }

    private void refresh() {
        List<Deadline> ds = ctx.dataStore().allDeadlines();
        deadlineTable.getItems().setAll(ds);
        long pending = ds.stream().filter(d -> !d.isSynced()).count();
        long overdue = ds.stream().filter(d -> d.daysLeft() < 0).count();
        syncSummaryLabel.setText(ds.size() + " deadlines \u00B7 " + pending + " awaiting Google Calendar sync"
                + (overdue > 0 ? " \u00B7 " + overdue + " overdue - urgent!" : " \u00B7 none overdue"));
        if (overdue > 0) syncSummaryLabel.getStyleClass().add("text-warning");
    }

    private void syncOne(Deadline d) {
        String receipt = ctx.calendarSyncService().sync(d);
        ctx.auditService().success("System", "CALENDAR_SYNC", receipt);
        refresh();
        Toast.success(ctx, receipt);
    }

    private void syncAll() {
        List<Deadline> ds = ctx.dataStore().allDeadlines();
        String receipt = ctx.workflow().syncAllDeadlines(ds);
        refresh();
        Toast.success(ctx, receipt);
    }

    private void unsyncOne(Deadline d) {
        String receipt = ctx.workflow().unsyncDeadline(d);
        refresh();
        Toast.success(ctx, receipt);
    }

    private void unsyncAll() {
        List<Deadline> ds = ctx.dataStore().allDeadlines();
        String receipt = ctx.workflow().unsyncAllDeadlines(ds);
        refresh();
        Toast.success(ctx, receipt);
    }

    /** Export the calendar as an .ics file the user can open with their phone/computer calendar. */
    private void onExportIcs() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Save calendar");
        chooser.setInitialFileName("legal-case-erp-calendar.ics");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Calendar files", "*.ics"));
        java.io.File target = chooser.showSaveDialog(exportIcsButton.getScene().getWindow());
        if (target == null) return;
        try {
            String ics;
            if (ctx.isRemote()) {
                byte[] bytes = ctx.api().downloadBytes("/api/calendar/export.ics");
                ics = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            } else {
                StringBuilder sb = new StringBuilder("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//LegalCase ERP//EN\r\n");
                ctx.dataStore().allDeadlines().stream().filter(Deadline::isSynced).forEach(d ->
                        sb.append("BEGIN:VEVENT\r\nDTSTART;VALUE=DATE:").append(d.getDate())
                          .append("\r\nSUMMARY:").append(d.getTitle()).append("\r\nEND:VEVENT\r\n"));
                sb.append("END:VCALENDAR\r\n");
                ics = sb.toString();
            }
            java.nio.file.Files.writeString(target.toPath(), ics);
            ctx.auditService().info(ctx.currentUser().getName(), "CALENDAR_EXPORT",
                    "Exported calendar .ics with synced deadlines");
            Toast.success(ctx, "Calendar saved - open it with your phone/computer calendar app to import.");
        } catch (Exception ex) {
            Toast.error(ctx, "Could not export calendar: " + ex.getMessage());
        }
    }
}
