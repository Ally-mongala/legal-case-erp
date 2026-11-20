package za.co.mongala.legal.client;

import javafx.application.Platform;
import javafx.concurrent.Task;
import za.co.mongala.legal.model.*;
import za.co.mongala.legal.service.DocumentWorkflow;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * CONNECTED-mode workflow: drives the backend worker pipeline.
 *
 *  - processUpload:  POST multipart -> poll GET /api/documents/{id} until
 *                    PROCESSED/FAILED, then fetch the extractions + summary
 *  - approve:        POST /api/summaries/{id}/approve (server notifies client)
 *  - save draft:     PUT /api/summaries/{id}
 *  - sync:           POST /api/calendar/sync
 *  - message:        POST /api/cases/{id}/messages
 */
public class RemoteDocumentWorkflow implements DocumentWorkflow {

    private final ApiClient api;

    public RemoteDocumentWorkflow(ApiClient api) {
        this.api = api;
    }

    @Override
    public void processUpload(File file, Case legalCase, boolean consentGranted,
                              ProgressListener progress, ResultListener done) {
        Map<String, Object> created = api.uploadFile("/api/cases/" + legalCase.getId() + "/documents", file,
                "file", Map.of("consent", String.valueOf(consentGranted)));
        String docId = String.valueOf(created.get("id"));
        poll(docId, file.getName(), progress, done);
    }

    @Override
    public void retryProcessing(LegalDocument doc, Case legalCase, ProgressListener progress, ResultListener done) {
        api.post("/api/documents/" + doc.getId() + "/retry", Map.of(), Map.class);
        poll(doc.getId(), doc.getFileName(), progress, done);
    }

    private void poll(String docId, String fileName, ProgressListener progress, ResultListener done) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                int attempts = 0;
                while (true) {
                    attempts++;
                    Thread.sleep(450);
                    LegalDocument doc = api.get("/api/documents/" + docId, LegalDocument.class);
                    double p = Math.min(0.9, attempts * 0.15);
                    if (progress != null) {
                        double frac = p;
                        Platform.runLater(() -> progress.onProgress(frac,
                                "AI NLP worker analysing \"" + fileName + "\"..."));
                    }
                    if (doc.getProcessingStatus() == LegalDocument.ProcessingStatus.PROCESSED) {
                        LegalDocument finished = doc;
                        Platform.runLater(() -> {
                            if (progress != null) progress.onProgress(1.0, "Done");
                            if (done != null) done.onProcessed(finished);
                        });
                        return null;
                    }
                    if (doc.getProcessingStatus() == LegalDocument.ProcessingStatus.REJECTED) {
                        LegalDocument rejected = doc;
                        if (progress != null) Platform.runLater(() -> progress.onProgress(1.0, "Blocked by AI validation"));
                        Platform.runLater(() -> {
                            if (done != null) done.onRejected(rejected,
                                    rejected.getRejectionReason() == null ? "document does not match the case" : rejected.getRejectionReason());
                        });
                        return null;
                    }
                    if (doc.getProcessingStatus() == LegalDocument.ProcessingStatus.FAILED) {
                        LegalDocument failed = doc;
                        if (progress != null) Platform.runLater(() -> progress.onProgress(1.0, "Processing failed"));
                        Platform.runLater(() -> {
                            if (done != null) done.onFailed(failed, "AI processing failed server-side");
                        });
                        return null;
                    }
                    if (attempts > 80) throw new IllegalStateException("Timed out waiting for AI processing");
                }
            }
        };
        task.setOnFailed(e -> {
            if (done != null) {
                Throwable t = e.getSource().getException();
                done.onFailed(api.get("/api/documents/" + docId, LegalDocument.class),
                        t == null ? "AI processing failed" : t.getMessage());
            }
        });
        new Thread(task).start();
    }

    @Override
    public Summary saveSummaryDraft(Summary summary) {
        Map<String, Object> body = Map.of(
                "paragraph1", summary.getParagraph1() == null ? "" : summary.getParagraph1(),
                "paragraph2", summary.getParagraph2() == null ? "" : summary.getParagraph2(),
                "paragraph3", summary.getParagraph3() == null ? "" : summary.getParagraph3());
        return api.put("/api/summaries/" + summary.getId(), body, Summary.class);
    }

    @Override
    public Summary approveSummary(Summary summary, Case legalCase) {
        return api.post("/api/summaries/" + summary.getId() + "/approve", Map.of(), Summary.class);
    }

    @Override
    public String syncDeadline(Deadline d) {
        Map<String, Object> resp = api.post("/api/calendar/sync",
                Map.of("deadlineIds", List.of(d.getId())), Map.class);
        d.setSynced(true);
        return String.valueOf(resp.getOrDefault("message", "Synced"));
    }

    @Override
    public String syncAllDeadlines(List<Deadline> deadlines) {
        List<String> ids = deadlines.stream().map(Deadline::getId).toList();
        Map<String, Object> resp = api.post("/api/calendar/sync", Map.of("deadlineIds", ids), Map.class);
        deadlines.forEach(d -> d.setSynced(true));
        return String.valueOf(resp.getOrDefault("message", "Synced"));
    }

    @Override
    public String unsyncDeadline(Deadline d) {
        Map<String, Object> resp = api.post("/api/calendar/unsync",
                Map.of("deadlineIds", List.of(d.getId())), Map.class);
        d.setSynced(false);
        return String.valueOf(resp.getOrDefault("message", "Removed from calendar"));
    }

    @Override
    public String unsyncAllDeadlines(List<Deadline> deadlines) {
        List<String> ids = deadlines.stream().map(Deadline::getId).toList();
        Map<String, Object> resp = api.post("/api/calendar/unsync", Map.of("deadlineIds", ids), Map.class);
        deadlines.forEach(d -> d.setSynced(false));
        return String.valueOf(resp.getOrDefault("message", "Removed from calendar"));
    }

    @Override
    public void sendLawyerMessage(Case legalCase, String subject, String text) {
        api.post("/api/cases/" + legalCase.getId() + "/messages/lawyer",
                Map.of("subject", subject == null ? "Message from your attorney" : subject,
                        "body", text == null ? "" : text), Map.class);
    }

    @Override
    public void sendClientMessage(Case legalCase, String text) {
        api.post("/api/cases/" + legalCase.getId() + "/messages",
                Map.of("body", text.trim()), Map.class);
    }
}
