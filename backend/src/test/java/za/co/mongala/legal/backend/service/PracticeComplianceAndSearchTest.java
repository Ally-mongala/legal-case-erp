package za.co.mongala.legal.backend.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.mongala.legal.model.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Automated tests for trust accounting, precedent search, UX & offline PWA features:
 *  1. LPC Section 86 Trust Account Ledger, overdraft block & PDF trust statement
 *  2. Firm-wide full-text precedent & extraction search
 *  3. Offline PWA service worker, web manifest & accessibility assets on classpath
 */
class PracticeComplianceAndSearchTest {

    private HikariDataSource ds;
    private CaseService cases;
    private DocumentService documents;
    private ExtractionService extractions;
    private TrustAccountingService trust;
    private PrecedentSearchService precedentSearch;

    @BeforeEach
    void setUp() throws Exception {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:pcs_" + UUID.randomUUID().toString().replace("-", "")
                + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        cfg.setMaximumPoolSize(4);
        ds = new HikariDataSource(cfg);

        try (InputStream in = getClass().getResourceAsStream("/db/schema.sql");
             Connection c = ds.getConnection();
             Statement st = c.createStatement()) {
            assertNotNull(in);
            st.execute(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }

        cases = new CaseService(ds);
        documents = new DocumentService(ds);
        extractions = new ExtractionService(ds);
        trust = new TrustAccountingService(ds);
        precedentSearch = new PrecedentSearchService(ds);

        cases.insert(new Case("c1", "C-2026-014", "Mokoena v Siyaya Transport (Pty) Ltd",
                "u2", "Thabo Mokoena", "Siyaya Transport (Pty) Ltd",
                CaseStatus.IN_LITIGATION, "Personal Injury", "Johannesburg High Court",
                "Motor vehicle collision claim", LocalDate.of(2026, 11, 2), 1250000));
    }

    @AfterEach
    void tearDown() {
        if (ds != null) ds.close();
    }

    @Test
    @DisplayName("1. Section 86 trust ledger tracks balances, blocks overdrafts, and exports PDF statement")
    void trustLedgerTracksBalanceAndBlocksOverdraft() throws Exception {
        trust.recordTransaction("c1", LocalDate.of(2026, 11, 5), "DEPOSIT",
                "Client trust deposit", "EFT-001", 10000.00, "Ally Mongala");
        trust.recordTransaction("c1", LocalDate.of(2026, 11, 10), "TRANSFER_TO_BUSINESS",
                "Settle interim fee note", "TRF-002", 3500.00, "Ally Mongala");

        var st = trust.statementForCase("c1");
        assertEquals(10000.00, st.totalDeposits(), 0.01);
        assertEquals(3500.00, st.totalTransfersOut(), 0.01);
        assertEquals(6500.00, st.trustBalance(), 0.01);

        // Overdraft attempt must be rejected under Section 86
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                trust.recordTransaction("c1", LocalDate.of(2026, 11, 12), "TRANSFER_TO_BUSINESS",
                        "Excess transfer", "TRF-BAD", 7000.00, "Ally Mongala"));
        assertTrue(ex.getMessage().contains("Section 86"));
        assertEquals(6500.00, trust.statementForCase("c1").trustBalance(), 0.01);

        byte[] pdf = trust.generateTrustStatementPdf(cases.findById("c1"), st);
        try (PDDocument doc = PDDocument.load(pdf)) {
            String txt = new PDFTextStripper().getText(doc);
            assertTrue(txt.contains("TRUST ACCOUNT"));
            assertTrue(txt.contains("6,500.00"));
        }
    }

    @Test
    @DisplayName("2. PrecedentSearchService searches processed full text and ignores blocked documents")
    void precedentSearchFindsProcessedAndSkipsRejected() {
        LegalDocument okDoc = new LegalDocument("d1", "c1", "Pleading_Mokoena.pdf", "seed://1", "Ally",
                LocalDateTime.now(), 80, LegalDocument.ProcessingStatus.PROCESSED);
        documents.insert(okDoc);
        documents.setFullText("d1", "The plaintiff sustained injuries to his cervical spine following a collision on the N1 highway.");
        extractions.insertAll("d1", List.of(new Extraction("Claim Amount", "R1,250,000.00", 0.95, "R1,250,000.00", 1)));

        LegalDocument rejDoc = new LegalDocument("d2", "c1", "Blocked_File.pdf", "seed://2", "Ally",
                LocalDateTime.now(), 40, LegalDocument.ProcessingStatus.REJECTED, "PDF", true, "Wrong matter");
        documents.insert(rejDoc);
        documents.setFullText("d2", "cervical spine secret rejected content");

        var hits = precedentSearch.search("cervical spine");
        assertEquals(1, hits.size(), "Only PROCESSED documents may appear in precedent search");
        assertEquals("d1", hits.get(0).documentId());
        assertTrue(hits.get(0).snippet().toLowerCase().contains("cervical spine"));
    }

    @Test
    @DisplayName("3. PWA manifest, offline service worker and accessibility CSS/HTML ship on classpath")
    void pwaAndAccessibilityAssetsExistOnClasspath() throws Exception {
        for (String res : List.of("/web/manifest.json", "/web/sw.js", "/web/index.html", "/web/app.css")) {
            try (InputStream in = getClass().getResourceAsStream(res)) {
                assertNotNull(in, res + " must be packaged in resources");
                String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                assertFalse(content.isBlank());
            }
        }
    }

    @Test
    @DisplayName("4. Firm billing overview links AI-extracted claim quantum, fee ratio alerts, and trust settlement")
    void firmBillingOverviewSettlesFromTrustAndChecksAiClaimRatio() {
        BillingService billing = new BillingService(ds);
        LegalDocument d1 = new LegalDocument("d1", "c1", "Summons.pdf", "seed://s", "Ally",
                LocalDateTime.now(), 100, LegalDocument.ProcessingStatus.PROCESSED);
        documents.insert(d1);
        extractions.insertAll("d1", List.of(
                new Extraction("Claim Amount", "R20,000.00", 0.96, "sum of R20,000.00", 1)
        ));

        // Add R6,000 ex VAT -> R6,900 incl. 15% VAT (34.5% of AI-extracted R20,000 -> highFeeRatioWarning = true!)
        billing.addEntry("c1", LocalDate.of(2026, 11, 5), "TIME", "AI-assisted pleading review", 4.0, 1500.0, 0, "Ally Mongala");
        trust.recordTransaction("c1", LocalDate.of(2026, 11, 3), "DEPOSIT", "Trust deposit", "TR-1", 10000.0, "Ally Mongala");

        var ovBefore = billing.firmOverview(cases, trust, documents, extractions);
        var rowBefore = ovBefore.matters().get(0);
        assertEquals("AI_EXTRACTED", rowBefore.claimSource());
        assertEquals(20000.0, rowBefore.effectiveClaimAmount(), 0.01);
        assertEquals(6900.0, rowBefore.totalInclVat(), 0.01);
        assertEquals("UNPAID", rowBefore.paymentStatus());
        assertTrue(rowBefore.highFeeRatioWarning(), "Fee exceeding 25% of AI-extracted claim quantum must raise alert");

        // Settle full R6,900 from Section 86 trust
        trust.recordTransaction("c1", LocalDate.of(2026, 11, 8), "TRANSFER_TO_BUSINESS",
                "Settlement of INV-C-2026-014", "INV-C-2026-014", 6900.0, "Ally Mongala");

        var rowAfter = billing.matterRow(cases.findById("c1"), cases, trust, documents, extractions);
        assertEquals("PAID", rowAfter.paymentStatus());
        assertEquals(0.0, rowAfter.outstandingBalance(), 0.01);
        assertEquals(3100.0, rowAfter.availableTrustBalance(), 0.01);
    }

    @Test
    @DisplayName("5. User onboarding provisions PBKDF2-secured client account and links existing matter")
    void userOnboardingCreatesAndLinksClientToMatter() {
        UserService users = new UserService(ds);
        cases.insert(new Case("c5", "C-2026-019", "Dlamini v Nedbank Ltd",
                "u11", "Sibongile Dlamini", "Nedbank Ltd",
                CaseStatus.ON_HOLD, "Banking Law", "Johannesburg High Court",
                "Unauthorised transactions", LocalDate.of(2026, 11, 9), 340000));

        users.create("u_new", "Sibongile Dlamini", "sibongile@example.com", "securePass2026", Role.CLIENT);
        cases.assignClient("c5", "u_new", "Sibongile Dlamini");

        User verified = users.verify("sibongile@example.com", "securePass2026");
        assertNotNull(verified);
        assertEquals(Role.CLIENT, verified.getRole());
        List<Case> clientCases = cases.byClient("u_new");
        assertEquals(1, clientCases.size());
        assertEquals("C-2026-019", clientCases.get(0).getReference());
    }

    @Test
    @DisplayName("6. ExcelWorkbookService exports valid 4-sheet .xlsx workbook with AI extractions & financials")
    void excelWorkbookExportsFourStructuredSheets() throws Exception {
        BillingService billing = new BillingService(ds);
        DeadlineService deadlines = new DeadlineService(ds);
        ExcelWorkbookService excel = new ExcelWorkbookService();

        LegalDocument d1 = new LegalDocument("d1", "c1", "Statement_of_Claim.pdf", "seed://1", "Ally",
                LocalDateTime.now(), 120, LegalDocument.ProcessingStatus.PROCESSED);
        documents.insert(d1);
        extractions.insertAll("d1", List.of(
                new Extraction("Case Number", "GP/JHB/2026/000841", 0.98, "GP/JHB/2026/000841", 1),
                new Extraction("Claim Amount", "R1,250,000.00", 0.95, "R1,250,000.00", 1)
        ));
        billing.addEntry("c1", LocalDate.of(2026, 11, 4), "TIME", "Drafting summons", 2.0, 1850.0, 0, "Ally Mongala");
        trust.recordTransaction("c1", LocalDate.of(2026, 11, 2), "DEPOSIT", "Retainer", "TR-01", 15000.0, "Ally Mongala");
        deadlines.insert(new Deadline("dl1", "c1", "C-2026-014", "Hearing", LocalDate.of(2026, 12, 14), Deadline.DeadlineType.HEARING, 5, true));

        byte[] bytes = excel.generateFirmWorkbook(cases, billing, trust, documents, extractions, deadlines);
        assertTrue(bytes.length > 2000, "Generated .xlsx workbook must be non-empty");

        try (var wb = org.apache.poi.ss.usermodel.WorkbookFactory.create(new java.io.ByteArrayInputStream(bytes))) {
            assertEquals(4, wb.getNumberOfSheets());
            assertEquals("Matters & Finance", wb.getSheetName(0));
            assertEquals("AI Extractions & Quality", wb.getSheetName(1));
            assertEquals("Billing & 15% VAT", wb.getSheetName(2));
            assertEquals("Trust & Deadlines", wb.getSheetName(3));
            assertEquals("GP/JHB/2026/000841", wb.getSheet("AI Extractions & Quality").getRow(1).getCell(6).getStringCellValue());
        }
    }
}
