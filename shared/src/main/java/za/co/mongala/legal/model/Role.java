package za.co.mongala.legal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * System roles - mirrors the role-based access control (RBAC) requirement NFR1.
 * The front-end hides/shows screens depending on the logged-in role; the
 * backend issues JWTs with the role and its permitted scopes.
 */
public enum Role {
    LAWYER("Lawyer"),
    CLIENT("Client"),
    ADMIN("Administrator");

    private final String displayName;

    Role(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
