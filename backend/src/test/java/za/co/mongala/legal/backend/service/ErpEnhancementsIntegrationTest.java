package za.co.mongala.legal.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import za.co.mongala.legal.backend.security.EncryptionUtil;
import za.co.mongala.legal.model.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests verifying the core ERP enhancements:
 *  1. Conflict-of-interest checker (LPC rules)
 *  2. Practitioner AI correction harvesting into JSONL retraining dataset (ETR-04)
 *  3. Time billing, 15% SA VAT & PDFBox fee-note invoice generation
 *  4. Live audit-chain verification & AES-256-GCM encrypted backup + restore check (NFR-02)
 */
class ErpEnhancementsIntegrationTest {

    @TempDir
    Path tempDir;

    private HikariDataSource ds;
    private Path uploadDir;
    private ObjectMapper mapper;
    private UserService users;
    private CaseService cases;
    private SummaryService summaries;
    private AuditService audit;
    private TrainingService training;
    private ConflictCheckService conflicts;
    private BillingService billing;
    private BackupService backups;

    @BeforeEach
    void setUp() throws Exception {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:enh_" + UUID.randomUUID().toString().replace("-", "")
                + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        cfg.setMaximumPoolSize(4);
        ds = new HikariDataSource(cfg);

        try (InputStream in = getClass().getResourceAsStream("/db/schema.sql");
             Connection c = ds.getConnection();
             Statement st = c.createStatement()) {
            assertNotNull(in, "schema.sql must be on classpath");
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            st.execute(sql);
        }

        uploadDir = Files.createDirectories(tempDir.resolve("uploads"));
        mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        EncryptionUtil enc = new EncryptionUtil("unit-test-passphrase-2026");

        users = new UserService(ds);
        cases = new CaseService(ds);
        summaries = new SummaryService(ds);
        audit = new AuditService(ds);
        training = new TrainingService(ds);
        conflicts = new ConflictCheckService(ds);
        billing = new BillingService(ds);
        backups = new BackupService(ds, enc, uploadDir, mapper);

        users.create("u1", "Ally Mongala", "ally@mongalalaw.co.za", "lawyer123", Role.LAWYER);
        users.create("u2", "Thabo Mokoena", "thabo@example.com", "client123", Role.CLIENT);
        cases.insert(new Case("c1", "C-2026-014", "Mokoena v Siyaya Transport (Pty) Ltd",
                "u2", "Thabo Mokoena", "Siyaya Transport (Pty) Ltd",
                CaseStatus.IN_LITIGATION, "Personal Injury", "Johannesburg High Court",
                "Motor collision claim", LocalDate.of(2026, 11, 2), 1250000));
    }

    @AfterEach
    void tearDown() {
        if (ds != null) ds.close();
    }

    @Test
    @DisplayName("1a. Conflict check flags CRITICAL when prospective opposing party is an existing client")
    void conflictDetectsAdverseAgainstOwnClient() {
        var rep = conflicts.check("Acme Logistics (Pty) Ltd", "Thabo Mokoena", null);
        assertTrue(rep.hasCriticalConflict(), "Suing an existing firm client must raise a CRITICAL conflict");
        assertEquals("ADVERSE_TO_EXISTING_CLIENT", rep.matches().get(0).conflictType());
    }

    @Test
    @DisplayName("1b. Conflict check flags CRITICAL when prospective client is an active opposing party")
    void conflictDetectsRepresentingActiveOpponent() {
        var rep = conflicts.check("Siyaya Transport", "Some Third Party", null);
        assertTrue(rep.hasCriticalConflict(), "Representing an active opposing party must raise a CRITICAL conflict");
        assertEquals("CLIENT_IS_EXISTING_OPPONENT", rep.matches().get(0).conflictType());
    }

    @Test
    @DisplayName("1c. Conflict check passes cleanly for unrelated parties")
    void conflictPassesCleanlyForUnrelatedMandate() {
        var rep = conflicts.check("Lerato Khumalo", "Protea Retail Holdings", null);
        assertFalse(rep.hasCriticalConflict());
        assertFalse(rep.hasAnyMatch());
    }

    @Test
    @DisplayName("2. Practitioner summary edit harvests JSONL sample and queues PENDING retraining run")
    void correctionHarvestingUpdatesDatasetAndTrainingRun() throws Exception {
        Summary orig = new Summary("s1", "d1", "c1", "AI facts", "AI issues", "AI status",
                1, Summary.SummaryStatus.DRAFT, "AI Worker", LocalDateTime.now(), null);
        summaries.insert(orig);

        training.recordSummaryCorrection(orig, "Lawyer corrected facts", "AI issues", "Updated status",
                "Ally Mongala", uploadDir, mapper);

        Dataset dsRow = training.findById(TrainingService.CORRECTIONS_DATASET_ID);
        assertNotNull(dsRow, "Live corrections dataset must be registered");
        assertEquals(1L, dsRow.getRecords());
        assertTrue(Files.exists(uploadDir.resolve(dsRow.getStoragePath())));
        assertEquals(1, training.listCorrections().size());
        assertTrue(training.runs().stream().anyMatch(
                r -> TrainingService.CORRECTIONS_DATASET_ID.equals(r.getDatasetId())
                        && r.getStatus() == TrainingRun.Status.PENDING));
    }

    @Test
    @DisplayName("4. Time & disbursement billing computes 15% SA VAT and generates readable PDF invoice")
    void billingComputesVatAndGeneratesPdfInvoice() throws Exception {
        billing.addEntry("c1", LocalDate.of(2026, 5, 10), "TIME", "Consultation and drafting", 2.0, 1500.0, 0, "Ally Mongala");
        billing.addEntry("c1", LocalDate.of(2026, 5, 12), "DISBURSEMENT", "Sheriff service fee", 0, 0, 500.0, "Ally Mongala");

        var st = billing.statementForCase("c1");
        assertEquals(2.0, st.totalHours(), 0.001);
        assertEquals(3000.0, st.feesSubtotal(), 0.01);
        assertEquals(500.0, st.disbursementsSubtotal(), 0.01);
        assertEquals(3500.0, st.subtotalExVat(), 0.01);
        assertEquals(525.0, st.vatAmount(), 0.01);
        assertEquals(4025.0, st.totalInclVat(), 0.01);

        byte[] pdf = billing.generateInvoicePdf(cases.findById("c1"), st);
        assertTrue(pdf.length > 500, "Generated PDF must be non-empty");
        try (PDDocument doc = PDDocument.load(pdf)) {
            assertEquals(1, doc.getNumberOfPages());
            String text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains("MONGALA ATTORNEYS INC."));
            assertTrue(text.contains("C-2026-014"));
            assertTrue(text.contains("4,025.00"));
        }
    }

    @Test
    @DisplayName("5. AuditService.verifyChain and BackupService prove integrity and encrypted restore dry-run")
    void liveAuditVerifyAndEncryptedBackupRoundTrip() throws Exception {
        audit.info("Ally Mongala", "LOGIN", "Signed in");
        audit.success("Ally Mongala", "CASE_CREATED", "Created C-2026-014");

        var verifyOk = audit.verifyChain();
        assertTrue(verifyOk.intact());
        assertEquals(2, verifyOk.totalRows());

        var backup = backups.createEncryptedBackup("System Admin");
        assertTrue(backup.verified(), "Restore dry-run verification must succeed");
        assertTrue(backup.totalRows() >= 4);
        byte[] onDisk = backups.readBackupBytes(backup.filename());
        assertNotNull(onDisk);
        String rawOnDisk = new String(onDisk, StandardCharsets.UTF_8);
        assertFalse(rawOnDisk.contains("Thabo Mokoena"), "Encrypted backup on disk must not expose plaintext names");

        // Tamper with audit row and confirm verifyChain() catches it
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("UPDATE audit_log SET details = 'Forged detail' WHERE id = 1");
        }
        var verifyTampered = audit.verifyChain();
        assertFalse(verifyTampered.intact(), "verifyChain() must detect modified row");
        assertEquals(1, verifyTampered.brokenAtId());
    }
}
