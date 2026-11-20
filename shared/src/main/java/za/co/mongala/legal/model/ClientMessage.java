package za.co.mongala.legal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * A message between lawyer and client in the client portal (FR5 / US3).
 * `readByClient` / `readByLawyer` drive the unread-alert icon on both sides.
 */
public class ClientMessage {
    private final String id;
    private final String caseId;
    private final String fromName;
    private final String subject;
    private final String body;
    private final LocalDateTime sentAt;
    private final boolean fromLawyer;
    private boolean readByClient;
    private boolean readByLawyer;

    @JsonCreator
    public ClientMessage(@JsonProperty("id") String id,
                         @JsonProperty("caseId") String caseId,
                         @JsonProperty("fromName") String fromName,
                         @JsonProperty("subject") String subject,
                         @JsonProperty("body") String body,
                         @JsonProperty("sentAt") LocalDateTime sentAt,
                         @JsonProperty("fromLawyer") boolean fromLawyer,
                         @JsonProperty("readByClient") boolean readByClient,
                         @JsonProperty("readByLawyer") boolean readByLawyer) {
        this.id = id;
        this.caseId = caseId;
        this.fromName = fromName;
        this.subject = subject;
        this.body = body;
        this.sentAt = sentAt;
        this.fromLawyer = fromLawyer;
        this.readByClient = readByClient;
        this.readByLawyer = readByLawyer;
    }

    public String getId() { return id; }
    public String getCaseId() { return caseId; }
    public String getFromName() { return fromName; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public LocalDateTime getSentAt() { return sentAt; }
    public boolean isFromLawyer() { return fromLawyer; }
    public boolean isReadByClient() { return readByClient; }
    public void markRead() { this.readByClient = true; }
    public boolean isReadByLawyer() { return readByLawyer; }
    public void markReadByLawyer() { this.readByLawyer = true; }
}
