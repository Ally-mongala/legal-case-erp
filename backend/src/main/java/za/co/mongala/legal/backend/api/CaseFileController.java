package za.co.mongala.legal.backend.api;

import io.javalin.http.Context;
import za.co.mongala.legal.backend.service.BillingService;
import za.co.mongala.legal.backend.service.CaseFileService;
import za.co.mongala.legal.backend.service.TrustAccountingService;
import za.co.mongala.legal.model.Case;
import za.co.mongala.legal.model.Deadline;
import za.co.mongala.legal.model.Summary;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 *   GET /api/cases/{id}/case-file.pdf   one-page case file (summary, deadlines, billing)
 *
 * Read access follows requireCaseRead: lawyers and admins see any case, a client only their own.
 */
public class CaseFileController {

    private final ApiContext api;
    private final CaseFileService service = new CaseFileService();

    public CaseFileController(ApiContext api) {
        this.api = api;
    }

    public void caseFile(Context ctx) throws Exception {
        String caseId = ctx.pathParam("id");
        ApiContext.requireCaseRead(ctx, caseId, api);
        Case c = api.cases.findById(caseId);
        if (c == null) { api.notFound(ctx, "Case"); return; }

        Summary approved = api.summaries.byCase(caseId).stream()
                .filter(s -> s.getStatus() == Summary.SummaryStatus.APPROVED)
                .findFirst().orElse(null);
        List<Deadline> upcoming = api.deadlines.byCase(caseId).stream()
                .filter(d -> d.daysLeft() >= 0)
                .sorted(Comparator.comparing(Deadline::getDate))
                .toList();
        BillingService.CaseStatement st = api.billing.statementForCase(caseId);
        TrustAccountingService.TrustStatement tr = api.trust.statementForCase(caseId);

        byte[] pdf = service.render(c, approved, upcoming, st, tr, LocalDateTime.now());
        api.audit.info(ApiContext.session(ctx).name(), "CASE_FILE_EXPORT", "Case file exported for " + c.getReference());
        ctx.contentType("application/pdf")
                .header("Content-Disposition", "attachment; filename=\"CaseFile-" + c.getReference() + ".pdf\"")
                .result(pdf);
    }
}
