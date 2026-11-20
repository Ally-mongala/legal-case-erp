package za.co.mongala.legal.backend.api;

import io.javalin.http.Context;
import za.co.mongala.legal.model.Case;
import za.co.mongala.legal.model.CaseStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Case management endpoints (central legal database):
 *   GET  /api/cases          - list / search
 *   POST /api/cases          - create (POST /api/cases, US1)
 *   GET  /api/cases/{id}     - single case
 *   GET  /api/cases/{id}/documents
 */
public class CaseController {

    private final ApiContext api;

    public CaseController(ApiContext api) {
        this.api = api;
    }

    public void list(Context ctx) {
        ApiContext.require(ctx, "read:case");
        String q = ctx.queryParam("q");
        ctx.json(api.cases.search(q));
    }

    public void create(Context ctx) {
        ApiContext.require(ctx, "write:case");
        Case incoming = ctx.bodyAsClass(Case.class);
        String id = "c" + UUID.randomUUID().toString().substring(0, 10);
        String ref = api.cases.nextReference(LocalDate.now().getYear());
        String clientName = incoming.getClientName();
        if (incoming.getClientId() != null && (clientName == null || clientName.isBlank())) {
            var client = api.users.findByEmail(incoming.getClientId());
            clientName = client == null ? "Client" : client.getName();
        }
        String clientFinal = clientName == null ? "Client" : clientName;
        String opposingFinal = incoming.getOpposingParty() == null ? "Unknown" : incoming.getOpposingParty();
        var conflictReport = api.conflicts.check(clientFinal, opposingFinal, null);

        Case c = new Case(
                id,
                ref,
                incoming.getTitle() == null ? "Untitled matter" : incoming.getTitle(),
                incoming.getClientId(),
                clientFinal,
                opposingFinal,
                incoming.getStatus() == null ? CaseStatus.OPEN : incoming.getStatus(),
                incoming.getMatterType() == null ? "General" : incoming.getMatterType(),
                incoming.getCourt() == null ? "High Court" : incoming.getCourt(),
                incoming.getDescription() == null ? "" : incoming.getDescription(),
                LocalDate.now(),
                Math.max(0, incoming.getAmountInvolved()));
        api.cases.insert(c);
        api.audit.success(ApiContext.session(ctx).name(), "CASE_CREATED", "Case " + ref + " created: " + c.getTitle());
        if (conflictReport.hasCriticalConflict()) {
            api.audit.warning(ApiContext.session(ctx).name(), "CONFLICT_ALERT",
                    "Conflict of interest flagged on " + ref + ": " + conflictReport.matches().get(0).explanation());
        }
        ctx.status(201).json(c);
    }

    /** Live conflict-of-interest check across all firm matters and AI-extracted parties. */
    public void checkConflicts(Context ctx) {
        ApiContext.require(ctx, "read:case");
        String clientName = ctx.queryParam("clientName");
        String opposingParty = ctx.queryParam("opposingParty");
        String excludeCaseId = ctx.queryParam("excludeCaseId");
        var report = api.conflicts.check(clientName, opposingParty, excludeCaseId);
        if (report.hasCriticalConflict()) {
            api.audit.warning(ApiContext.session(ctx).name(), "CONFLICT_CHECK",
                    "Critical conflict flagged for prospective mandate ("
                            + (clientName == null ? "" : clientName) + " vs "
                            + (opposingParty == null ? "" : opposingParty) + ")");
        }
        ctx.json(report);
    }

    public void get(Context ctx) {
        String caseId = ctx.pathParam("id");
        ApiContext.requireCaseRead(ctx, caseId, api);
        Case c = api.cases.findById(caseId);
        if (c == null) { api.notFound(ctx, "Case"); return; }
        ctx.json(c);
    }

    /** Update a case and alert the client (PATCH /api/cases/{id}). */
    public void update(Context ctx) {
        ApiContext.require(ctx, "write:case");
        String id = ctx.pathParam("id");
        Case existing = api.cases.findById(id);
        if (existing == null) { api.notFound(ctx, "Case"); return; }
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        String title = body.get("title") == null ? existing.getTitle() : String.valueOf(body.get("title"));
        String opposing = body.get("opposingParty") == null ? existing.getOpposingParty() : String.valueOf(body.get("opposingParty"));
        String matter = body.get("matterType") == null ? existing.getMatterType() : String.valueOf(body.get("matterType"));
        String court = body.get("court") == null ? existing.getCourt() : String.valueOf(body.get("court"));
        String desc = body.get("description") == null ? existing.getDescription() : String.valueOf(body.get("description"));
        double amount = existing.getAmountInvolved();
        if (body.get("amountInvolved") != null) {
            try { amount = Double.parseDouble(String.valueOf(body.get("amountInvolved"))); } catch (Exception ignored) {}
        }
        api.cases.updateFields(id, title, opposing, matter, court, desc, amount);
        // alert the client (FR: alert the client if there are changes)
        api.messages.insert(new za.co.mongala.legal.model.ClientMessage(
                "m" + UUID.randomUUID().toString().substring(0, 10), id,
                ApiContext.session(ctx).name(), "Case updated",
                "The details of your matter " + existing.getReference() + " were updated by the firm. Please review your portal.",
                java.time.LocalDateTime.now(), true, false, true));
        api.audit.success(ApiContext.session(ctx).name(), "CASE_UPDATED",
                "Case " + existing.getReference() + " updated - client notified");
        ctx.json(api.cases.findById(id));
    }

    /** Update only the case status (PATCH /api/cases/{id}/status) + alert client. */
    public void updateStatus(Context ctx) {
        ApiContext.require(ctx, "write:case");
        String id = ctx.pathParam("id");
        Case existing = api.cases.findById(id);
        if (existing == null) { api.notFound(ctx, "Case"); return; }
        Map<String, String> body = ctx.bodyAsClass(Map.class);
        String statusName = body.get("status");
        CaseStatus status;
        try {
            status = CaseStatus.valueOf(statusName);
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", "Invalid status - use one of: " + java.util.Arrays.toString(CaseStatus.values())));
            return;
        }
        api.cases.setStatus(id, status);
        api.messages.insert(new za.co.mongala.legal.model.ClientMessage(
                "m" + UUID.randomUUID().toString().substring(0, 10), id,
                ApiContext.session(ctx).name(), "Case status updated",
                "The status of your matter " + existing.getReference() + " changed to: " + status.getDisplayName() + ".",
                java.time.LocalDateTime.now(), true, false, true));
        api.audit.success(ApiContext.session(ctx).name(), "CASE_STATUS_CHANGED",
                "Case " + existing.getReference() + " status -> " + status.getDisplayName() + " - client notified");
        ctx.json(api.cases.findById(id));
    }

    /** Delete a case (DELETE /api/cases/{id}). */
    public void delete(Context ctx) {
        ApiContext.require(ctx, "write:case");
        String id = ctx.pathParam("id");
        Case existing = api.cases.findById(id);
        if (existing == null) { api.notFound(ctx, "Case"); return; }
        api.cases.delete(id);
        api.audit.warning(ApiContext.session(ctx).name(), "CASE_DELETED",
                "Case " + existing.getReference() + " (" + existing.getTitle() + ") deleted with all records");
        ctx.json(Map.of("ok", true, "message", "Case " + existing.getReference() + " deleted"));
    }

    public void documents(Context ctx) {
        String caseId = ctx.pathParam("id");
        ApiContext.requireCaseRead(ctx, caseId, api);
        Case c = api.cases.findById(caseId);
        if (c == null) { api.notFound(ctx, "Case"); return; }
        ctx.json(api.documents.byCase(c.getId()));
    }

    /* ---------------- Case Intelligence, .docx Templates, Court Bundle & Precedent Search ---------------- */

    public void caseIntelligence(Context ctx) {
        String caseId = ctx.pathParam("id");
        ApiContext.requireCaseRead(ctx, caseId, api);
        var rep = api.intelligence.analyse(caseId);
        if (rep == null) { api.notFound(ctx, "Case"); return; }
        ctx.json(rep);
    }

    public void downloadTemplate(Context ctx) {
        ApiContext.require(ctx, "read:case");
        String caseId = ctx.pathParam("id");
        Case c = api.cases.findById(caseId);
        if (c == null) { api.notFound(ctx, "Case"); return; }
        String rawType = ctx.pathParam("templateType").replace(".docx", "");

        var docs = api.documents.byCase(caseId).stream()
                .filter(d -> d.getProcessingStatus() == za.co.mongala.legal.model.LegalDocument.ProcessingStatus.PROCESSED)
                .toList();
        List<za.co.mongala.legal.model.Extraction> allEx = docs.stream()
                .flatMap(d -> api.extractions.byDocument(d.getId()).stream())
                .toList();
        var sumList = api.summaries.byCase(caseId);
        var bestSum = sumList.stream()
                .filter(s -> s.getStatus() == za.co.mongala.legal.model.Summary.SummaryStatus.APPROVED)
                .findFirst().orElse(sumList.isEmpty() ? null : sumList.get(0));

        byte[] docx = api.templates.generateDocx(rawType, c, allEx, bestSum, docs);
        String fname = rawType.replaceAll("[^a-zA-Z0-9_-]", "_") + "-" + c.getReference() + ".docx";
        api.audit.info(ApiContext.session(ctx).name(), "TEMPLATE_GENERATED",
                "Generated Word pleading template \"" + fname + "\" for " + c.getReference());
        ctx.contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        ctx.header("Content-Disposition", "attachment; filename=" + fname);
        ctx.result(docx);
    }

    public void downloadCourtBundle(Context ctx) {
        ApiContext.require(ctx, "read:case");
        String caseId = ctx.pathParam("id");
        Case c = api.cases.findById(caseId);
        if (c == null) { api.notFound(ctx, "Case"); return; }
        byte[] pdf = api.courtBundle.compileBundlePdf(c);
        api.audit.info(ApiContext.session(ctx).name(), "COURT_BUNDLE_GENERATED",
                "Compiled paginated Court Bundle PDF for " + c.getReference());
        ctx.contentType("application/pdf");
        ctx.header("Content-Disposition", "attachment; filename=CourtBundle-" + c.getReference() + ".pdf");
        ctx.result(pdf);
    }

    public void searchPrecedents(Context ctx) {
        ApiContext.require(ctx, "read:case");
        String q = ctx.queryParam("q");
        ctx.json(api.precedentSearch.search(q));
    }
}
