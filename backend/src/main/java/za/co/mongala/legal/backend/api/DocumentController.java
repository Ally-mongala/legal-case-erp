package za.co.mongala.legal.backend.api;

import io.javalin.http.Context;
import io.javalin.http.UploadedFile;
import za.co.mongala.legal.model.Case;
import za.co.mongala.legal.model.LegalDocument;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Document endpoints (FR1 / FR7):
 *   POST /api/cases/{id}/documents   - multipart PDF/Word upload (with POPIA consent flag) -> enqueue AI job
 *   GET  /api/documents/{id}         - status polling (PROCESSING -> PROCESSED/FAILED)
 *   GET  /api/documents/{id}/extractions
 *   POST /api/documents/{id}/retry
 *   GET  /api/extractions            - all extractions (dashboard confidence metric)
 *
 * FR11: files are encrypted at rest (AES-256-GCM) before being written to disk;
 * the pipeline decrypts them in memory only.
 */
public class DocumentController {

    private final ApiContext api;

    public DocumentController(ApiContext api) {
        this.api = api;
    }

    public void upload(Context ctx) {
        // lawyers/admin need write:case; a client may share documents on their own case (write:client + owner)
        var session = ApiContext.session(ctx);
        if (session.role() == za.co.mongala.legal.model.Role.CLIENT) {
            if (!session.has("write:client")) throw new io.javalin.http.ForbiddenResponse("Insufficient scope");
        } else {
            ApiContext.require(ctx, "write:case");
        }
        String caseId = ctx.pathParam("id");
        Case c = api.cases.findById(caseId);
        if (c == null) { api.notFound(ctx, "Case"); return; }
        ApiContext.requireClientOwner(ctx, caseId, api);

        List<UploadedFile> files = ctx.uploadedFiles("file");
        if (files.isEmpty()) {
            ctx.status(400).json(Map.of("error", "No file provided (multipart field name: file)"));
            return;
        }
        UploadedFile uf = files.get(0);
        String lower = uf.filename() == null ? "" : uf.filename().toLowerCase();
        boolean word = lower.endsWith(".doc") || lower.endsWith(".docx");
        if (!(lower.endsWith(".pdf") || word)) {
            ctx.status(400).json(Map.of("error", "Only PDF or Word (.doc/.docx) files are supported (FR1)"));
            return;
        }

        // POPIA consent control (FR11) - passed as a multipart form field
        String consentParam = ctx.formParam("consent");
        boolean consent = consentParam == null || !"false".equalsIgnoreCase(consentParam);
        if (!consent) {
            ctx.status(400).json(Map.of("error", "Client consent is required before processing the document (POPIA / FR11)"));
            return;
        }

        String id = "d" + UUID.randomUUID().toString().substring(0, 10);
        String storage = caseId + "/" + id + "_" + sanitize(uf.filename()) + ".enc";
        Path target = api.uploadDir.resolve(storage);
        try {
            Files.createDirectories(target.getParent());
            byte[] bytes;
            try (InputStream in = uf.content()) {
                bytes = in.readAllBytes();
            }
            // FR11: encrypt at rest
            String encrypted = api.encryption.encrypt(bytes);
            Files.writeString(target, encrypted, java.nio.charset.StandardCharsets.UTF_8);
            long kb = Math.max(1, bytes.length / 1024);

            LegalDocument doc = new LegalDocument(id, caseId, uf.filename(), storage,
                    ApiContext.session(ctx).name(), LocalDateTime.now(), kb,
                    LegalDocument.ProcessingStatus.PROCESSING,
                    word ? "WORD" : "PDF", consent);
            api.documents.insert(doc);
            api.audit.info(ApiContext.session(ctx).name(), "UPLOAD",
                    uf.filename() + " (" + doc.getDocType() + ") uploaded to " + c.getReference()
                            + " - encrypted at rest, consent=" + consent);
            api.pipeline.enqueue(doc, c);
            ctx.status(201).json(doc);
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Could not store file: " + e.getMessage()));
        }
    }

    public void status(Context ctx) {
        ApiContext.require(ctx, "read:case");
        LegalDocument doc = api.documents.findById(ctx.pathParam("id"));
        if (doc == null) { api.notFound(ctx, "Document"); return; }
        ctx.json(doc);
    }

    public void extractions(Context ctx) {
        ApiContext.require(ctx, "read:case");
        String docId = ctx.pathParam("id");
        if (api.documents.findById(docId) == null) { api.notFound(ctx, "Document"); return; }
        ctx.json(api.extractions.byDocument(docId));
    }

    public void retry(Context ctx) {
        ApiContext.require(ctx, "write:case");
        String docId = ctx.pathParam("id");
        LegalDocument doc = api.documents.findById(docId);
        if (doc == null) { api.notFound(ctx, "Document"); return; }
        Case c = api.cases.findById(doc.getCaseId());
        api.audit.info(ApiContext.session(ctx).name(), "AI_RETRY", "Manual retry requested for " + doc.getFileName());
        api.pipeline.retry(doc, c);
        ctx.status(202).json(Map.of("message", "Retry queued", "documentId", docId));
    }

    /** Download a shared document - decrypted bytes (client: own case only). */
    public void download(Context ctx) {
        String docId = ctx.pathParam("id");
        LegalDocument doc = api.documents.findById(docId);
        if (doc == null) { api.notFound(ctx, "Document"); return; }
        ApiContext.requireCaseRead(ctx, doc.getCaseId(), api);
        try {
            java.nio.file.Path file = api.uploadDir.resolve(doc.getStoragePath());
            byte[] bytes;
            if (java.nio.file.Files.exists(file)) {
                String b64 = java.nio.file.Files.readString(file);
                bytes = api.encryption.decrypt(b64);
            } else {
                // seed/demo document: no physical file - serve an informative placeholder
                String note = "LegalCase ERP\n\nDocument: " + doc.getFileName() + "\nCase: " + doc.getCaseId()
                        + "\nUploaded by: " + doc.getUploadedBy() + "\n\nThis is a seeded sample record. "
                        + "Documents uploaded through the app are stored encrypted and downloaded in full.";
                bytes = note.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                ctx.contentType("text/plain");
            }
            if (ctx.contentType() == null) {
                ctx.contentType(doc.getDocType().equals("WORD") && doc.getFileName().toLowerCase().endsWith(".docx")
                        ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                        : doc.getDocType().equals("WORD") ? "application/msword" : "application/pdf");
            }
            ctx.header("Content-Disposition", "attachment; filename=" + doc.getFileName());
            ctx.result(bytes);
            api.audit.info(ApiContext.session(ctx).name(), "DOCUMENT_DOWNLOAD",
                    ApiContext.session(ctx).name() + " downloaded " + doc.getFileName() + " (case " + doc.getCaseId() + ")");
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Could not serve document: " + e.getMessage()));
        }
    }

    /** Read the full extracted text of a document (lawyer: read the document in full). */
    public void content(Context ctx) {
        String docId = ctx.pathParam("id");
        LegalDocument doc = api.documents.findById(docId);
        if (doc == null) { api.notFound(ctx, "Document"); return; }
        ApiContext.requireCaseRead(ctx, doc.getCaseId(), api);
        String fullText = api.documents.getFullText(docId);
        if (fullText == null || fullText.isBlank()) {
            ctx.status(404).json(Map.of("error", "No extracted text available - the document may not have been processed yet."));
            return;
        }
        ctx.json(Map.of(
                "id", doc.getId(),
                "fileName", doc.getFileName(),
                "docType", doc.getDocType(),
                "caseId", doc.getCaseId(),
                "fullText", fullText,
                "characters", fullText.length(),
                "extractions", api.extractions.byDocument(docId)));
    }

    /** Delete an uploaded document (lawyer: any; client: documents on their own case). */
    public void delete(Context ctx) {
        String docId = ctx.pathParam("id");
        LegalDocument doc = api.documents.findById(docId);
        if (doc == null) { api.notFound(ctx, "Document"); return; }

        var session = ApiContext.session(ctx);
        if (session.role() == za.co.mongala.legal.model.Role.CLIENT) {
            if (!session.has("write:client")) throw new io.javalin.http.ForbiddenResponse("Insufficient scope");
            ApiContext.requireClientOwner(ctx, doc.getCaseId(), api);
        } else {
            ApiContext.require(ctx, "write:case");
        }

        // remove the encrypted file from disk
        try {
            java.nio.file.Path file = api.uploadDir.resolve(doc.getStoragePath());
            java.nio.file.Files.deleteIfExists(file);
        } catch (Exception ignored) {}

        api.documents.delete(docId);
        api.audit.warning(session.name(), "DOCUMENT_DELETED",
                session.name() + " deleted document \"" + doc.getFileName() + "\" (case " + doc.getCaseId() + ")");
        ctx.json(Map.of("ok", true, "message", "Document deleted"));
    }

    public void allExtractions(Context ctx) {
        ApiContext.require(ctx, "read:case");
        ctx.json(api.extractions.all());
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
