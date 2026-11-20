package za.co.mongala.legal.backend.service;

import za.co.mongala.legal.backend.auth.PasswordHasher;
import za.co.mongala.legal.model.Role;
import za.co.mongala.legal.model.User;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** User persistence, credential verification and account lifecycle (FR8). */
public class UserService {

    private final DataSource ds;

    public UserService(DataSource ds) {
        this.ds = ds;
    }

    public void create(String id, String name, String email, String plainPassword, Role role) {
        String hash = PasswordHasher.hash(plainPassword);
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO users(id, name, email, password_hash, role, created_at, active) VALUES (?,?,?,?,?,?,TRUE)")) {
            ps.setString(1, id);
            ps.setString(2, name);
            ps.setString(3, email);
            ps.setString(4, hash);
            ps.setString(5, role.name());
            ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create user", e);
        }
    }

    public User findByEmail(String email) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM users WHERE lower(email) = lower(?)")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not query user", e);
        }
    }

    public List<User> listAll() {
        List<User> out = new ArrayList<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM users ORDER BY name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(map(rs));
        } catch (SQLException e) {
            throw new IllegalStateException("Could not list users", e);
        }
        return out;
    }

    /** Returns the user (without usable password) when credentials are valid, else null. */
    public User verify(String email, String password) {
        User u = findByEmail(email);
        if (u == null || u.getPassword() == null || u.getPassword().isBlank()) return null;
        if (!u.isActive()) return null;
        return PasswordHasher.verify(password, u.getPassword()) ? u : null;
    }

    public boolean isDisabled(String email) {
        User u = findByEmail(email);
        return u != null && !u.isActive();
    }

    public void setActive(String id, boolean active) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE users SET active = ? WHERE id = ?")) {
            ps.setBoolean(1, active);
            ps.setString(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not update user", e);
        }
    }

    private User map(ResultSet rs) throws SQLException {
        boolean active = true;
        try { active = rs.getBoolean("active"); } catch (SQLException ignored) {}
        return new User(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getString("password_hash"),
                Role.valueOf(rs.getString("role")),
                active);
    }
}
