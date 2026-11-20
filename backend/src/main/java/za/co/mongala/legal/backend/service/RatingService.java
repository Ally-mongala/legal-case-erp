package za.co.mongala.legal.backend.service;

import za.co.mongala.legal.model.Rating;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Client ratings of lawyers (FR7). */
public class RatingService {

    private final DataSource ds;

    public RatingService(DataSource ds) {
        this.ds = ds;
    }

    public void insert(Rating r) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO ratings(id, case_id, client_id, lawyer_name, score, comment, created_at) VALUES (?,?,?,?,?,?,?)")) {
            ps.setString(1, r.getId());
            ps.setString(2, r.getCaseId());
            ps.setString(3, r.getClientId());
            ps.setString(4, r.getLawyerName());
            ps.setInt(5, r.getScore());
            ps.setString(6, r.getComment());
            ps.setTimestamp(7, Timestamp.valueOf(r.getCreatedAt()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not insert rating", e);
        }
    }

    public List<Rating> all() {
        List<Rating> out = new ArrayList<>();
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM ratings ORDER BY created_at DESC")) {
            while (rs.next()) {
                out.add(new Rating(rs.getString("id"), rs.getString("case_id"),
                        rs.getString("client_id"), rs.getString("lawyer_name"),
                        rs.getInt("score"), rs.getString("comment"),
                        rs.getTimestamp("created_at").toLocalDateTime()));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not query ratings", e);
        }
        return out;
    }

    public double average() {
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT AVG(score) FROM ratings")) {
            return rs.next() ? rs.getDouble(1) : 0.0;
        } catch (SQLException e) {
            return 0.0;
        }
    }
}
