package za.co.mongala.legal.backend.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import za.co.mongala.legal.backend.auth.LoginGuard;
import za.co.mongala.legal.backend.security.EncryptionUtil;
import za.co.mongala.legal.model.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Automated tests for the legal-practice & security enhancements:
 *  1. LoginGuard 3-attempt brute-force lockout & unlock
 *  2. Cross-document contradiction detector & master case timeline
 *  3. Editable Microsoft Word (.docx) legal template generation (Apache POI)
 *  4. Paginated High Court Hearing Bundle PDF compiler (Apache PDFBox)
 */
class LegalWorkflowEnhancementsTest {

    @TempDir
    Path tempDir;

    private HikariDataSource ds;
    private Path uploadDir;
    private CaseService cases;
    private DocumentService documents;
    private ExtractionService extractions;
    private SummaryService summaries;
    private DeadlineService deadlines;
    private CaseIntelligenceService intelligence;
    private LegalTemplateService templates;
    private CourtBundleService courtBundle;

    @BeforeEach
    void setUp() throws Exception {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:wf_" + UUID.randomUUID().toString().replace("-", "")
                + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        cfg.setMaximumPoolSize(4);
        ds = new HikariDataSource(cfg);

        try (InputStream in = getClass().getResourceAsStream("/db/schema.sql");
             Connection c = ds.getConnection();
             Statement st = c.createStatement()) {
            assertNotNull(in);
            st.execute(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }

        uploadDir = Files.createDirectories(tempDir.resolve("uploads"));
        EncryptionUtil enc = new EncryptionUtil("bundle-test-key-2026");

        cases = new CaseService(ds);
        documents = new DocumentService(ds);
        extractions = new ExtractionService(ds);
        summaries = new SummaryService(ds);
        deadlines = new DeadlineService(ds);
        intelligence = new CaseIntelligenceService(cases, documents, extractions, deadlines);
        templates = new LegalTemplateService();
        courtBundle = new CourtBundleService(documents, enc, uploadDir);

        cases.insert(new Case("c1", "C-2026-014", "Mokoena v Siyaya Transport (Pty) Ltd",
                "u2", "Thabo Mokoena", "Siyaya Transport (Pty) Ltd",
                CaseStatus.IN_LITIGATION, "Personal Injury", "Johannesburg High Court",
                "Motor vehicle collision claim", LocalDate.of(2026, 11, 2), 1250000));
        cases.insert(new Case("c3", "C-2026-011", "Ndlovu Family Trust v City of Johannesburg",
                "u9", "Ndlovu Family Trust", "City of Johannesburg",
                CaseStatus.OPEN, "Municipal Law", "Gauteng Division",
                "Municipal rates review", LocalDate.of(2026, 11, 4), 0));
    }

    @AfterEach
    void tearDown() {
        if (ds != null) ds.close();
    }

    @Test
    @DisplayName("1a. LoginGuard locks account on the 3rd consecutive failed attempt and unlocks on reset")
    void loginGuardLocksAfterThreeFailedAttempts() {
        LoginGuard guard = new LoginGuard(3, Duration.ofMinutes(15));
        assertEquals(3, guard.maxAttempts());

        var f1 = guard.recordFailure("ally@mongalalaw.co.za");
        assertFalse(f1.locked());
        assertEquals(2, f1.remainingAttempts());

        var f2 = guard.recordFailure("ally@mongalalaw.co.za");
        assertFalse(f2.locked());
        assertEquals(1, f2.remainingAttempts());

        var f3 = guard.recordFailure("ally@mongalalaw.co.za");
        assertTrue(f3.locked(), "3rd failed attempt must lock the account");
        assertEquals(0, f3.remainingAttempts());
        assertTrue(guard.status("ALLy@mongalalaw.co.za").locked(), "Lock check must be case-insensitive");

        guard.reset("ally@mongalalaw.co.za");
        assertFalse(guard.status("ally@mongalalaw.co.za").locked());
        assertEquals(3, guard.status("ally@mongalalaw.co.za").remainingAttempts());
    }

    @Test
    @DisplayName("1b. LoginGuard automatically clears lock once cooldown window elapses")
    void loginGuardExpiresAfterCooldown() throws Exception {
        LoginGuard guard = new LoginGuard(3, Duration.ofMillis(60));
        guard.recordFailure("thabo@example.com");
        guard.recordFailure("thabo@example.com");
        assertTrue(guard.recordFailure("thabo@example.com").locked());

        Thread.sleep(90);
        assertFalse(guard.status("thabo@example.com").locked());
    }

    @Test
    @DisplayName("2. CaseIntelligenceService detects cross-document claim-amount contradiction and sorts timeline")
    void caseIntelligenceDetectsContradictionAndBuildsTimeline() {
        LegalDocument d1 = new LegalDocument("d1", "c1", "Summons.pdf", "seed://s", "Ally",
                LocalDateTime.now(), 100, LegalDocument.ProcessingStatus.PROCESSED);
        LegalDocument d2 = new LegalDocument("d2", "c1", "Amended_Quantum_Note.pdf", "seed://q", "Ally",
                LocalDateTime.now(), 100, LegalDocument.ProcessingStatus.PROCESSED);
        documents.insert(d1);
        documents.insert(d2);

        extractions.insertAll("d1", List.of(
                new Extraction("Claim Amount", "R1,250,000.00", 0.95, "sum of R1,250,000.00", 1),
                new Extraction("Hearing Date", "2026-12-14", 0.92, "14 December 2026", 1)
        ));
        extractions.insertAll("d2", List.of(
                new Extraction("Claim Amount", "R950,000.00", 0.90, "reduced sum of R950,000.00", 1)
        ));

        var rep = intelligence.analyse("c1");
        assertFalse(rep.consistent(), "Disagreeing claim amounts across two documents must flag contradiction");
        assertEquals(1, rep.contradictions().size());
        assertEquals("Claim Amount", rep.contradictions().get(0).fieldName());
        assertTrue(rep.timeline().size() >= 2, "Timeline must combine matter inception and extracted dates");
        for (int i = 1; i < rep.timeline().size(); i++) {
            assertFalse(rep.timeline().get(i).date().isBefore(rep.timeline().get(i - 1).date()),
                    "Timeline must be sorted chronologically");
        }
    }

    @Test
    @DisplayName("4. LegalTemplateService generates valid editable .docx pleadings pre-filled with case data")
    void generatesEditableWordTemplates() throws Exception {
        Case c = cases.findById("c1");
        List<Extraction> ex = List.of(new Extraction("Case Number", "GP/JHB/2026/000841", 0.96, "GP/JHB/2026/000841", 1));
        Summary s = new Summary("s1", "d1", "c1", "Collision on the N1 highway.", "Negligence.", "Set down.",
                1, Summary.SummaryStatus.APPROVED, "Ally", LocalDateTime.now(), LocalDateTime.now());

        for (String kind : List.of("letter_of_demand", "notice_to_defend", "discovery_affidavit")) {
            byte[] bytes = templates.generateDocx(kind, c, ex, s, List.of());
            assertTrue(bytes.length > 1000);
            try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
                String allText = doc.getParagraphs().stream().map(p -> p.getText()).reduce("", (a, b) -> a + "\n" + b);
                assertTrue(allText.contains("C-2026-014") || allText.contains("GP/JHB/2026/000841"),
                        kind + " must embed case reference/number");
            }
        }
    }

    @Test
    @DisplayName("5. CourtBundleService compiles cover index + exhibit pages with 'Bundle Page X of Y' pagination")
    void compilesPaginatedCourtBundlePdf() throws Exception {
        LegalDocument d1 = new LegalDocument("d1", "c1", "Statement_of_Claim.pdf", "seed://d1", "Ally Mongala",
                LocalDateTime.of(2026, 8, 3, 10, 0), 120, LegalDocument.ProcessingStatus.PROCESSED);
        documents.insert(d1);
        documents.setFullText("d1", "IN THE HIGH COURT OF SOUTH AFRICA\nTHABO MOKOENA Plaintiff\nSIYAYA TRANSPORT Defendant");

        byte[] pdf = courtBundle.compileBundlePdf(cases.findById("c1"));
        assertTrue(pdf.length > 800);
        try (PDDocument doc = PDDocument.load(pdf)) {
            assertEquals(2, doc.getNumberOfPages(), "Cover/index page + 1 exhibit page expected");
            String text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains("INDEXED COURT HEARING BUNDLE"));
            assertTrue(text.contains("Statement_of_Claim.pdf"));
            assertTrue(text.contains("Bundle Page 1 of 2"));
            assertTrue(text.contains("Bundle Page 2 of 2"));
        }
    }
}
