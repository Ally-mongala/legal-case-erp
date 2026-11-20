package za.co.mongala.legal.client;

import com.fasterxml.jackson.core.type.TypeReference;
import za.co.mongala.legal.model.*;
import za.co.mongala.legal.service.IDataStore;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * CONNECTED-mode data access: every query becomes a REST call to the backend
 * (Assignment 2 section 11). The backend database is the single source of truth.
 */
public class RemoteDataStore implements IDataStore {

    private final ApiClient api;
    private final TypeReference<List<Case>> CASE_LIST = new TypeReference<>() {};
    private final TypeReference<List<LegalDocument>> DOC_LIST = new TypeReference<>() {};
    private final TypeReference<List<Extraction>> EX_LIST = new TypeReference<>() {};
    private final TypeReference<List<Summary>> SUM_LIST = new TypeReference<>() {};
    private final TypeReference<List<Deadline>> DL_LIST = new TypeReference<>() {};
    private final TypeReference<List<ClientMessage>> MSG_LIST = new TypeReference<>() {};
    private final TypeReference<List<ActivityLog>> ACT_LIST = new TypeReference<>() {};
    private final TypeReference<List<Rating>> RAT_LIST = new TypeReference<>() {};
    private final TypeReference<List<User>> USER_LIST = new TypeReference<>() {};
    private final TypeReference<List<TrainingRun>> TR_LIST = new TypeReference<>() {};
    private final TypeReference<List<Dataset>> DS_LIST = new TypeReference<>() {};
    private final TypeReference<List<RejectedDocument>> REJ_LIST = new TypeReference<>() {};

    public RemoteDataStore(ApiClient api) {
        this.api = api;
    }

    @Override
    public List<Case> getAllCases() { return api.get("/api/cases", CASE_LIST); }

    @Override
    public List<Case> findCases(String query) {
        String q = query == null || query.isBlank() ? "" : query;
        return api.get("/api/cases?q=" + java.net.URLEncoder.encode(q, java.nio.charset.StandardCharsets.UTF_8), CASE_LIST);
    }

    @Override
    public Optional<Case> findCaseById(String id) {
        try {
            return Optional.ofNullable(api.get("/api/cases/" + id, Case.class));
        } catch (IllegalStateException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<Case> findCasesForClient(String clientId) {
        return api.get("/api/client/cases", CASE_LIST);
    }

    @Override
    public List<LegalDocument> documentsForCase(String caseId) {
        return api.get("/api/cases/" + caseId + "/documents", DOC_LIST);
    }

    @Override
    public List<Extraction> extractionsForDocument(String documentId) {
        return api.get("/api/documents/" + documentId + "/extractions", EX_LIST);
    }

    @Override
    public List<Summary> summariesForCase(String caseId) {
        return api.get("/api/cases/" + caseId + "/summaries", SUM_LIST);
    }

    @Override
    public List<Deadline> allDeadlines() { return api.get("/api/deadlines", DL_LIST); }

    @Override
    public List<Deadline> deadlinesForCase(String caseId) {
        return api.get("/api/cases/" + caseId + "/deadlines", DL_LIST);
    }

    @Override
    public List<ClientMessage> messagesForCase(String caseId) {
        return api.get("/api/cases/" + caseId + "/messages", MSG_LIST);
    }

    @Override
    public long unreadCountForClient(String clientId) {
        Map<String, Object> m = api.get("/api/me/unread", Map.class);
        Object u = m == null ? null : m.get("unread");
        return u instanceof Number n ? n.longValue() : 0L;
    }

    @Override
    public List<Extraction> allExtractions() { return api.get("/api/extractions", EX_LIST); }

    @Override
    public Case createCase(Case template) {
        return api.post("/api/cases", template, Case.class);
    }

    @Override
    public void clearExtractionsForDocument(String documentId) { throw unsupported(); }

    @Override
    public void clearSummaryForDocument(String documentId) { throw unsupported(); }

    @Override
    public List<ActivityLog> recentActivity(int limit) {
        return api.get("/api/activity?limit=" + limit, ACT_LIST);
    }

    /* ---- Case lifecycle (lawyer) ---- */
    @Override
    public Case updateCase(Case c) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("title", c.getTitle());
        body.put("opposingParty", c.getOpposingParty());
        body.put("matterType", c.getMatterType());
        body.put("court", c.getCourt());
        body.put("description", c.getDescription());
        body.put("amountInvolved", c.getAmountInvolved());
        return api.patch("/api/cases/" + c.getId(), body, Case.class);
    }

    @Override
    public Case updateCaseStatus(String caseId, CaseStatus status) {
        return api.patch("/api/cases/" + caseId + "/status", Map.of("status", status.name()), Case.class);
    }

    @Override
    public void deleteCase(String caseId) {
        api.delete("/api/cases/" + caseId, Map.class);
    }

    /* ---- Message alerts ---- */
    @Override
    public long lawyerUnreadCount() {
        Map<String, Object> m = api.get("/api/me/unread-lawyer", Map.class);
        Object u = m == null ? null : m.get("unread");
        return u instanceof Number n ? n.longValue() : 0L;
    }

    @Override
    public void markCaseReadByLawyer(String caseId) {
        api.post("/api/cases/" + caseId + "/messages/read-by-lawyer", Map.of(), Map.class);
    }

    @Override
    public void sendLawyerMessage(String caseId, String subject, String body) {
        api.post("/api/cases/" + caseId + "/messages/lawyer",
                Map.of("subject", subject == null ? "Message from your attorney" : subject,
                        "body", body == null ? "" : body), Map.class);
    }

    /* ---- Document download / share ---- */
    @Override
    public byte[] downloadDocument(String documentId) {
        return api.downloadBytes("/api/documents/" + documentId + "/download");
    }

    @Override
    public void uploadClientDocument(String caseId, java.io.File file, boolean consent) {
        api.uploadFile("/api/cases/" + caseId + "/documents", file, "file",
                Map.of("consent", String.valueOf(consent)));
    }

    @Override
    public void deleteDocument(String documentId) {
        api.delete("/api/documents/" + documentId, Map.class);
    }

    @Override
    public String documentContent(String documentId) {
        Map<String, Object> m = api.get("/api/documents/" + documentId + "/content", Map.class);
        return m == null ? null : String.valueOf(m.getOrDefault("fullText", ""));
    }

    /* ---- Admin datasets & reports ---- */
    @Override
    public void uploadDataset(java.io.File file, String name) {
        api.uploadFile("/api/admin/datasets", file, "file", Map.of("name", name == null ? file.getName() : name));
    }

    @Override
    public byte[] downloadDataset(String datasetId) {
        return api.downloadBytes("/api/admin/datasets/" + datasetId + "/download");
    }

    @Override
    public String systemReport() { return api.get("/api/admin/reports/system.csv", String.class); }

    /* ---- FR4: manual deadlines ---- */
    @Override
    public void addDeadline(Deadline d) {
        api.post("/api/cases/" + d.getCaseId() + "/deadlines",
                Map.of("title", d.getTitle(), "date", d.getDate().toString(),
                        "type", d.getType().name(), "reminderDays", d.getReminderDays()), Map.class);
    }

    /* ---- FR7: ratings ---- */
    @Override
    public void createRating(Rating r) {
        api.post("/api/ratings", Map.of("caseId", r.getCaseId(), "score", r.getScore(),
                "comment", r.getComment() == null ? "" : r.getComment()), Map.class);
    }

    @Override
    public List<Rating> ratingsList() { return api.get("/api/ratings", RAT_LIST); }

    /* ---- FR8: admin user management ---- */
    @Override
    public List<User> listUsers() { return api.get("/api/admin/users", USER_LIST); }

    @Override
    public void setUserActive(String userId, boolean active) {
        api.post("/api/admin/users/" + userId + (active ? "/restore" : "/revoke"), Map.of(), Map.class);
    }

    /* ---- FR9: reports ---- */
    @Override
    public String auditCsv() { return api.get("/api/admin/reports/audit.csv", String.class); }

    @Override
    public List<RejectedDocument> rejectionsList() { return api.get("/api/admin/rejections", REJ_LIST); }

    @Override
    public String rejectionsCsv() { return api.get("/api/admin/reports/rejections.csv", String.class); }

    /* ---- FR10: system & model ---- */
    @Override
    public Map<String, Object> metrics() { return api.get("/api/admin/metrics", Map.class); }

    @Override
    public List<TrainingRun> trainingRuns() { return api.get("/api/admin/training-runs", TR_LIST); }

    @Override
    public void approveTrainingRun(String runId) {
        api.post("/api/admin/training-runs/" + runId + "/approve", Map.of(), Map.class);
    }

    @Override
    public List<Dataset> datasets() { return api.get("/api/admin/datasets", DS_LIST); }

    /* ---- local-only mutations: the backend performs these server-side ---- */

    @Override
    public String addDocument(LegalDocument doc) { throw unsupported(); }
    @Override
    public void addExtractions(String documentId, List<Extraction> ex) { throw unsupported(); }
    @Override
    public void addSummary(Summary s) { throw unsupported(); }
    @Override
    public void addMessage(ClientMessage m) { throw unsupported(); }
    @Override
    public void addActivity(ActivityLog a) { throw unsupported(); }
    @Override
    public int nextId() { throw unsupported(); }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException(
                "Server-side operation - not available on the client in CONNECTED mode");
    }
}
