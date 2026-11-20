package za.co.mongala.legal.backend.api;

import io.javalin.http.Context;
import za.co.mongala.legal.model.ActivityLog;

import java.util.Map;

/**
 * Audit endpoints (FR10):
 *   GET  /api/activity - tamper-evident audit trail
 *   POST /api/audit    - record a client-side action
 */
public class ActivityController {

    private final ApiContext api;

    public ActivityController(ApiContext api) {
        this.api = api;
    }

    public void list(Context ctx) {
        ApiContext.require(ctx, "read:audit");
        int limit = Math.min(500, Math.max(1, ctx.queryParamAsClass("limit", Integer.class).getOrDefault(200)));
        ctx.json(api.audit.recent(limit));
    }

    /** Download the audit history as CSV (lawyers + admins - read:audit). */
    public void downloadCsv(Context ctx) {
        ApiContext.require(ctx, "read:audit");
        ctx.contentType("text/csv");
        ctx.header("Content-Disposition", "attachment; filename=activity-audit.csv");
        ctx.result(za.co.mongala.legal.backend.service.ReportService.auditCsv(api.audit.all()));
    }

    public void record(Context ctx) {
        ApiContext.require(ctx, "write:audit");
        Map<String, String> body = ctx.bodyAsClass(Map.class);
        String level = body.getOrDefault("level", "INFO").toUpperCase();
        ActivityLog.Level lvl;
        try {
            lvl = ActivityLog.Level.valueOf(level);
        } catch (Exception e) {
            lvl = ActivityLog.Level.INFO;
        }
        api.audit.log(ApiContext.session(ctx).name(), body.getOrDefault("action", "CLIENT_ACTION"),
                body.getOrDefault("details", ""), lvl);
        ctx.status(201).json(Map.of("ok", true));
    }
}
