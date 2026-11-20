package za.co.mongala.legal.backend.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.mail.MessagingException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.mongala.legal.backend.auth.JwtUtil;
import za.co.mongala.legal.model.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Email reminders (queue, no duplicates, sending) and the one-page case file PDF.
 */
class ReminderAndCaseFileTest {

    private HikariDataSource ds;
    private UserService users;
    private CaseService cases;
    private DeadlineService deadlines;
    private AuditService audit;
    private BillingService billing;
    private TrustAccountingService trust;

    @BeforeEach
    void setUp() throws Exception {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:rem_" + UUID.randomUUID().toString().replace("-", "")
                + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        cfg.setMaximumPoolSize(4);
        ds = new HikariDataSource(cfg);
        try (InputStream in = getClass().getResourceAsStream("/db/schema.sql");
             Connection c = ds.getConnection();
             Statement st = c.createStatement()) {
            st.execute(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        users = new UserService(ds);
        cases = new CaseService(ds);
        deadlines = new DeadlineService(ds);
        audit = new AuditService(ds);
        billing = new BillingService(ds);
        trust = new TrustAccountingService(ds);

        users.create("u2", "Thabo Mokoena", "thabo@example.com", "client123", Role.CLIENT);
        cases.insert(new Case("c1", "C-2026-014", "Mokoena v Siyaya Transport (Pty) Ltd",
                "u2", "Thabo Mokoena", "Siyaya Transport (Pty) Ltd",
                CaseStatus.IN_LITIGATION, "Personal Injury", "Johannesburg High Court",
                "Motor vehicle collision claim", LocalDate.of(2026, 11, 2), 1250000));
        // due in 3 days (inside the 7-day window)
        deadlines.insert(new Deadline("d1", "c1", "C-2026-014", "File heads of argument",
                LocalDate.now().plusDays(3), Deadline.DeadlineType.FILING, 7, false));
        // due in 30 days (outside the window)
        deadlines.insert(new Deadline("d2", "c1", "C-2026-014", "Pre-trial conference",
                LocalDate.now().plusDays(30), Deadline.DeadlineType.MEETING, 7, false));
        // already past
        deadlines.insert(new Deadline("d3", "c1", "C-2026-014", "Old notice",
                LocalDate.now().minusDays(2), Deadline.DeadlineType.OTHER, 7, false));
        trust.recordTransaction("c1", LocalDate.now(), "DEPOSIT", "Trust deposit", "DEP-1", 24155.00, "Attorney");
    }

    @AfterEach
    void tearDown() {
        ds.close();
    }

    /** Stands in for a real SMTP server: records what would have been sent. */
    private static final class RecordingMail extends MailSender {
        final List<String> sent = new ArrayList<>();
        RecordingMail() { super("smtp.test", 587, null, null, "reminders@test.co.za"); }
        @Override public boolean isConfigured() { return true; }
        @Override public void send(String to, String subject, String body) throws MessagingException {
            sent.add(to + " | " + subject);
        }
    }

    @Test
    @DisplayName("Reminders: only deadlines inside their window are queued, once per recipient")
    void queuesOnceForDueDeadlines() {
        ReminderService svc = new ReminderService(ds, users, cases, deadlines,
                new MailSender(null, 587, null, null, null), audit, "firm@mongalalaw.co.za");

        ReminderService.RunResult first = svc.runOnce();
        assertEquals(2, first.queued(), "client + firm for the one due deadline");
        assertEquals(0, first.sent());
        assertFalse(first.mailConfigured());
        assertEquals(2, first.waiting(), "without SMTP the reminders stay queued");

        ReminderService.RunResult second = svc.runOnce();
        assertEquals(0, second.queued(), "running again must not duplicate reminders");

        List<ReminderService.Reminder> all = svc.recent(50);
        assertEquals(2, all.size());
        assertTrue(all.stream().allMatch(r -> r.status().equals("QUEUED")));
        assertTrue(all.stream().anyMatch(r -> r.recipient().equals("thabo@example.com")));
        assertTrue(all.stream().anyMatch(r -> r.recipient().equals("firm@mongalalaw.co.za")));
        assertTrue(all.stream().allMatch(r -> r.subject().contains("File heads of argument")));
    }

    @Test
    @DisplayName("Reminders: queued reminders are sent once SMTP is configured")
    void sendsQueuedWhenMailConfigured() {
        new ReminderService(ds, users, cases, deadlines,
                new MailSender(null, 587, null, null, null), audit, "firm@mongalalaw.co.za").runOnce();

        RecordingMail mail = new RecordingMail();
        ReminderService svc = new ReminderService(ds, users, cases, deadlines, mail, audit, "firm@mongalalaw.co.za");
        ReminderService.RunResult r = svc.runOnce();

        assertEquals(0, r.queued());
        assertEquals(2, r.sent());
        assertEquals(0, r.failed());
        assertEquals(0, r.waiting());
        assertEquals(2, mail.sent.size());
        assertTrue(svc.recent(10).stream().allMatch(x -> x.status().equals("SENT") && x.sentAt() != null));
    }

    @Test
    @DisplayName("Case file: one A4 page with the case reference, deadline and trust credit")
    void caseFileIsOnePage() throws Exception {
        CaseFileService svc = new CaseFileService();
        List<Deadline> upcoming = deadlines.byCase("c1").stream().filter(d -> d.daysLeft() >= 0).toList();
        byte[] pdf = svc.render(cases.findById("c1"), null, upcoming,
                billing.statementForCase("c1"), trust.statementForCase("c1"), LocalDateTime.now());

        assertTrue(pdf.length > 1000);
        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII));
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(pdf))) {
            assertEquals(1, doc.getNumberOfPages(), "case file must fit on one page");
            String text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains("C-2026-014"));
            assertTrue(text.contains("File heads of argument"));
            assertTrue(text.contains("No approved summary yet."));
            assertTrue(text.contains("Trust credit available: R 24,155.00"));
        }
    }

    @Test
    @DisplayName("Case file: characters outside Latin-1 are replaced so the PDF still renders")
    void caseFileSanitisesText() {
        assertEquals("Ma?a Siyaya", CaseFileService.safe("Ma\u0107a Siyaya"));
        assertEquals("Not recorded", CaseFileService.safe("  "));
    }
}
