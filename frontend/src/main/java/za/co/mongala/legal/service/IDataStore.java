package za.co.mongala.legal.service;

import za.co.mongala.legal.model.*;

import java.util.List;
import java.util.Optional;

/**
 * Data access contract used by the UI.
 *
 * Two implementations:
 *  - DataStore          : in-memory demo data (DEMO mode)
 *  - RemoteDataStore    : REST calls to the LegalCase ERP backend (CONNECTED mode)
 */
public interface IDataStore {

    List<Case> getAllCases();

    List<Case> findCases(String query);

    Optional<Case> findCaseById(String id);

    List<Case> findCasesForClient(String clientId);

    List<LegalDocument> documentsForCase(String caseId);

    List<Extraction> extractionsForDocument(String documentId);

    List<Summary> summariesForCase(String caseId);

    List<Deadline> allDeadlines();

    List<Deadline> deadlinesForCase(String caseId);

    List<ClientMessage> messagesForCase(String caseId);

    long unreadCountForClient(String clientId);

    List<Extraction> allExtractions();

    void clearExtractionsForDocument(String documentId);

    void clearSummaryForDocument(String documentId);

    List<ActivityLog> recentActivity(int limit);

    /** Create a new matter (US1: POST /api/cases server-side). Returns the persisted case. */
    Case createCase(Case template);

    /* ---- FR7: client ratings ---- */
    void createRating(Rating r);
    List<Rating> ratingsList();

    /* ---- FR8: admin user management ---- */
    List<User> listUsers();
    void setUserActive(String userId, boolean active);

    /* ---- FR9: admin reports ---- */
    String auditCsv();
    List<RejectedDocument> rejectionsList();
    String rejectionsCsv();

    /* ---- FR10: admin system & model ---- */
    java.util.Map<String, Object> metrics();
    List<TrainingRun> trainingRuns();
    void approveTrainingRun(String runId);
    List<Dataset> datasets();

    /* ---- Case lifecycle (lawyer) ---- */
    Case updateCase(Case c);
    Case updateCaseStatus(String caseId, CaseStatus status);
    void deleteCase(String caseId);

    /* ---- Message alerts ---- */
    long lawyerUnreadCount();
    void markCaseReadByLawyer(String caseId);
    void sendLawyerMessage(String caseId, String subject, String body);

    /* ---- Document download / share / delete / full text ---- */
    byte[] downloadDocument(String documentId);
    void uploadClientDocument(String caseId, java.io.File file, boolean consent);
    void deleteDocument(String documentId);
    String documentContent(String documentId);

    /* ---- Admin datasets & reports ---- */
    void uploadDataset(java.io.File file, String name);
    byte[] downloadDataset(String datasetId);
    String systemReport();

    // ---- local-only mutations (remote mode: performed server-side) ----
    String addDocument(LegalDocument doc);

    void addExtractions(String documentId, List<Extraction> ex);

    void addSummary(Summary s);

    void addDeadline(Deadline d);

    void addMessage(ClientMessage m);

    void addActivity(ActivityLog a);

    int nextId();
}
