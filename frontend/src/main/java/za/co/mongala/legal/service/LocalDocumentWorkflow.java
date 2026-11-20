package za.co.mongala.legal.service;

import za.co.mongala.legal.model.*;
import za.co.mongala.legal.service.LegalNlpSimulator.AiProcessingResult;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

/**
 * DEMO-mode workflow: the simulated AI runs on a background thread with a
 * visible progress bar, exactly as in the original prototype.
 */
public class LocalDocumentWorkflow implements DocumentWorkflow {

    private final IDataStore store;
    private final LegalNlpSimulator nlp;
    private final ICalendarSyncService calendarSync;
    private final IAuditService audit;
    private final Supplier<User> currentUser;

    public LocalDocumentWorkflow(IDataStore store, LegalNlpSimulator nlp,
                                 ICalendarSyncService calendarSync, IAuditService audit,
                                 Supplier<User> currentUser) {
        this.store = store;
        this.nlp = nlp;
        this.calendarSync = calendarSync;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    @Override
    public void processUpload(File file, Case legalCase, boolean consentGranted,
                              ProgressListener progress, ResultListener done) {
        String lower = file.getName().toLowerCase();
        LegalDocument doc = new LegalDocument(
                "d" + store.nextId(),
                legalCase.getId(),
                file.getName(),
                "s3://mongala-case-files/" + legalCase.getId() + "/" + file.getName(),
                currentUser.get().getName(),
                LocalDateTime.now(),
                Math.max(1, file.length() / 1024),
                LegalDocument.ProcessingStatus.PROCESSING,
                lower.endsWith(".doc") || lower.endsWith(".docx") ? "WORD" : "PDF",
                consentGranted);
        store.addDocument(doc);
        audit.info(currentUser.get().getName(), "UPLOAD",
                file.getName() + " (" + doc.getDocType() + ") uploaded to " + legalCase.getReference()
                        + " (" + doc.getSizeKb() + " KB) - consent=" + consentGranted);
        runAi(doc, legalCase, progress, done);
    }

    @Override
    public void retryProcessing(LegalDocument doc, Case legalCase, ProgressListener progress, ResultListener done) {
        store.clearExtractionsForDocument(doc.getId());
        store.clearSummaryForDocument(doc.getId());
        doc.setProcessingStatus(LegalDocument.ProcessingStatus.PROCESSING);
        runAi(doc, legalCase, progress, done);
    }

    private void runAi(LegalDocument doc, Case legalCase, ProgressListener progress, ResultListener done) {
        javafx.concurrent.Task<AiProcessingResult> task = new javafx.concurrent.Task<>() {
            @Override
            protected AiProcessingResult call() throws InterruptedException {
                int steps = 5;
                for (int i = 1; i <= steps; i++) {
                    Thread.sleep(350);
                    updateProgress(i, steps);
                }
                return nlp.processDocument(doc, legalCase);
            }
        };
        if (progress != null) {
            task.progressProperty().addListener((obs, o, n) ->
                    progress.onProgress(n.doubleValue(), "AI NLP worker analysing \"" + doc.getFileName() + "\"..."));
        }
        task.setOnSucceeded(e -> {
            AiProcessingResult r = task.getValue();
            store.addExtractions(doc.getId(), r.extractions());
            // Demo mode uses the simulated NLP worker, which derives extractions from the
            // case record itself - so uploaded documents always match and are never
            // wrongly blocked. (The backend performs the real AI validation; a seeded
            // REJECTED document in the demo data still demonstrates the blocking UI.)
            doc.setProcessingStatus(LegalDocument.ProcessingStatus.PROCESSED);
            store.addSummary(r.summary());
            audit.success(currentUser.get().getName(), "AI_PROCESSED",
                    doc.getFileName() + ": extracted " + r.extractions().size()
                            + " fields and generated a 3-paragraph draft summary");
            if (done != null) done.onProcessed(doc);
        });
        task.setOnFailed(e -> {
            doc.setProcessingStatus(LegalDocument.ProcessingStatus.FAILED);
            audit.warning("AI Worker", "EXTRACTION_WARNING",
                    doc.getFileName() + " failed processing - queued for retry (FR7)");
            if (done != null) done.onFailed(doc, "Processing failed");
        });
        new Thread(task).start();
    }

    @Override
    public Summary saveSummaryDraft(Summary summary) {
        audit.info(currentUser.get().getName(), "SUMMARY_EDITED",
                "Draft summary v" + summary.getVersion() + " edited for " + summary.getCaseId());
        return summary;
    }

    @Override
    public Summary approveSummary(Summary summary, Case legalCase) {
        summary.bumpVersion();
        summary.approve(LocalDateTime.now());
        store.addMessage(new ClientMessage(
                "m" + store.nextId(),
                legalCase.getId(),
                currentUser.get().getName(),
                "Your case update - summary approved",
                "Your updated case summary for " + legalCase.getReference()
                        + " has been approved and is now available in your portal.",
                LocalDateTime.now(), true, false, true));
        audit.success(currentUser.get().getName(), "SUMMARY_APPROVED",
                "Summary v" + summary.getVersion() + " approved for " + legalCase.getReference()
                        + " and published to the client portal");
        return summary;
    }

    @Override
    public String syncDeadline(Deadline d) {
        String receipt = calendarSync.sync(d);
        audit.success("System", "CALENDAR_SYNC", receipt);
        return receipt;
    }

    @Override
    public String syncAllDeadlines(List<Deadline> deadlines) {
        String receipt = calendarSync.syncAll(deadlines);
        audit.success("System", "CALENDAR_SYNC", receipt);
        return receipt;
    }

    @Override
    public String unsyncDeadline(Deadline d) {
        d.setSynced(false);
        audit.info("System", "CALENDAR_UNSYNC", "\"" + d.getTitle() + "\" removed from the calendar");
        return "Removed \"" + d.getTitle() + "\" from the calendar.";
    }

    @Override
    public String unsyncAllDeadlines(List<Deadline> deadlines) {
        long count = deadlines.stream().filter(Deadline::isSynced).count();
        deadlines.stream().filter(Deadline::isSynced).forEach(d -> d.setSynced(false));
        audit.info("System", "CALENDAR_UNSYNC", count + " deadline(s) removed from the calendar");
        return count == 0 ? "No synced deadlines to remove."
                : count + " deadline(s) removed from the calendar.";
    }

    @Override
    public void sendLawyerMessage(Case legalCase, String subject, String text) {
        store.addMessage(new ClientMessage(
                "m" + store.nextId(),
                legalCase.getId(),
                currentUser.get().getName(),
                subject == null || subject.isBlank() ? "Message from your attorney" : subject,
                text == null ? "" : text,
                LocalDateTime.now(), true, false, true));
        audit.info(currentUser.get().getName(), "CLIENT_MESSAGE",
                "Message sent to client on " + legalCase.getReference());
    }

    @Override
    public void sendClientMessage(Case legalCase, String text) {
        store.addMessage(new ClientMessage(
                "m" + store.nextId(),
                legalCase.getId(),
                currentUser.get().getName(),
                "Message from client",
                text.trim(),
                LocalDateTime.now(), false, false, false));
        audit.info(currentUser.get().getName(), "CLIENT_MESSAGE",
                "Message sent to firm on " + legalCase.getReference());
    }
}
