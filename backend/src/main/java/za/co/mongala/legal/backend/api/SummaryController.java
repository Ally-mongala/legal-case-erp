package za.co.mongala.legal.backend.api;

import io.javalin.http.Context;
import za.co.mongala.legal.model.ClientMessage;
import za.co.mongala.legal.model.Summary;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Summary endpoints - human-in-the-loop workflow (FR3 / FR6 / ETR3 / ETR4):
 *   GET  /api/cases/{id}/summaries      - all summaries for the case
 *   GET  /api/documents/{id}/summaries  - the summary for one document
 *   PUT  /api/summaries/{id}            - save lawyer edits (version bump)
 *   POST /api/summaries/{id}/approve    - approve + publish to client portal
 */
public class SummaryController {

    private final ApiContext api;

    public SummaryController(ApiContext api) {
        this.api = api;
    }

    public void byCase(Context ctx) {
        String caseId = ctx.pathParam("id");
        ApiContext.requireCaseRead(ctx, caseId, api);
        if (api.cases.findById(caseId) == null) { api.notFound(ctx, "Case"); return; }
        ctx.json(api.summaries.byCase(caseId));
    }

    public void byDocument(Context ctx) {
        ApiContext.require(ctx, "read:case");
        Summary s = api.summaries.byDocument(ctx.pathParam("id"));
        ctx.json(s == null ? Map.of() : s);
    }

    public void update(Context ctx) {
        ApiContext.require(ctx, "write:case");
        String id = ctx.pathParam("id");
        Summary existing = api.summaries.findById(id);
        if (existing == null) { api.notFound(ctx, "Summary"); return; }

        Map<String, String> body = ctx.bodyAsClass(Map.class);
        String p1 = body.getOrDefault("paragraph1", existing.getParagraph1());
        String p2 = body.getOrDefault("paragraph2", existing.getParagraph2());
        String p3 = body.getOrDefault("paragraph3", existing.getParagraph3());

        boolean changed = !java.util.Objects.equals(p1, existing.getParagraph1())
                || !java.util.Objects.equals(p2, existing.getParagraph2())
                || !java.util.Objects.equals(p3, existing.getParagraph3());

        api.summaries.updateTexts(id, p1, p2, p3);
        String editor = ApiContext.session(ctx).name();
        if (changed) {
            api.training.recordSummaryCorrection(existing, p1, p2, p3, editor, api.uploadDir, api.mapper);
            api.audit.info(editor, "AI_CORRECTION_CAPTURED",
                    "Practitioner edit on summary " + id + " harvested into retraining dataset ("
                            + za.co.mongala.legal.backend.service.TrainingService.CORRECTIONS_DATASET_ID + ")");
        } else {
            api.audit.info(editor, "SUMMARY_EDITED", "Draft summary saved for " + id);
        }
        ctx.json(api.summaries.findById(id));
    }

    public void approve(Context ctx) {
        ApiContext.require(ctx, "write:case");
        String id = ctx.pathParam("id");
        Summary s = api.summaries.findById(id);
        if (s == null) { api.notFound(ctx, "Summary"); return; }
        if (s.getStatus() == Summary.SummaryStatus.APPROVED) {
            ctx.status(409).json(Map.of("error", "Summary already approved"));
            return;
        }
        api.summaries.approve(id, LocalDateTime.now());

        // notify the client through the portal (FR5 / US3)
        api.messages.insert(new ClientMessage(
                "m" + UUID.randomUUID().toString().substring(0, 10),
                s.getCaseId(),
                ApiContext.session(ctx).name(),
                "Your case update - summary approved",
                "Your updated case summary has been approved and is now available in your portal.",
                LocalDateTime.now(), true, false, true));

        api.audit.success(ApiContext.session(ctx).name(), "SUMMARY_APPROVED",
                "Summary " + id + " approved and published to the client portal");
        ctx.json(api.summaries.findById(id));
    }
}
