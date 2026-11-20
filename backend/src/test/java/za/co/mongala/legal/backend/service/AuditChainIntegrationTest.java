package za.co.mongala.legal.backend.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.mongala.legal.model.ActivityLog;

import javax.sql.DataSource;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the tamper-evident audit trail (FR-09, NFR-06).
 *
 * These run against a real in-memory H2 database rather than a mock, so they
 * exercise the actual SQL, the hash-chain construction and the recovery of the
 * previous hash between writes.
 *
 * Test case IDs map to the A3 Q5.1 test summary table.
 */
class AuditChainIntegrationTest {

    private HikariDataSource ds;
    private AuditService audit;

    @BeforeEach
    void setUp() throws Exception {
        HikariConfig cfg = new HikariConfig();
        // a uniquely named in-memory database per test keeps the tests isolated
        cfg.setJdbcUrl("jdbc:h2:mem:audit_" + UUID.randomUUID().toString().replace("-", "")
                + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        cfg.setMaximumPoolSize(2);
        ds = new HikariDataSource(cfg);

        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS audit_log (
                        id        INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        ts        TIMESTAMP,
                        user_name VARCHAR(200),
                        action    VARCHAR(60),
                        details   VARCHAR(1200),
                        level     VARCHAR(10),
                        prev_hash VARCHAR(64) DEFAULT '',
                        hash      VARCHAR(64) DEFAULT ''
                    )""");
        }
        audit = new AuditService(ds);
    }

    @AfterEach
    void tearDown() {
        if (ds != null) ds.close();
    }

    private record Row(int id, String ts, String user, String action,
                       String details, String level, String prevHash, String hash) {}

    private List<Row> readChain() throws Exception {
        List<Row> rows = new ArrayList<>();
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT id, ts, user_name, action, details, level, prev_hash, hash "
                             + "FROM audit_log ORDER BY id")) {
            while (rs.next()) {
                rows.add(new Row(rs.getInt(1), String.valueOf(rs.getTimestamp(2).toLocalDateTime()),
                        rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), rs.getString(7), rs.getString(8)));
            }
        }
        return rows;
    }

    private static String sha256(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] d = md.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** Recomputes each row's hash and confirms it links to its predecessor. */
    private static boolean chainIsIntact(List<Row> rows) throws Exception {
        String expectedPrev = "";
        for (Row r : rows) {
            if (!expectedPrev.equals(r.prevHash())) return false;
            String payload = r.ts() + "|" + r.user() + "|" + r.action() + "|"
                    + r.details() + "|" + r.level() + "|" + r.prevHash();
            if (!sha256(payload).equals(r.hash())) return false;
            expectedPrev = r.hash();
        }
        return true;
    }

    @Test
    @DisplayName("TC-I01: an audit entry is persisted with a non-empty hash")
    void writesEntryWithHash() throws Exception {
        audit.info("ally@mongalalaw.co.za", "LOGIN", "Successful login");

        List<Row> rows = readChain();
        assertEquals(1, rows.size());
        assertFalse(rows.get(0).hash().isBlank(), "every entry must carry a hash");
        assertEquals(64, rows.get(0).hash().length(), "SHA-256 renders as 64 hex characters");
    }

    @Test
    @DisplayName("TC-I02: the first entry has an empty previous hash (chain genesis)")
    void firstEntryHasEmptyPrevHash() throws Exception {
        audit.info("system", "STARTUP", "Service started");

        assertEquals("", readChain().get(0).prevHash(),
                "the genesis entry has no predecessor");
    }

    @Test
    @DisplayName("TC-I03: each entry chains to the hash of the entry before it")
    void entriesAreChained() throws Exception {
        audit.info("ally@mongalalaw.co.za", "LOGIN", "Successful login");
        audit.success("ally@mongalalaw.co.za", "DOCUMENT_UPLOAD", "affidavit.pdf");
        audit.warning("admin@mongalalaw.co.za", "ACCESS_REVOKED", "user u7 disabled");

        List<Row> rows = readChain();
        assertEquals(3, rows.size());

        assertEquals(rows.get(0).hash(), rows.get(1).prevHash(),
                "entry 2 must reference entry 1's hash");
        assertEquals(rows.get(1).hash(), rows.get(2).prevHash(),
                "entry 3 must reference entry 2's hash");
    }

    @Test
    @DisplayName("TC-I04: an unmodified chain verifies as intact")
    void intactChainVerifies() throws Exception {
        audit.info("u1", "LOGIN", "ok");
        audit.info("u1", "CASE_CREATE", "case c1 created");
        audit.info("u1", "DOCUMENT_UPLOAD", "summons.pdf");

        assertTrue(chainIsIntact(readChain()),
                "a chain that has not been tampered with must verify");
    }

    @Test
    @DisplayName("TC-I05: editing a historical row breaks verification (tamper evidence)")
    void tamperingBreaksTheChain() throws Exception {
        audit.info("u1", "LOGIN", "ok");
        audit.warning("admin", "ACCESS_REVOKED", "user u7 disabled");
        audit.info("u1", "CASE_CREATE", "case c1 created");

        assertTrue(chainIsIntact(readChain()), "precondition: chain starts intact");

        // simulate an attacker editing the middle record directly in the database
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("UPDATE audit_log SET details = 'nothing happened' WHERE id = 2");
        }

        assertFalse(chainIsIntact(readChain()),
                "a retrospective edit must be detectable through the hash chain");
    }

    @Test
    @DisplayName("TC-I06: deleting a row breaks verification")
    void deletionBreaksTheChain() throws Exception {
        audit.info("u1", "LOGIN", "ok");
        audit.info("u1", "CASE_CREATE", "case c1 created");
        audit.info("u1", "DOCUMENT_UPLOAD", "summons.pdf");

        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM audit_log WHERE id = 2");
        }

        assertFalse(chainIsIntact(readChain()),
                "removing an entry must break the link between its neighbours");
    }

    @Test
    @DisplayName("TC-I07: identical actions still produce different hashes (timestamp included)")
    void identicalActionsDifferentHashes() throws Exception {
        audit.info("u1", "LOGIN", "Successful login");
        Thread.sleep(5);
        audit.info("u1", "LOGIN", "Successful login");

        List<Row> rows = readChain();
        assertNotEquals(rows.get(0).hash(), rows.get(1).hash(),
                "the chained previous hash and timestamp must make each entry unique");
    }

    @Test
    @DisplayName("TC-I08: a null user is recorded as System rather than failing")
    void nullUserDefaultsToSystem() throws Exception {
        audit.log(null, "SCHEDULED_JOB", "nightly backup", ActivityLog.Level.INFO);

        assertEquals("System", readChain().get(0).user());
    }
}
