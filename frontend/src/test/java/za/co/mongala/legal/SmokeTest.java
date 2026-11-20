package za.co.mongala.legal;

import za.co.mongala.legal.model.*;
import za.co.mongala.legal.service.*;

import java.time.LocalDateTime;

/**
 * Headless smoke test of the service layer the UI is built on.
 * Run with: java -cp target/classes za.co.mongala.legal.SmokeTest
 */
public class SmokeTest {

    public static void main(String[] args) {
        // 1. Auth
        AuthService auth = new AuthService();
        User lawyer = auth.authenticate("ally@mongalalaw.co.za", "lawyer123");
        User client = auth.authenticate("thabo@example.com", "client123");
        User bad = auth.authenticate("x@y.z", "nope");
        check(lawyer != null && lawyer.getRole() == Role.LAWYER, "lawyer auth");
        check(client != null && client.getRole() == Role.CLIENT, "client auth");
        check(bad == null, "invalid credentials rejected");

        // 2. Data store + case lookup
        DataStore ds = new DataStore();
        Case c = ds.findCaseById("c1").orElseThrow();
        check(c.getReference().equals("C-2026-014"), "case lookup");
        check(ds.findCases("siyaya").size() == 1, "case search");

        // 3. Upload + AI processing (FR1/FR2/FR3)
        LegalDocument doc = new LegalDocument("dX", c.getId(), "Affidavit_of_Quantum.pdf",
                "s3://mongala-case-files/c1/affidavit.pdf", "Ally Mongala", LocalDateTime.now(),
                512, LegalDocument.ProcessingStatus.PROCESSING);
        ds.addDocument(doc);
        LegalNlpSimulator.AiProcessingResult res =
                new LegalNlpSimulator().processDocument(doc, c);
        ds.addExtractions(doc.getId(), res.extractions());
        ds.addSummary(res.summary());
        check(res.extractions().size() >= 5, "extraction produced >= 5 fields");
        check(res.summary().getParagraph1() != null && res.summary().getParagraph3() != null,
                "three-paragraph summary generated");
        check(ds.extractionsForDocument(doc.getId()).size() == res.extractions().size(),
                "extractions persisted per document");
        check(res.extractions().stream().allMatch(e -> e.getConfidence() > 0 && e.getConfidence() <= 1),
                "confidence scores in (0,1] - ETR5");
        check(res.extractions().stream().allMatch(e -> e.getSpanText() != null && !e.getSpanText().isBlank()),
                "source spans present - ETR2");

        // 4. Human-in-the-loop approval (FR6)
        Summary sum = ds.summariesForCase(c.getId()).get(0);
        check(sum.getStatus() == Summary.SummaryStatus.DRAFT, "summary starts as DRAFT");
        sum.approve(LocalDateTime.now());
        check(sum.getStatus() == Summary.SummaryStatus.APPROVED, "summary approval works");

        // 5. Calendar sync (FR4)
        CalendarSyncService cal = new CalendarSyncService();
        Deadline d = ds.deadlinesForCase("c1").get(0);
        String receipt = cal.sync(d);
        check(d.isSynced(), "deadline marked synced");
        check(receipt.contains("Google Calendar event created"), "sync receipt returned");

        // 6. Audit trail (FR10)
        AuditService audit = new AuditService(ds);
        audit.success("Ally Mongala", "SMOKE_TEST", "validated service layer");
        check(ds.recentActivity(200).stream().anyMatch(a -> a.getAction().equals("SMOKE_TEST")),
                "audit entries recorded");

        // 7. Client portal queries (FR5)
        check(ds.findCasesForClient("u2").size() >= 1, "client has linked cases");
        check(ds.unreadCountForClient("u2") >= 1, "unread message count for client");

        System.out.println("ALL SMOKE TESTS PASSED");
    }

    private static void check(boolean ok, String label) {
        if (!ok) {
            System.out.println("FAILED: " + label);
            System.exit(1);
        }
        System.out.println("  ok - " + label);
    }
}
