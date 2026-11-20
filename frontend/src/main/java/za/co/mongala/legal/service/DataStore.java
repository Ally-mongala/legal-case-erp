package za.co.mongala.legal.service;

import za.co.mongala.legal.model.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * In-memory data store that plays the role of the central legal database
 * (Assignment 1: "Central Legal Database"; Assignment 2 section 10) in DEMO
 * mode. In CONNECTED mode the UI uses {@link za.co.mongala.legal.client.RemoteDataStore},
 * which talks to the real backend database over REST.
 */
public class DataStore implements IDataStore {

    private final List<Case> cases = new ArrayList<>();
    private final List<LegalDocument> documents = new ArrayList<>();
    private final List<Extraction> extractions = new ArrayList<>();
    private final List<Summary> summaries = new ArrayList<>();
    private final List<Deadline> deadlines = new ArrayList<>();
    private final List<ClientMessage> messages = new ArrayList<>();
    private final List<ActivityLog> activity = new ArrayList<>();
    private final List<Rating> ratings = new ArrayList<>();
    private final List<TrainingRun> trainingRuns = new ArrayList<>();
    private final List<Dataset> datasets = new ArrayList<>();
    private final List<User> users = new ArrayList<>();

    private int nextId = 100;

    public DataStore() {
        seed();
    }

    /* ------------------------------------------------------------------
     * Queries
     * ------------------------------------------------------------------ */

    public List<Case> getAllCases() { return List.copyOf(cases); }

    public List<Case> findCases(String query) {
        if (query == null || query.isBlank()) return getAllCases();
        String q = query.toLowerCase();
        return cases.stream()
                .filter(c -> c.getTitle().toLowerCase().contains(q)
                        || c.getReference().toLowerCase().contains(q)
                        || c.getClientName().toLowerCase().contains(q)
                        || c.getOpposingParty().toLowerCase().contains(q)
                        || c.getStatus().getDisplayName().toLowerCase().contains(q))
                .collect(Collectors.toList());
    }

    public Optional<Case> findCaseById(String id) {
        return cases.stream().filter(c -> c.getId().equals(id)).findFirst();
    }

    public List<Case> findCasesForClient(String clientId) {
        return cases.stream().filter(c -> c.getClientId().equals(clientId)).collect(Collectors.toList());
    }

    public List<LegalDocument> documentsForCase(String caseId) {
        return documents.stream().filter(d -> d.getCaseId().equals(caseId))
                .sorted(Comparator.comparing(LegalDocument::getUploadedAt).reversed())
                .collect(Collectors.toList());
    }

    public List<Extraction> extractionsForDocument(String documentId) {
        return extractions.stream().filter(e -> e.getDocumentId().equals(documentId))
                .collect(Collectors.toList());
    }

    public List<Summary> summariesForCase(String caseId) {
        return summaries.stream().filter(s -> s.getCaseId().equals(caseId))
                .sorted(Comparator.comparing(Summary::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public List<Deadline> allDeadlines() {
        return deadlines.stream()
                .sorted(Comparator.comparing(Deadline::getDate))
                .collect(Collectors.toList());
    }

    public List<Deadline> deadlinesForCase(String caseId) {
        return deadlines.stream().filter(d -> d.getCaseId().equals(caseId))
                .sorted(Comparator.comparing(Deadline::getDate))
                .collect(Collectors.toList());
    }

    public List<ClientMessage> messagesForCase(String caseId) {
        return messages.stream().filter(m -> m.getCaseId().equals(caseId))
                .sorted(Comparator.comparing(ClientMessage::getSentAt))
                .collect(Collectors.toList());
    }

    public long unreadCountForClient(String clientId) {
        List<String> caseIds = findCasesForClient(clientId).stream().map(Case::getId).collect(Collectors.toSet()).stream().toList();
        return messages.stream()
                .filter(m -> caseIds.contains(m.getCaseId()))
                .filter(m -> m.isFromLawyer() && !m.isReadByClient())
                .count();
    }

    public List<Extraction> allExtractions() { return List.copyOf(extractions); }

    /** Removes stale AI results before a retry (FR7). */
    public void clearExtractionsForDocument(String documentId) {
        extractions.removeIf(e -> e.getDocumentId().equals(documentId));
    }

    public void clearSummaryForDocument(String documentId) {
        summaries.removeIf(s -> s.getDocumentId().equals(documentId));
    }

    @Override
    public void createRating(Rating r) { ratings.add(r); }

    @Override
    public List<Rating> ratingsList() { return List.copyOf(ratings); }

    @Override
    public List<User> listUsers() { return List.copyOf(users); }

    @Override
    public void setUserActive(String userId, boolean active) {
        for (int i = 0; i < users.size(); i++) {
            User u = users.get(i);
            if (u.getId().equals(userId)) {
                users.set(i, new User(u.getId(), u.getName(), u.getEmail(), u.getPassword(), u.getRole(), active));
            }
        }
    }

    @Override
    public String auditCsv() {
        StringBuilder sb = new StringBuilder("timestamp,user,action,details,level\n");
        for (ActivityLog a : activity) {
            sb.append(csv(a.getTimestamp().toString())).append(',')
              .append(csv(a.getUserName())).append(',')
              .append(csv(a.getAction())).append(',')
              .append(csv(a.getDetails())).append(',')
              .append(csv(a.getLevel().name())).append('\n');
        }
        return sb.toString();
    }

    private static String csv(String v) {
        String s = v == null ? "" : v.replace("\"", "\"\"");
        return "\"" + s + "\"";
    }

    @Override
    public java.util.Map<String, Object> metrics() {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("totalUsers", users.size());
        m.put("lawyers", users.stream().filter(u -> u.getRole() == Role.LAWYER).count());
        m.put("clients", users.stream().filter(u -> u.getRole() == Role.CLIENT).count());
        m.put("revokedUsers", users.stream().filter(u -> !u.isActive()).count());
        m.put("totalCases", cases.size());
        m.put("activeCases", cases.stream().filter(c -> c.getStatus() != CaseStatus.CLOSED).count());
        m.put("totalDocuments", documents.size());
        m.put("processedDocuments", documents.stream().filter(d -> d.getProcessingStatus() == LegalDocument.ProcessingStatus.PROCESSED).count());
        m.put("failedDocuments", documents.stream().filter(d -> d.getProcessingStatus() == LegalDocument.ProcessingStatus.FAILED).count());
        m.put("totalDeadlines", deadlines.size());
        m.put("pendingSyncs", deadlines.stream().filter(d -> !d.isSynced()).count());
        m.put("totalMessages", messages.size());
        m.put("totalRatings", ratings.size());
        m.put("trainingRuns", trainingRuns.size());
        m.put("datasets", datasets.size());
        m.put("avgConfidence", extractions.stream().mapToDouble(Extraction::getConfidence).average().orElse(0));
        m.put("dataResidency", "ZA");
        return m;
    }

    @Override
    public List<TrainingRun> trainingRuns() { return List.copyOf(trainingRuns); }

    @Override
    public void approveTrainingRun(String runId) {
        for (int i = 0; i < trainingRuns.size(); i++) {
            TrainingRun r = trainingRuns.get(i);
            if (r.getId().equals(runId) && r.getStatus() == TrainingRun.Status.PENDING) {
                trainingRuns.set(i, new TrainingRun(r.getId(), r.getModelName(), r.getDatasetId(),
                        TrainingRun.Status.APPROVED, r.getRequestedBy(), r.getRequestedAt(),
                        LocalDateTime.now(), null));
            }
        }
    }

    @Override
    public List<Dataset> datasets() { return List.copyOf(datasets); }

    public List<ActivityLog> recentActivity(int limit) {
        return activity.stream()
                .sorted(Comparator.comparing(ActivityLog::getTimestamp).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /* ------------------------------------------------------------------
     * Mutations (recorded through AuditService in the UI layer)
     * ------------------------------------------------------------------ */

    @Override
    public Case createCase(Case template) {
        String id = "c" + (cases.size() + 100);
        String ref = String.format("C-%d-%03d", LocalDate.now().getYear(), cases.size() + 15);
        Case c = new Case(id, ref,
                template.getTitle() == null ? "Untitled matter" : template.getTitle(),
                template.getClientId(),
                template.getClientName() == null ? "Client" : template.getClientName(),
                template.getOpposingParty() == null ? "Unknown" : template.getOpposingParty(),
                template.getStatus() == null ? CaseStatus.OPEN : template.getStatus(),
                template.getMatterType() == null ? "General" : template.getMatterType(),
                template.getCourt() == null ? "High Court" : template.getCourt(),
                template.getDescription() == null ? "" : template.getDescription(),
                LocalDate.now(),
                Math.max(0, template.getAmountInvolved()));
        cases.add(c);
        return c;
    }

    public String addDocument(LegalDocument doc) { documents.add(doc); return doc.getId(); }

    public void addExtractions(String documentId, List<Extraction> ex) {
        ex.forEach(e -> {
            e.setDocumentId(documentId);
            extractions.add(e);
        });
    }

    public void addSummary(Summary s) { summaries.add(s); }

    public void addDeadline(Deadline d) { deadlines.add(d); }

    /* ---- Case lifecycle (demo) ---- */
    @Override
    public Case updateCase(Case c) {
        for (int i = 0; i < cases.size(); i++) {
            Case old = cases.get(i);
            if (old.getId().equals(c.getId())) {
                Case updated = new Case(old.getId(), old.getReference(), c.getTitle(), old.getClientId(),
                        old.getClientName(), c.getOpposingParty(), old.getStatus(), c.getMatterType(),
                        c.getCourt(), c.getDescription(), old.getOpenedDate(), c.getAmountInvolved());
                cases.set(i, updated);
                messages.add(new ClientMessage("m" + nextId(), old.getId(), "Firm", "Case updated",
                        "The details of your matter " + old.getReference() + " were updated by the firm.",
                        LocalDateTime.now(), true, false, true));
                return updated;
            }
        }
        return c;
    }

    @Override
    public Case updateCaseStatus(String caseId, CaseStatus status) {
        for (int i = 0; i < cases.size(); i++) {
            Case old = cases.get(i);
            if (old.getId().equals(caseId)) {
                Case updated = new Case(old.getId(), old.getReference(), old.getTitle(), old.getClientId(),
                        old.getClientName(), old.getOpposingParty(), status, old.getMatterType(),
                        old.getCourt(), old.getDescription(), old.getOpenedDate(), old.getAmountInvolved());
                cases.set(i, updated);
                messages.add(new ClientMessage("m" + nextId(), caseId, "Firm", "Case status updated",
                        "The status of your matter " + old.getReference() + " changed to: " + status.getDisplayName() + ".",
                        LocalDateTime.now(), true, false, true));
                return updated;
            }
        }
        return null;
    }

    @Override
    public void deleteCase(String caseId) {
        cases.removeIf(c -> c.getId().equals(caseId));
        documents.removeIf(d -> d.getCaseId().equals(caseId));
        summaries.removeIf(s -> s.getCaseId().equals(caseId));
        deadlines.removeIf(d -> d.getCaseId().equals(caseId));
        messages.removeIf(m -> m.getCaseId().equals(caseId));
        ratings.removeIf(r -> r.getCaseId().equals(caseId));
    }

    /* ---- Message alerts ---- */
    @Override
    public long lawyerUnreadCount() {
        return messages.stream().filter(m -> !m.isFromLawyer() && !m.isReadByLawyer()).count();
    }

    @Override
    public void markCaseReadByLawyer(String caseId) {
        messages.stream().filter(m -> m.getCaseId().equals(caseId) && !m.isFromLawyer())
                .forEach(ClientMessage::markReadByLawyer);
    }

    @Override
    public void sendLawyerMessage(String caseId, String subject, String body) {
        messages.add(new ClientMessage("m" + nextId(), caseId, "Firm",
                subject == null || subject.isBlank() ? "Message from your attorney" : subject,
                body == null ? "" : body, LocalDateTime.now(), true, false, true));
    }

    /* ---- Document download / share ---- */
    @Override
    public byte[] downloadDocument(String documentId) { return null; } // demo simulates

    @Override
    public void uploadClientDocument(String caseId, java.io.File file, boolean consent) {
        String lower = file.getName().toLowerCase();
        LegalDocument doc = new LegalDocument("d" + nextId(), caseId, file.getName(),
                "s3://mongala-case-files/" + caseId + "/" + file.getName(), "Client",
                LocalDateTime.now(), Math.max(1, file.length() / 1024),
                LegalDocument.ProcessingStatus.PROCESSING,
                lower.endsWith(".doc") || lower.endsWith(".docx") ? "WORD" : "PDF", consent);
        documents.add(doc);
    }

    @Override
    public void deleteDocument(String documentId) {
        documents.removeIf(d -> d.getId().equals(documentId));
        extractions.removeIf(e -> e.getDocumentId() != null && e.getDocumentId().equals(documentId));
        summaries.removeIf(s -> s.getDocumentId().equals(documentId));
    }

    @Override
    public String documentContent(String documentId) {
        return documents.stream().filter(d -> d.getId().equals(documentId))
                .findFirst().map(d -> "LegalCase ERP (demo mode)\n\nDocument: " + d.getFileName()
                        + "\nCase: " + d.getCaseId() + "\n\nIn CONNECTED mode the full extracted text "
                        + "of the uploaded document is displayed here.").orElse(null);
    }

    /* ---- Admin datasets & reports ---- */
    @Override
    public void uploadDataset(java.io.File file, String name) {
        datasets.add(new Dataset("ds" + nextId(), name == null ? file.getName() : name,
                Math.max(1, file.length() / 200), Math.max(1, file.length() / 1024),
                "datasets/" + file.getName(), LocalDateTime.now()));
    }

    @Override
    public byte[] downloadDataset(String datasetId) {
        return ("id,name,records\n" + datasetId + ",sample dataset,1\n").getBytes();
    }

    @Override
    public List<RejectedDocument> rejectionsList() {
        List<RejectedDocument> out = new java.util.ArrayList<>();
        for (LegalDocument d : documents) {
            if (d.getProcessingStatus() == LegalDocument.ProcessingStatus.REJECTED) {
                String ref = findCaseById(d.getCaseId()).map(Case::getReference).orElse(d.getCaseId());
                out.add(new RejectedDocument(d.getId(), d.getFileName(), d.getDocType(), ref,
                        d.getUploadedBy(), d.getUploadedAt(), "Blocked by AI validation - document does not match the case"));
            }
        }
        return out;
    }

    @Override
    public String rejectionsCsv() {
        StringBuilder sb = new StringBuilder("file_name,doc_type,case_reference,uploaded_by,uploaded_at,rejection_reason\n");
        for (RejectedDocument r : rejectionsList()) {
            sb.append(csv(r.getFileName())).append(',')
              .append(csv(r.getDocType())).append(',')
              .append(csv(r.getCaseReference())).append(',')
              .append(csv(r.getUploadedBy())).append(',')
              .append(csv(r.getUploadedAt() == null ? "" : r.getUploadedAt().toString())).append(',')
              .append(csv(r.getRejectionReason())).append('\n');
        }
        return sb.toString();
    }

    @Override
    public String systemReport() {
        StringBuilder sb = new StringBuilder("=== SYSTEM & MODEL MANAGEMENT REPORT ===\n");
        metrics().forEach((k, v) -> sb.append(k).append(',').append(v).append('\n'));
        return sb.toString();
    }

    public void addMessage(ClientMessage m) { messages.add(m); }

    public void addActivity(ActivityLog a) { activity.add(a); }

    public int nextId() { return ++nextId; }

    /* ------------------------------------------------------------------
     * Seed data - small law firm with realistic matters
     * ------------------------------------------------------------------ */

    private void seed() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 12, 9, 0);

        // ---- Cases -------------------------------------------------
        cases.add(new Case("c1", "C-2026-014", "Mokoena v Siyaya Transport (Pty) Ltd",
                "u2", "Thabo Mokoena", "Siyaya Transport (Pty) Ltd", CaseStatus.IN_LITIGATION,
                "Personal Injury", "Johannesburg High Court",
                "Claim for damages arising from a motor vehicle collision on the N1. Liability admitted, quantum in dispute.",
                LocalDate.of(2026, 11, 2), 1250000));

        cases.add(new Case("c2", "C-2026-017", "Van Wyk v Standard Bank of SA Ltd",
                "u3", "Elna van Wyk", "Standard Bank of SA Ltd", CaseStatus.PENDING_HEARING,
                "Consumer Credit", "Pretoria Magistrate's Court",
                "Dispute over unlawful debt collection practices and interest calculation on a home loan account.",
                LocalDate.of(2026, 11, 5), 412000));

        cases.add(new Case("c3", "C-2026-011", "Ndlovu Family Trust v City of Johannesburg",
                "u9", "Ndlovu Family Trust", "City of Johannesburg", CaseStatus.OPEN,
                "Municipal Law", "Gauteng Division",
                "Review of municipal rates assessment; claim that property was over-valued for rating purposes.",
                LocalDate.of(2026, 11, 4), 0));

        cases.add(new Case("c4", "C-2026-009", "Molefe & Partners v Hartbeespoort Developers (Pty) Ltd",
                "u10", "Molefe & Partners", "Hartbeespoort Developers (Pty) Ltd", CaseStatus.CLOSED,
                "Breach of Contract", "North Gauteng High Court",
                "Claim for payment of outstanding architectural fees. Settled by agreement in November 2026.",
                LocalDate.of(2026, 11, 1), 680000));

        cases.add(new Case("c5", "C-2026-019", "Dlamini v Nedbank Ltd",
                "u11", "Sibongile Dlamini", "Nedbank Ltd", CaseStatus.ON_HOLD,
                "Banking Law", "Johannesburg High Court",
                "Action relating to unauthorised investment transactions. Held in abeyance pending Ombud decision.",
                LocalDate.of(2026, 11, 9), 340000));

        // ---- Documents (FR1) ---------------------------------------
        documents.add(new LegalDocument("d1", "c1", "Statement_of_Claim_Mokoena.pdf",
                "s3://mongala-case-files/c1/statement_of_claim.pdf", "Ally Mongala",
                LocalDateTime.of(2026, 11, 3, 10, 30), 1240, LegalDocument.ProcessingStatus.PROCESSED));
        documents.add(new LegalDocument("d2", "c1", "Medical_Report_Dr_Ncube.pdf",
                "s3://mongala-case-files/c1/medical_report_drncube.pdf", "Ally Mongala",
                LocalDateTime.of(2026, 11, 5, 10, 30), 385, LegalDocument.ProcessingStatus.PROCESSED));
        documents.add(new LegalDocument("d3", "c1", "Pre-Trial_Minutes_2026-11.pdf",
                "s3://mongala-case-files/c1/pre_trial_minutes.pdf", "Ally Mongala",
                LocalDateTime.of(2026, 11, 6, 14, 5), 210, LegalDocument.ProcessingStatus.PROCESSED));
        documents.add(new LegalDocument("d4", "c2", "Summons_and_Particulars.pdf",
                "s3://mongala-case-files/c2/summons.pdf", "Ally Mongala",
                LocalDateTime.of(2026, 11, 6, 10, 30), 540, LegalDocument.ProcessingStatus.PROCESSED));
        documents.add(new LegalDocument("d5", "c2", "Bank_Statements_Scan.pdf",
                "s3://mongala-case-files/c2/bank_statements.pdf", "Ally Mongala",
                LocalDateTime.of(2026, 11, 7, 11, 45), 2210, LegalDocument.ProcessingStatus.FAILED));
        documents.add(new LegalDocument("d6", "c3", "Rates_Assessment_2026.pdf",
                "s3://mongala-case-files/c3/rates_assessment.pdf", "Ally Mongala",
                LocalDateTime.of(2026, 11, 8, 10, 30), 190, LegalDocument.ProcessingStatus.PROCESSED));
        documents.add(new LegalDocument("d7", "c4", "Signed_Service_Agreement.pdf",
                "s3://mongala-case-files/c4/service_agreement.pdf", "Ally Mongala",
                LocalDateTime.of(2026, 11, 1, 9, 15), 410, LegalDocument.ProcessingStatus.PROCESSED));

        // ---- Extractions for d1 (FR2 / ETR2 / ETR5) ------------------
        seedExtraction("d1", "Case Number", "GP/JHB/2026/000841", 0.98,
                "under case number GP/JHB/2026/000841", 1);
        seedExtraction("d1", "Hearing Date", "2026-12-14", 0.96,
                "set down for hearing on 14 December 2026", 3);
        seedExtraction("d1", "Filing Deadline", "2026-11-28", 0.94,
                "heads of argument to be filed within 21 days of receipt of this notice", 2);
        seedExtraction("d1", "Plaintiff", "Thabo Mokoena", 0.99,
                "Thabo Mokoena (ID 780512 5048 088) of 14 Oak Road, Randburg", 1);
        seedExtraction("d1", "Defendant", "Siyaya Transport (Pty) Ltd", 0.97,
                "Siyaya Transport (Pty) Ltd, registration 2015/112233/07", 1);
        seedExtraction("d1", "Claim Amount", "R1,250,000.00", 0.92,
                "general and special damages in the sum of R1,250,000.00", 2);
        seedExtraction("d1", "Court", "Johannesburg High Court", 0.99,
                "in the Gauteng Division of the High Court, Johannesburg", 1);

        seedExtraction("d2", "Examination Date", "2026-11-04", 0.95,
                "examined on 4 November 2026", 1);
        seedExtraction("d2", "Specialist", "Dr N. Ncube (Orthopaedic Surgeon)", 0.98,
                "Dr N. Ncube, orthopaedic surgeon", 1);
        seedExtraction("d2", "Incapacity Rating", "18% permanent disability", 0.88,
                "18% permanent whole-person impairment", 4);
        seedExtraction("d2", "Follow-up Date", "2027-02-10", 0.81,
                "review scheduled for February 2027", 5);

        // ---- Summaries (FR3) ----------------------------------------
        summaries.add(new Summary("s1", "d1", "c1",
                "This matter arises from a motor vehicle collision on the N1 highway on 2 November 2026, in which the plaintiff, Mr Thabo Mokoena, sustained soft-tissue and orthopaedic injuries. Liability has been admitted by the defendant, Siyaya Transport (Pty) Ltd, and the dispute is limited to the quantum of damages claimed in the sum of R1,250,000.00.",
                "The plaintiff was examined by Dr N. Ncube on 4 November 2026, who recorded an 18% permanent whole-person impairment. The matter is set down for hearing in the Johannesburg High Court on 14 December 2026, with heads of argument due for filing by 28 November 2026.",
                "Recommendation: the heads of argument should be finalised ahead of the filing deadline. The defendant's offer of R950,000.00 remains open for consideration, and settlement instructions should be confirmed before the pre-trial conference on 2 December 2026.",
                1, Summary.SummaryStatus.DRAFT, "Ally Mongala", LocalDateTime.of(2026, 11, 6, 16, 20), null));
        summaries.get(0).approve(LocalDateTime.of(2026, 11, 7, 9, 15));

        summaries.add(new Summary("s2", "d4", "c2",
                "Ms Elna van Wyk has brought an action against Standard Bank of SA Ltd arising from the administration of her home loan account. The plaintiff alleges that interest was charged on arrears in excess of the amounts permitted under the National Credit Act, and that debt collection costs were debited without the required statements.",
                "The matter was initiated by summons and particulars of claim filed on 5 November 2026. The defendant has filed a notice of intention to defend and a plea dated 16 November 2026. The matter has been set down for hearing in the Pretoria Magistrate's Court on 9 December 2026.",
                "Recommendation: the plaintiff's bank statements are still being scanned and verified; the AI extraction of the scanned statements failed OCR validation and a re-upload is required before the quantum can be finalised. Counsel's opinion is recommended before the pre-hearing conference.",
                1, Summary.SummaryStatus.DRAFT, "Ally Mongala", LocalDateTime.of(2026, 11, 8, 12, 40), null));

        summaries.add(new Summary("s3", "d6", "c3",
                "The Ndlovu Family Trust disputes the municipal rates assessment issued by the City of Johannesburg for its property at Erf 452, Sandton, which valued the property at R8.4 million for the 2026 rating year.",
                "The Trust contends that comparable sales in the area support a value of approximately R5.9 million, and has lodged a formal objection under section 78 of the Local Government: Municipal Property Rates Act. The City has not responded within the prescribed 90-day period.",
                "Recommendation: file an application for review in the Gauteng Division if the City fails to respond by 30 November 2026. An independent valuation is being commissioned to support the review application.",
                1, Summary.SummaryStatus.DRAFT, "Ally Mongala", LocalDateTime.of(2026, 11, 8, 10, 5), null));

        // ---- Deadlines (FR4) ----------------------------------------
        deadlines.add(new Deadline("dl1", "c1", "C-2026-014", "File heads of argument", LocalDate.of(2026, 11, 28), Deadline.DeadlineType.FILING, 7, false));
        deadlines.add(new Deadline("dl2", "c1", "C-2026-014", "Pre-trial conference", LocalDate.of(2026, 12, 2), Deadline.DeadlineType.MEETING, 3, true));
        deadlines.add(new Deadline("dl3", "c1", "C-2026-014", "Hearing - Mokoena v Siyaya Transport", LocalDate.of(2026, 12, 14), Deadline.DeadlineType.HEARING, 5, true));
        deadlines.add(new Deadline("dl4", "c2", "C-2026-017", "Hearing - Van Wyk v Standard Bank", LocalDate.of(2026, 12, 9), Deadline.DeadlineType.HEARING, 5, false));
        deadlines.add(new Deadline("dl5", "c2", "C-2026-017", "File practice note", LocalDate.of(2026, 11, 26), Deadline.DeadlineType.FILING, 3, false));
        deadlines.add(new Deadline("dl6", "c3", "C-2026-011", "City response deadline (rates objection)", LocalDate.of(2026, 11, 30), Deadline.DeadlineType.OTHER, 7, false));
        deadlines.add(new Deadline("dl7", "c3", "C-2026-011", "Internal review - valuation report", LocalDate.of(2026, 11, 24), Deadline.DeadlineType.MEETING, 2, false));
        deadlines.add(new Deadline("dl8", "c1", "C-2026-014", "Medical follow-up review", LocalDate.of(2027, 2, 10), Deadline.DeadlineType.OTHER, 14, true));
        deadlines.add(new Deadline("dl9", "c5", "C-2026-019", "Ombud outcome review", LocalDate.of(2027, 1, 18), Deadline.DeadlineType.MEETING, 7, true));

        // ---- Client messages (FR5) -----------------------------------
        messages.add(new ClientMessage("m1", "c1", "Ally Mongala", "Your case update - summary approved",
                "Good day Thabo, your case summary for the hearing on 14 December has been approved and is now available in your portal. We will file heads of argument before 28 November. Regards, Ally.",
                LocalDateTime.of(2026, 11, 7, 9, 20), true, false, true));
        messages.add(new ClientMessage("m2", "c1", "Ally Mongala", "Settlement offer from Siyaya Transport",
                "The defendant's insurers have put forward an offer of R950,000. We would like your instructions before the pre-trial conference on 2 December. Kindly confirm whether you wish to discuss a meeting.",
                LocalDateTime.of(2026, 11, 6, 11, 5), true, false, true));
        messages.add(new ClientMessage("m3", "c1", "Thabo Mokoena", "Re: Settlement offer",
                "Thank you. I would prefer to discuss this in a meeting next week, before giving final instructions.",
                LocalDateTime.of(2026, 11, 6, 15, 42), false, true, false));

        // ---- Seed audit trail (FR10) ---------------------------------
        activity.add(new ActivityLog(LocalDateTime.of(2026, 11, 10, 8, 12), "Ally Mongala",
                "LOGIN", "Lawyer signed in to the firm workspace", ActivityLog.Level.INFO));
        activity.add(new ActivityLog(LocalDateTime.of(2026, 11, 9, 16, 50), "Ally Mongala",
                "SUMMARY_APPROVED", "Summary s1 approved for C-2026-014 and published to client portal", ActivityLog.Level.SUCCESS));
        activity.add(new ActivityLog(LocalDateTime.of(2026, 11, 9, 16, 50), "System",
                "CLIENT_NOTIFICATION", "Client notified: 'Your case update - summary approved'", ActivityLog.Level.INFO));
        activity.add(new ActivityLog(LocalDateTime.of(2026, 11, 8, 12, 40), "Ally Mongala",
                "AI_SUMMARY_GENERATED", "3-paragraph draft summary created for Summons_and_Particulars.pdf (C-2026-017)", ActivityLog.Level.INFO));
        activity.add(new ActivityLog(LocalDateTime.of(2026, 11, 8, 12, 39), "AI Worker",
                "EXTRACTION_WARNING", "Bank_Statements_Scan.pdf failed OCR validation (confidence 0.41 < 0.70 threshold) - queued for retry", ActivityLog.Level.WARNING));
        activity.add(new ActivityLog(LocalDateTime.of(2026, 11, 7, 9, 18), "System",
                "CALENDAR_SYNC", "Pre-trial conference + Hearing pushed to Google Calendar (2 events) for C-2026-014", ActivityLog.Level.SUCCESS));
        activity.add(new ActivityLog(LocalDateTime.of(2026, 11, 6, 16, 20), "Ally Mongala",
                "UPLOAD", "Pre-Trial_Minutes_2026-11.pdf uploaded to C-2026-014 (210 KB)", ActivityLog.Level.INFO));
        activity.add(new ActivityLog(LocalDateTime.of(2026, 11, 5, 10, 5), "Ally Mongala",
                "AI_SUMMARY_GENERATED", "3-paragraph draft summary created for Rates_Assessment_2026.pdf (C-2026-011)", ActivityLog.Level.INFO));

        // users (demo admin portal - FR8)
        users.add(new User("u1", "Ally Mongala", "ally@mongalalaw.co.za", "lawyer123", Role.LAWYER, true));
        users.add(new User("u2", "Thabo Mokoena", "thabo@example.com", "client123", Role.CLIENT, true));
        users.add(new User("u3", "Elna van Wyk", "elna@example.com", "client123", Role.CLIENT, true));
        users.add(new User("u4", "System Admin", "admin@mongalalaw.co.za", "admin123", Role.ADMIN, true));

        // ratings (FR7)
        ratings.add(new Rating("r1", "c1", "u2", "Ally Mongala", 5,
                "Very clear updates, kept me informed throughout.", LocalDateTime.of(2026, 11, 8, 10, 0)));
        ratings.add(new Rating("r2", "c2", "u3", "Ally Mongala", 4,
                "Good communication, could improve on turnaround times.", LocalDateTime.of(2026, 11, 9, 9, 30)));

        // training runs + datasets (FR10)
        trainingRuns.add(new TrainingRun("tr1", "legal-nlp-extractor-v4", "ds1", TrainingRun.Status.PENDING,
                "Ally Mongala", LocalDateTime.of(2026, 11, 10, 14, 0), null, null));
        trainingRuns.add(new TrainingRun("tr2", "legal-nlp-summariser-v2", "ds2", TrainingRun.Status.PENDING,
                "System", LocalDateTime.of(2026, 11, 11, 9, 0), null, null));
        datasets.add(new Dataset("ds1", "Legal PDF corpus - dates & parties (v3)", 1240, 48200, "datasets/ds1_seed.csv", LocalDateTime.of(2026, 11, 1, 10, 0)));
        datasets.add(new Dataset("ds2", "SA case law summaries - corrected annotations", 860, 31800, "datasets/ds2_seed.csv", LocalDateTime.of(2026, 11, 4, 11, 0)));
    }

    private void seedExtraction(String docId, String field, String value, double confidence,
                                String span, int page) {
        Extraction ex = new Extraction(field, value, confidence, span, page);
        ex.setDocumentId(docId);
        extractions.add(ex);
    }
}
