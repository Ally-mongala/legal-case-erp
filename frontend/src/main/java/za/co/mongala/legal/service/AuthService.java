package za.co.mongala.legal.service;

import za.co.mongala.legal.model.Role;
import za.co.mongala.legal.model.User;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AuthService - DEMO mode credential validation.
 *
 * In CONNECTED mode the UI calls the backend's POST /api/auth/login (OAuth2 +
 * JWT with scopes, Assignment 2 section 11) via {@link za.co.mongala.legal.client.RemoteAuthService}.
 */
public class AuthService implements IAuthService {

    private final Map<String, User> usersByEmail = new LinkedHashMap<>();

    public AuthService() {
        seedUsers();
    }

    private void seedUsers() {
        usersByEmail.put("ally@mongalalaw.co.za",
                new User("u1", "Ally Mongala", "ally@mongalalaw.co.za", "lawyer123", Role.LAWYER));
        usersByEmail.put("thabo@example.com",
                new User("u2", "Thabo Mokoena", "thabo@example.com", "client123", Role.CLIENT));
        usersByEmail.put("elna@example.com",
                new User("u3", "Elna van Wyk", "elna@example.com", "client123", Role.CLIENT));
        usersByEmail.put("admin@mongalalaw.co.za",
                new User("u4", "System Admin", "admin@mongalalaw.co.za", "admin123", Role.ADMIN));
    }

    /**
     * @return the logged-in user or null if the credentials are invalid.
     */
    public User authenticate(String email, String password) {
        if (email == null || password == null) return null;
        User u = usersByEmail.get(email.trim().toLowerCase());
        if (u != null && u.getPassword().equals(password)) {
            return u;
        }
        return null;
    }

    public User findByEmail(String email) {
        return usersByEmail.get(email.trim().toLowerCase());
    }
}
