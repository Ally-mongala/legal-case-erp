package za.co.mongala.legal.service;

import za.co.mongala.legal.model.Case;
import za.co.mongala.legal.model.Deadline;
import za.co.mongala.legal.model.LegalDocument;
import za.co.mongala.legal.model.Summary;

import java.io.File;
import java.util.List;

/**
 * The human-in-the-loop document workflow the UI drives (FR1-FR7).
 *
 * LocalDocumentWorkflow implements it with the simulated AI (DEMO mode);
 * RemoteDocumentWorkflow calls the backend REST API, where the worker pipeline,
 * real PDF extraction, NLP and calendar gateway actually run (CONNECTED mode).
 */
public interface DocumentWorkflow {

    /** Progress callback during AI processing (used to drive the progress bar). */
    interface ProgressListener {
        void onProgress(double fraction, String message);
    }

    /** Completion callback after processing finishes (success or failure). */
    interface ResultListener {
        void onProcessed(LegalDocument doc);
        void onRejected(LegalDocument doc, String reason);
        void onFailed(LegalDocument doc, String reason);
    }

    /** Upload a PDF/Word document and run it through AI processing (FR1 -> FR2/FR3/FR4). */
    void processUpload(File file, Case legalCase, boolean consentGranted,
                       ProgressListener progress, ResultListener done);

    /** Re-run AI processing on a FAILED document (FR7). */
    void retryProcessing(LegalDocument doc, Case legalCase, ProgressListener progress, ResultListener done);

    /** Save lawyer edits to the draft (FR6); returns the refreshed summary. */
    Summary saveSummaryDraft(Summary summary);

    /** Approve and publish to the client portal (FR6/US2); returns the refreshed summary. */
    Summary approveSummary(Summary summary, Case legalCase);

    /** Sync one deadline to the calendar (FR4). Returns a receipt string. */
    String syncDeadline(Deadline d);

    /** Sync all given deadlines (FR4). Returns a receipt string. */
    String syncAllDeadlines(List<Deadline> deadlines);

    /** Unsync one deadline (calendar unsynchronise toggle). */
    String unsyncDeadline(Deadline d);

    /** Unsync all given deadlines. Returns a receipt string. */
    String unsyncAllDeadlines(List<Deadline> deadlines);

    /** Lawyer sends a message to the client from the case chat. */
    void sendLawyerMessage(Case legalCase, String subject, String text);

    /** Send a client message through the portal (FR5). */
    void sendClientMessage(Case legalCase, String text);
}
