package za.co.mongala.legal.backend.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import za.co.mongala.legal.backend.config.AppConfig;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Database bootstrap. Uses H2 by default (zero-setup, file-backed, runs anywhere).
 * To use PostgreSQL instead, swap the JDBC URL/driver below - the schema is
 * standard SQL and the services use plain JDBC, so nothing else changes.
 *
 * The schema mirrors Assignment 2 section 10: users, cases, documents,
 * extractions, summaries, deadlines, messages + audit_log and processing_jobs.
 */
public class Database {

    private final HikariDataSource dataSource;
    private final Path uploadDir;

    public Database(AppConfig cfg) {
        Path dataDir = Path.of(cfg.dataDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dataDir);
            this.uploadDir = Files.createDirectories(dataDir.resolve("uploads"));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create data directory " + dataDir, e);
        }

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(cfg.dbUrl());
        // Name the driver explicitly. Auto-discovery is fragile in a shaded JAR.
        if (cfg.dbUrl().startsWith("jdbc:postgresql:")) {
            hc.setDriverClassName("org.postgresql.Driver");
        } else if (cfg.dbUrl().startsWith("jdbc:h2:")) {
            hc.setDriverClassName("org.h2.Driver");
        }
        hc.setUsername(cfg.dbUser());
        hc.setPassword(cfg.dbPassword());
        hc.setMaximumPoolSize(8);
        this.dataSource = new HikariDataSource(hc);

        runSchema();
    }

    private void runSchema() {
        String sql;
        try (InputStream in = getClass().getResourceAsStream("/db/schema.sql")) {
            if (in == null) throw new IllegalStateException("schema.sql missing from classpath");
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read schema.sql", e);
        }
        // PostgreSQL has no CLOB - use TEXT (H2 in PostgreSQL mode accepts TEXT too)
        sql = sql.replace("CLOB", "TEXT");
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to apply schema", e);
        }
    }

    public DataSource dataSource() { return dataSource; }

    /** Local file storage for uploaded documents (role of S3/Azure Blob in production). */
    public Path uploadDir() { return uploadDir; }

    public void close() {
        if (dataSource != null) dataSource.close();
    }
}
