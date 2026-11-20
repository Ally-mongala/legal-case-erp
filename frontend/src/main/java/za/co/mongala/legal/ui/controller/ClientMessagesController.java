package za.co.mongala.legal.ui.controller;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import za.co.mongala.legal.model.Case;
import za.co.mongala.legal.model.ClientMessage;
import za.co.mongala.legal.ui.AppContext;
import za.co.mongala.legal.ui.Toast;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Client messages screen (FXML: fxml/views/client_messages.fxml).
 *
 * A simple secure thread between the firm and the client (FR5, US3).
 * In production this sits behind OAuth2 scopes (read:client) and the messages
 * would be delivered through the portal / email. Here everything is in-memory,
 * so a message sent by the client immediately appears in the lawyer's case
 * detail screen.
 */
public class ClientMessagesController {

    private final AppContext ctx;

    @FXML private VBox threadBox;
    @FXML private TextArea composeArea;
    @FXML private Button sendButton;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm");

    public ClientMessagesController(AppContext ctx) {
        this.ctx = ctx;
    }

    @FXML
    private void initialize() {
        List<Case> cases = ctx.dataStore().findCasesForClient(ctx.currentUser().getId());
        if (cases.isEmpty()) {
            threadBox.getChildren().add(new Label("No case linked to your account."));
            sendButton.setDisable(true);
            return;
        }
        Case c = cases.get(0);

        // Mark lawyer messages as read once the client opens the thread
        ctx.dataStore().messagesForCase(c.getId()).stream()
                .filter(m -> m.isFromLawyer() && !m.isReadByClient())
                .forEach(ClientMessage::markRead);

        refresh(c);

        sendButton.setOnAction(e -> send(c));
        composeArea.setOnKeyPressed(e -> {
            if (e.isControlDown() && e.getCode() == javafx.scene.input.KeyCode.ENTER) send(c);
        });
    }

    private void refresh(Case c) {
        threadBox.getChildren().clear();
        List<ClientMessage> msgs = ctx.dataStore().messagesForCase(c.getId());
        if (msgs.isEmpty()) {
            threadBox.getChildren().add(new Label("No messages yet. Send your attorney a note below."));
            return;
        }
        for (ClientMessage m : msgs) {
            HBox row = new HBox();
            boolean mine = !m.isFromLawyer();
            row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

            VBox bubble = new VBox(2);
            bubble.getStyleClass().addAll("bubble", mine ? "bubble-mine" : "bubble-theirs");
            bubble.setMaxWidth(560);

            Label from = new Label(m.getFromName() + "  \u00B7  " + m.getSentAt().format(DT_FMT));
            from.getStyleClass().add("bubble-meta");
            Label body = new Label(m.getBody());
            body.setWrapText(true);
            body.getStyleClass().add("bubble-text");
            bubble.getChildren().addAll(from, body);

            row.getChildren().add(bubble);
            threadBox.getChildren().add(row);
        }
    }

    private void send(Case c) {
        String text = composeArea.getText();
        if (text.isBlank()) return;

        ctx.workflow().sendClientMessage(c, text);

        composeArea.clear();
        refresh(c);
        Toast.success(ctx, "Message sent to your attorney.");
    }
}
