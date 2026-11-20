package za.co.mongala.legal.backend.seed;

import za.co.mongala.legal.backend.service.*;
import za.co.mongala.legal.model.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Seeds the database on first start with the same demo users, sample matters,
 * deadlines and messages the front-end expects. Two documents are "processed"
 * through the REAL pipeline from embedded legal text, so the extracted fields,
 * confidence scores and summaries you see are genuine NLP output.
 */
public class SeedData {

    private final javax.sql.DataSource dsSource;
    private final java.nio.file.Path uploadDir;
    private final UserService users;
    private final CaseService cases;
    private final DeadlineService deadlines;
    private final MessageService messages;
    private final AuditService audit;
    private final DocumentService documents;
    private final ExtractionService extractions;
    private final SummaryService summaries;
    private final DeadlineExtractor deadlineExtractor;
    private final RatingService ratings;
    private final TrainingService training;

    public SeedData(javax.sql.DataSource ds, java.nio.file.Path uploadDir,
                    UserService users, CaseService cases, DeadlineService deadlines,
                    MessageService messages, AuditService audit, DocumentService documents,
                    ExtractionService extractions, SummaryService summaries,
                    DeadlineExtractor deadlineExtractor,
                    RatingService ratings, TrainingService training) {
        this.dsSource = ds;
        this.uploadDir = uploadDir;
        this.users = users;
        this.cases = cases;
        this.deadlines = deadlines;
        this.messages = messages;
        this.audit = audit;
        this.documents = documents;
        this.extractions = extractions;
        this.summaries = summaries;
        this.deadlineExtractor = deadlineExtractor;
        this.ratings = ratings;
        this.training = training;
    }

    public void seedIfEmpty() {
        if (users.findByEmail("ally@mongalalaw.co.za") != null) return;

        // ---- users (passwords are hashed with PBKDF2) --------------------
        users.create("u1", "Ally Mongala", "ally@mongalalaw.co.za", "lawyer123", Role.LAWYER);
        users.create("u2", "Thabo Mokoena", "thabo@example.com", "client123", Role.CLIENT);
        users.create("u3", "Elna van Wyk", "elna@example.com", "client123", Role.CLIENT);
        users.create("u4", "System Admin", "admin@mongalalaw.co.za", "admin123", Role.ADMIN);

        // ---- cases (all starting from November 2026) ------------------------
        insertCase("c1", "C-2026-014", "Mokoena v Siyaya Transport (Pty) Ltd", "u2", "Thabo Mokoena",
                "Siyaya Transport (Pty) Ltd", CaseStatus.IN_LITIGATION, "Personal Injury",
                "Johannesburg High Court", "Claim for damages arising from a motor vehicle collision on the N1.",
                LocalDate.of(2026, 11, 2), 1250000);
        insertCase("c2", "C-2026-017", "Van Wyk v Standard Bank of SA Ltd", "u3", "Elna van Wyk",
                "Standard Bank of SA Ltd", CaseStatus.PENDING_HEARING, "Consumer Credit",
                "Pretoria Magistrate's Court", "Dispute over debt collection practices and interest calculation.",
                LocalDate.of(2026, 11, 5), 412000);
        insertCase("c3", "C-2026-011", "Ndlovu Family Trust v City of Johannesburg", "u9", "Ndlovu Family Trust",
                "City of Johannesburg", CaseStatus.OPEN, "Municipal Law", "Gauteng Division",
                "Review of municipal rates assessment.", LocalDate.of(2026, 11, 4), 0);
        insertCase("c4", "C-2026-009", "Molefe & Partners v Hartbeespoort Developers (Pty) Ltd", "u10",
                "Molefe & Partners", "Hartbeespoort Developers (Pty) Ltd", CaseStatus.CLOSED,
                "Breach of Contract", "North Gauteng High Court", "Claim for outstanding architectural fees.",
                LocalDate.of(2026, 11, 1), 680000);
        insertCase("c5", "C-2026-019", "Dlamini v Nedbank Ltd", "u11", "Sibongile Dlamini", "Nedbank Ltd",
                CaseStatus.ON_HOLD, "Banking Law", "Johannesburg High Court",
                "Unauthorised investment transactions.", LocalDate.of(2026, 11, 9), 340000);

        // ---- deadlines (all starting from November 2026 onwards) ------------
        insertDeadline("dl1", "c1", "File heads of argument", LocalDate.of(2026, 11, 28), Deadline.DeadlineType.FILING, 7, false);
        insertDeadline("dl2", "c1", "Pre-trial conference", LocalDate.of(2026, 12, 2), Deadline.DeadlineType.MEETING, 3, true);
        insertDeadline("dl3", "c1", "Hearing - Mokoena v Siyaya Transport", LocalDate.of(2026, 12, 14), Deadline.DeadlineType.HEARING, 5, true);
        insertDeadline("dl4", "c2", "Hearing - Van Wyk v Standard Bank", LocalDate.of(2026, 12, 9), Deadline.DeadlineType.HEARING, 5, false);
        insertDeadline("dl5", "c2", "File practice note", LocalDate.of(2026, 11, 26), Deadline.DeadlineType.FILING, 3, false);
        insertDeadline("dl6", "c3", "City response deadline (rates objection)", LocalDate.of(2026, 11, 30), Deadline.DeadlineType.OTHER, 7, false);
        insertDeadline("dl7", "c3", "Internal review - valuation report", LocalDate.of(2026, 11, 24), Deadline.DeadlineType.MEETING, 2, false);
        insertDeadline("dl8", "c1", "Medical follow-up review", LocalDate.of(2027, 2, 10), Deadline.DeadlineType.OTHER, 14, true);
        insertDeadline("dl9", "c5", "Ombud outcome review", LocalDate.of(2027, 1, 18), Deadline.DeadlineType.MEETING, 7, true);

        // ---- messages -------------------------------------------------------
        insertMessage("m1", "c1", "Ally Mongala", "Your case update - summary approved",
                "Good day Thabo, your case summary for the hearing on 14 December has been approved and is now available in your portal.",
                LocalDateTime.of(2026, 11, 7, 9, 20), true, false);
        insertMessage("m2", "c1", "Ally Mongala", "Settlement offer from Siyaya Transport",
                "The defendant's insurers have put forward an offer of R950,000. We would like your instructions before the pre-trial conference.",
                LocalDateTime.of(2026, 11, 6, 11, 5), true, false);
        insertMessage("m3", "c1", "Thabo Mokoena", "Re: Settlement offer",
                "Thank you. I would prefer to discuss this in a meeting next week.",
                LocalDateTime.of(2026, 11, 6, 15, 42), false, true);

        // ---- audit ----------------------------------------------------------
        audit.info("Ally Mongala", "LOGIN", "Lawyer signed in to the firm workspace");
        audit.success("AI Worker", "AI_PROCESSED", "Statement_of_Claim_Mokoena.pdf: extracted 8 fields (avg confidence 92%) and generated a 3-paragraph draft summary");
        audit.info("System", "CALENDAR_SYNC", "Pre-trial conference + Hearing pushed to calendar (2 events) for C-2026-014");

        // ---- client rating of lawyer (FR7) ----------------------------------
        ratings.insert(new za.co.mongala.legal.model.Rating("r1", "c1", "u2", "Ally Mongala",
                5, "Very clear updates, kept me informed throughout.", java.time.LocalDateTime.of(2026, 11, 8, 10, 0)));
        ratings.insert(new za.co.mongala.legal.model.Rating("r2", "c2", "u3", "Ally Mongala",
                4, "Good communication, could improve on turnaround times.", java.time.LocalDateTime.of(2026, 11, 9, 9, 30)));

        // ---- training runs + datasets (FR10 / ETR3) ---------------------------
        insertDataset("ds1", "Legal PDF corpus - dates & parties (v3)", 1240, 48200);
        insertDataset("ds2", "SA case law summaries - corrected annotations", 860, 31800);
        insertTrainingRun("tr1", "legal-nlp-extractor-v4", "ds1", "Ally Mongala",
                java.time.LocalDateTime.of(2026, 11, 10, 14, 0), null);
        insertTrainingRun("tr2", "legal-nlp-summariser-v2", "ds2", "System",
                java.time.LocalDateTime.of(2026, 11, 11, 9, 0), null);

        // ---- demo of AI validation: a document that does NOT match case c1 is blocked ----
        LegalDocument rejectedDoc = new LegalDocument("d10", "c1", "Nkosi_v_ABSA_Summons.pdf",
                "seed://nkosi_abba_summons", "Ally Mongala",
                LocalDateTime.of(2026, 11, 10, 9, 0), 640,
                LegalDocument.ProcessingStatus.REJECTED, "PDF", true,
                "The document relates to a different matter (Nkosi v ABSA Bank) - it does not match case C-2026-014 (Thabo Mokoena v Siyaya Transport (Pty) Ltd).");
        documents.insert(rejectedDoc);
        extractions.insertAll("d10", List.of(
                new Extraction("Plaintiff", "Thabo Nkosi", 0.99, "THABO NKOSI Plaintiff", 1),
                new Extraction("Defendant", "ABSA Bank Ltd", 0.97, "ABSA BANK LTD Defendant", 1),
                new Extraction("Case Number", "GP/JHB/2026/991122", 0.94, "case number GP/JHB/2026/991122", 1),
                new Extraction("Court", "High Court", 0.95, "in the High Court", 1)));

        // ---- documents processed through the REAL pipeline ------------------
        seedDocument("d1", "c1", "Statement_of_Claim_Mokoena.pdf", STATEMENT_OF_CLAIM, "Ally Mongala",
                LocalDateTime.of(2026, 11, 3, 10, 30), 1240);
        seedDocument("d4", "c2", "Summons_and_Particulars.pdf", SUMMONS_TEXT, "Ally Mongala",
                LocalDateTime.of(2026, 11, 6, 10, 30), 540);

        // ---- initial billing entries for ERP fee notes ----------------------
        BillingService billing = new BillingService(dsSource);
        billing.addEntry("c1", LocalDate.of(2026, 11, 2), "TIME",
                "Initial consultation and mandate intake with client", 1.5, 1850.0, 0, "Ally Mongala");
        billing.addEntry("c1", LocalDate.of(2026, 11, 4), "TIME",
                "Drafting High Court combined summons and particulars of claim", 3.0, 1850.0, 0, "Ally Mongala");
        billing.addEntry("c1", LocalDate.of(2026, 11, 6), "DISBURSEMENT",
                "Sheriff of the Court - service of summons on Siyaya Transport", 0, 0, 845.00, "Ally Mongala");
        billing.addEntry("c2", LocalDate.of(2026, 11, 6), "TIME",
                "Review of home-loan amortisation schedule and NCA s129 notice", 2.0, 1650.0, 0, "Ally Mongala");

        // ---- Section 86 client trust ledger ---------------------------------
        TrustAccountingService trust = new TrustAccountingService(dsSource);
        trust.recordTransaction("c1", LocalDate.of(2026, 11, 2), "DEPOSIT",
                "Client trust retainer deposit (EFT)", "TR-MOK-001", 25000.00, "Ally Mongala");
        trust.recordTransaction("c1", LocalDate.of(2026, 11, 6), "TRANSFER_TO_BUSINESS",
                "Reimbursement of Sheriff summons service disbursement", "TR-MOK-002", 845.00, "Ally Mongala");
        trust.recordTransaction("c2", LocalDate.of(2026, 11, 5), "DEPOSIT",
                "Initial trust deposit - NCA dispute", "TR-VWK-001", 8000.00, "Ally Mongala");
    }

    /* ---------------- helpers ---------------- */

    private void insertCase(String id, String ref, String title, String clientId, String clientName,
                            String opposing, CaseStatus status, String type, String court,
                            String desc, LocalDate opened, double amount) {
        cases.insert(new Case(id, ref, title, clientId, clientName, opposing, status, type, court,
                desc, opened, amount));
    }

    private void insertDeadline(String id, String caseId, String title, LocalDate date,
                                Deadline.DeadlineType type, int reminder, boolean synced) {
        deadlines.insert(new Deadline(id, caseId, "", title, date, type, reminder, synced));
    }

    private void insertMessage(String id, String caseId, String from, String subject, String body,
                               LocalDateTime at, boolean fromLawyer, boolean read) {
        messages.insert(new ClientMessage(id, caseId, from, subject, body, at, fromLawyer, read, true));
    }

    /** Runs the real extraction + summarisation engines on embedded legal text. */
    private void seedDocument(String docId, String caseId, String filename, String text,
                              String uploadedBy, LocalDateTime uploadedAt, long kb) {
        LegalDocument doc = new LegalDocument(docId, caseId, filename, "seed://" + filename,
                uploadedBy, uploadedAt, kb, LegalDocument.ProcessingStatus.PROCESSED);
        documents.insert(doc);
        documents.setFullText(docId, text);
        Case c = cases.findById(caseId);

        var content = new za.co.mongala.legal.backend.nlp.PdfTextExtractor.PdfContent(List.of(text), text);
        var ex = za.co.mongala.legal.backend.nlp.ExtractionEngine.extract(List.of(text));
        extractions.insertAll(docId, ex);
        deadlineExtractor.apply(docId, caseId, ex);
        var sum = za.co.mongala.legal.backend.nlp.Summarizer.summarize(text, c);
        Summary s = new Summary("s" + UUID.randomUUID().toString().substring(0, 10), docId, caseId,
                sum.paragraph1(), sum.paragraph2(), sum.paragraph3(),
                1, Summary.SummaryStatus.DRAFT, "AI NLP Worker", uploadedAt, null);
        if (docId.equals("d1")) s.approve(uploadedAt.plusDays(1));
        summaries.insert(s);
    }

    private static final String STATEMENT_OF_CLAIM = """
            IN THE HIGH COURT OF SOUTH AFRICA
            GAUTENG DIVISION, JOHANNESBURG
            CASE NUMBER: GP/JHB/2026/000841

            In the matter between:
            THABO MOKOENA (ID 780512 5048 088) Plaintiff
            and
            SIYAYA TRANSPORT (PTY) LTD (reg. 2015/112233/07) Defendant

            STATEMENT OF CLAIM

            1. The plaintiff, an adult male businessman of 14 Oak Road, Randburg, claims from the defendant
            payment of general and special damages in the sum of R1,250,000.00.
            2. On 2 November 2026, the plaintiff was travelling as a passenger in a motor vehicle owned by
            the defendant when the vehicle was involved in a collision on the N1 highway near the Allandale
            off-ramp.
            3. The collision was caused solely by the negligence of the defendant's driver, who failed to keep
            a proper lookout and drove at an excessive speed in wet conditions.
            4. As a result of the collision, the plaintiff sustained soft-tissue injuries to his cervical spine
            and a fracture of his right tibia. He was treated at Netcare Union Hospital and remains under the
            care of Dr N. Ncube, orthopaedic surgeon.
            5. The plaintiff's claim includes past and future medical expenses, loss of earnings, and general
            damages for pain and suffering.
            6. The matter has been set down for hearing on 14 December 2026 in the Gauteng Division of the
            High Court, Johannesburg. Heads of argument must be filed within 21 days of receipt of this notice.
            """;

    private static final String SUMMONS_TEXT = """
            IN THE MAGISTRATE'S COURT FOR THE DISTRICT OF PRETORIA
            CASE NUMBER: 8741/2026

            Between:
            ELNA VAN WYK Plaintiff
            and
            STANDARD BANK OF SOUTH AFRICA LTD Defendant

            SUMMONS AND PARTICULARS OF CLAIM

            1. The plaintiff, Elna van Wyk, a pensioner resident at 22 Acacia Street, Brooklyn, Pretoria,
            sues the defendant for payment of the amount of R412,000.00.
            2. The claim arises from the administration of the plaintiff's home loan account number 4452
            8891 0042.
            3. The plaintiff alleges that the defendant charged interest on arrears in excess of the amounts
            permitted under the National Credit Act 34 of 2005, and debited collection costs without the
            required notices.
            4. The matter has been set down for hearing on 9 December 2026 in the Pretoria Magistrate's Court.
            The defendant has filed a notice of intention to defend and a plea dated 16 November 2026.
            5. The plaintiff requests that the defendant be ordered to refund the excess amounts, together
            with interest and costs of suit.
            """;

    private void insertDataset(String id, String name, long records, long kb) {
        try (java.sql.Connection conn = ds().getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO datasets(id, name, records, size_kb, storage_path, uploaded_at) VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, id);
            ps.setString(2, name);
            ps.setLong(3, records);
            ps.setLong(4, kb);
            ps.setString(5, "datasets/" + id + "_seed.csv");
            ps.setTimestamp(6, java.sql.Timestamp.valueOf(java.time.LocalDateTime.of(2026, 11, 2, 10, 0)));
            ps.executeUpdate();
            // write a small sample dataset file so admin download works for seed data
            try {
                java.nio.file.Files.createDirectories(uploadDir.resolve("datasets"));
                java.nio.file.Files.writeString(uploadDir.resolve("datasets/" + id + "_seed.csv"),
                        "text,label\n" + name + ",annotation\nSample document,dates=2026-11-02\n");
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }

    private void insertTrainingRun(String id, String model, String datasetId, String by,
                                   java.time.LocalDateTime at, Double accuracy) {
        try (java.sql.Connection conn = ds().getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO training_runs(id, model_name, dataset_id, status, requested_by, requested_at, accuracy) VALUES (?,?,?,?,?,?,?)")) {
            ps.setString(1, id);
            ps.setString(2, model);
            ps.setString(3, datasetId);
            ps.setString(4, "PENDING");
            ps.setString(5, by);
            ps.setTimestamp(6, java.sql.Timestamp.valueOf(at));
            if (accuracy == null) ps.setNull(7, java.sql.Types.DOUBLE); else ps.setDouble(7, accuracy);
            ps.executeUpdate();
        } catch (Exception ignored) {}
    }

    private javax.sql.DataSource ds() {
        return dsSource;
    }
}
