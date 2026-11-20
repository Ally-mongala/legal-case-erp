package za.co.mongala.legal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/**
 * A legal matter. Mirrors the `cases` table from Assignment 2 section 10:
 * id, title, client_id, status, created_at, updated_at.
 */
public class Case {
    private final String id;
    private final String reference;      // e.g. C-2026-014
    private final String title;
    private final String clientId;       // FK -> user id (Client)
    private final String clientName;
    private final String opposingParty;
    private final CaseStatus status;
    private final String matterType;
    private final String court;
    private final String description;
    private final LocalDate openedDate;
    private final double amountInvolved;

    @JsonCreator
    public Case(@JsonProperty("id") String id,
                @JsonProperty("reference") String reference,
                @JsonProperty("title") String title,
                @JsonProperty("clientId") String clientId,
                @JsonProperty("clientName") String clientName,
                @JsonProperty("opposingParty") String opposingParty,
                @JsonProperty("status") CaseStatus status,
                @JsonProperty("matterType") String matterType,
                @JsonProperty("court") String court,
                @JsonProperty("description") String description,
                @JsonProperty("openedDate") LocalDate openedDate,
                @JsonProperty("amountInvolved") double amountInvolved) {
        this.id = id;
        this.reference = reference;
        this.title = title;
        this.clientId = clientId;
        this.clientName = clientName;
        this.opposingParty = opposingParty;
        this.status = status;
        this.matterType = matterType;
        this.court = court;
        this.description = description;
        this.openedDate = openedDate;
        this.amountInvolved = amountInvolved;
    }

    public String getId() { return id; }
    public String getReference() { return reference; }
    public String getTitle() { return title; }
    public String getClientId() { return clientId; }
    public String getClientName() { return clientName; }
    public String getOpposingParty() { return opposingParty; }
    public CaseStatus getStatus() { return status; }
    public String getMatterType() { return matterType; }
    public String getCourt() { return court; }
    public String getDescription() { return description; }
    public LocalDate getOpenedDate() { return openedDate; }
    public double getAmountInvolved() { return amountInvolved; }

    public String getAmountFormatted() {
        return String.format("R %,1.2f", amountInvolved).replace(".00", "");
    }
}
