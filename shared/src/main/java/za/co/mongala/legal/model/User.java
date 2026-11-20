package za.co.mongala.legal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A system user. Passwords are stored hashed on the backend (PBKDF2); the
 * front-end only ever holds id/name/email/role from the JWT session.
 * `active` supports FR8 (admin revokes access): inactive users cannot log in.
 */
public class User {
    private final String id;
    private final String name;
    private final String email;
    private final String password;
    private final Role role;
    private final boolean active;

    @JsonCreator
    public User(@JsonProperty("id") String id,
                @JsonProperty("name") String name,
                @JsonProperty("email") String email,
                @JsonProperty("password") String password,
                @JsonProperty("role") Role role,
                @JsonProperty("active") boolean active) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.password = password;
        this.role = role;
        this.active = active;
    }

    public User(String id, String name, String email, String password, Role role) {
        this(id, name, email, password, role, true);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }

    /** Never serialized - password hashes must not leave the backend (NFR1). */
    @JsonIgnore
    public String getPassword() { return password; }

    public Role getRole() { return role; }
    public boolean isActive() { return active; }

    @Override
    public String toString() { return name + " (" + role.getDisplayName() + ")"; }
}
