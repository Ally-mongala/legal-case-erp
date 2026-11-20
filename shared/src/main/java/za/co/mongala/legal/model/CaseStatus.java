package za.co.mongala.legal.model;

/**
 * Lifecycle of a legal case, shown as coloured badges across the UI.
 */
public enum CaseStatus {
    OPEN("Open", "#2e7d32"),
    IN_LITIGATION("In Litigation", "#1565c0"),
    PENDING_HEARING("Pending Hearing", "#e65100"),
    ON_HOLD("On Hold", "#6a1b9a"),
    CLOSED("Closed", "#616161");

    private final String displayName;
    private final String hexColor;

    CaseStatus(String displayName, String hexColor) {
        this.displayName = displayName;
        this.hexColor = hexColor;
    }

    public String getDisplayName() { return displayName; }
    public String getHexColor()   { return hexColor; }
}
