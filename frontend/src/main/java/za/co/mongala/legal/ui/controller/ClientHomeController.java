package za.co.mongala.legal.ui.controller;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import za.co.mongala.legal.model.*;
import za.co.mongala.legal.ui.AppContext;
import za.co.mongala.legal.ui.Toast;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Client portal home (FXML: fxml/views/client_home.fxml).
 *
 * Implements FR5 / US3: the client sees a simplified, AI-generated update of
 * their case, the upcoming deadlines (with reminders), and a preview of the
 * firm's messages. Only APPROVED summaries are ever shown here - drafts stay
 * with the legal team (FR6).
 */
public class ClientHomeController {

    private final AppContext ctx;

    @FXML private Label greetingLabel;
    @FXML private Label caseTitleLabel;
    @FXML private Label statusBadge;
    @FXML private Label caseMetaLabel;
    @FXML private VBox summaryBox;
    @FXML private VBox deadlineBox;
    @FXML private VBox messagePreviewBox;
    @FXML private Button viewDocumentsButton;
    @FXML private Button openMessagesButton;

    private static final DateTimeFormatter D_FMT = DateTimeFormatter.ofPattern("d MMMM yyyy");

    public ClientHomeController(AppContext ctx) {
        this.ctx = ctx;
    }

    @FXML
    private void initialize() {
        List<Case> cases = ctx.dataStore().findCasesForClient(ctx.currentUser().getId());
        if (cases.isEmpty()) {
            greetingLabel.setText("Welcome, " + ctx.currentUser().getName() + ".");
            summaryBox.getChildren().add(new Label("No case linked to your account yet."));
            return;
        }
        Case c = cases.get(0);
        greetingLabel.setText("Welcome back, " + firstname(ctx.currentUser().getName()));
        caseTitleLabel.setText(c.getTitle() + "  (" + c.getReference() + ")");
        statusBadge.setText(c.getStatus().getDisplayName().toUpperCase());
        statusBadge.setStyle("-fx-background-color: " + c.getStatus().getHexColor() + "; -fx-text-fill: white;");
        caseMetaLabel.setText("Matter type: " + c.getMatterType() + "  \u00B7  " + c.getCourt());

        buildSummary(c);
        buildDeadlines(c);
        buildMessagePreview(c);

        viewDocumentsButton.setOnAction(e -> ctx.app().showView("client_documents", null));
        openMessagesButton.setOnAction(e -> ctx.app().showView("client_messages", null));
    }

    /** Shows the latest APPROVED summary only (FR6). */
    private void buildSummary(Case c) {
        Summary approved = ctx.dataStore().summariesForCase(c.getId()).stream()
                .filter(s -> s.getStatus() == Summary.SummaryStatus.APPROVED)
                .findFirst().orElse(null);

        if (approved == null) {
            Label title = new Label("No approved update yet");
            title.getStyleClass().add("section-title");
            Label note = new Label("Your legal team is still preparing your case update. "
                    + "As soon as a summary is approved it will appear here - usually within a few days of a document upload.");
            note.getStyleClass().add("row-sub");
            note.setWrapText(true);
            summaryBox.getChildren().addAll(title, note);
            return;
        }

        Label title = new Label("Your case update - AI summarised");
        title.getStyleClass().add("section-title");

        Label p1 = paragraphLabel(approved.getParagraph1());
        Label p2 = paragraphLabel(approved.getParagraph2());
        Label p3 = paragraphLabel(approved.getParagraph3());

        Label meta = new Label("Approved by your attorney on "
                + (approved.getApprovedAt() != null ? approved.getApprovedAt().format(DateTimeFormatter.ofPattern("d MMMM yyyy")) : "")
                + "  \u00B7  version " + approved.getVersion());
        meta.getStyleClass().add("row-sub");

        summaryBox.getChildren().addAll(title, p1, p2, p3, meta);
    }

    private Label paragraphLabel(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.getStyleClass().add("body-text");
        VBox.setMargin(l, new Insets(0, 0, 6, 0));
        return l;
    }

    private void buildDeadlines(Case c) {
        List<Deadline> ds = ctx.dataStore().deadlinesForCase(c.getId()).stream()
                .filter(d -> d.daysLeft() >= 0)
                .limit(4)
                .toList();
        if (ds.isEmpty()) {
            deadlineBox.getChildren().add(new Label("No upcoming deadlines."));
            return;
        }
        for (Deadline d : ds) {
            HBox row = new HBox(10);
            row.getStyleClass().add("list-row");
            row.setAlignment(Pos.CENTER_LEFT);

            Label days = new Label(prettyCountdown(d.daysLeft()));
            days.getStyleClass().addAll("count-chip");

            VBox mid = new VBox(1);
            Label t = new Label(d.getTitle());
            t.getStyleClass().add("row-title");
            Label sub = new Label(d.getDate().format(D_FMT)
                    + (d.isSynced() ? "  \u00B7  reminder set " + d.getReminderDays() + " days before" : ""));
            sub.getStyleClass().add("row-sub");
            mid.getChildren().addAll(t, sub);

            HBox spacer = new HBox();
            HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

            Button bell = new Button("Remind me");
            bell.getStyleClass().add("btn-ghost");
            bell.setOnAction(e -> Toast.success(ctx,
                    "Reminder set for \"" + d.getTitle() + "\" - you will be notified "
                            + d.getReminderDays() + " days before " + d.getDate().format(D_FMT) + "."));

            row.getChildren().addAll(days, mid, spacer, bell);
            deadlineBox.getChildren().add(row);
        }
    }

    private String prettyCountdown(long days) {
        if (days == 0) return "TODAY";
        return days + "d";
    }

    private void buildMessagePreview(Case c) {
        List<ClientMessage> msgs = ctx.dataStore().messagesForCase(c.getId());
        if (msgs.isEmpty()) {
            messagePreviewBox.getChildren().add(new Label("No messages from your attorney yet."));
            return;
        }
        ClientMessage last = msgs.get(msgs.size() - 1);
        long unread = msgs.stream().filter(m -> m.isFromLawyer() && !m.isReadByClient()).count();

        Label title = new Label("Latest message"
                + (unread > 0 ? "  (" + unread + " unread)" : ""));
        title.getStyleClass().add("section-title");

        Label subj = new Label(last.getSubject());
        subj.getStyleClass().add("row-title");
        Label body = new Label(last.getBody());
        body.getStyleClass().add("row-sub");
        body.setWrapText(true);
        Label time = new Label(last.getSentAt().format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")));
        time.getStyleClass().add("row-sub");

        messagePreviewBox.getChildren().addAll(title, subj, body, time);
    }

    private String firstname(String fullName) {
        return fullName.contains(" ") ? fullName.substring(0, fullName.indexOf(" ")) : fullName;
    }
}
