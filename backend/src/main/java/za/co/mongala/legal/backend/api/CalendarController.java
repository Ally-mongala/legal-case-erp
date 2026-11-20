package za.co.mongala.legal.backend.api;

import io.javalin.http.Context;
import za.co.mongala.legal.model.Deadline;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Calendar endpoints (FR4 / US5):
 *   GET  /api/deadlines           - the whole firm deadline book
 *   GET  /api/cases/{id}/deadlines
 *   POST /api/calendar/sync       - {deadlineIds: [...]}
 *   POST /api/calendar/sync-all   - push everything pending
 */
public class CalendarController {

    private final ApiContext api;

    public CalendarController(ApiContext api) {
        this.api = api;
    }

    public void allDeadlines(Context ctx) {
        ApiContext.require(ctx, "read:case");
        ctx.json(api.deadlines.all());
    }

    public void caseDeadlines(Context ctx) {
        String caseId = ctx.pathParam("id");
        ApiContext.requireCaseRead(ctx, caseId, api);
        if (api.cases.findById(caseId) == null) { api.notFound(ctx, "Case"); return; }
        ctx.json(api.deadlines.byCase(caseId));
    }

    /** FR4: manually capture a deadline (not only AI-extracted ones). */
    public void add(Context ctx) {
        ApiContext.require(ctx, "write:case");
        String caseId = ctx.pathParam("id");
        if (api.cases.findById(caseId) == null) { api.notFound(ctx, "Case"); return; }
        var body = ctx.bodyAsClass(Map.class);
        String title = (String) body.getOrDefault("title", "");
        String dateStr = (String) body.getOrDefault("date", "");
        if (title.isBlank() || dateStr.isBlank()) {
            ctx.status(400).json(Map.of("error", "title and date (yyyy-mm-dd) are required"));
            return;
        }
        java.time.LocalDate date;
        try {
            date = java.time.LocalDate.parse(dateStr);
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", "Invalid date format - use yyyy-mm-dd"));
            return;
        }
        Deadline.DeadlineType type;
        try {
            type = Deadline.DeadlineType.valueOf(String.valueOf(body.getOrDefault("type", "OTHER")).toUpperCase());
        } catch (Exception e) {
            type = Deadline.DeadlineType.OTHER;
        }
        int reminder;
        try {
            reminder = Integer.parseInt(String.valueOf(body.getOrDefault("reminderDays", 7)));
        } catch (Exception e) {
            reminder = 7;
        }
        Deadline d = new Deadline("dl" + UUID.randomUUID().toString().substring(0, 10),
                caseId, "", title, date, type, reminder, false);
        api.deadlines.insert(d);
        api.audit.info(ApiContext.session(ctx).name(), "DEADLINE_CREATED",
                "Manual deadline \"" + title + "\" (" + date + ") added to case " + caseId
                        + " - reminder " + reminder + " days before");
        ctx.status(201).json(d);
    }

    public void sync(Context ctx) {
        ApiContext.require(ctx, "write:case");
        Map<String, List<String>> body = ctx.bodyAsClass(Map.class);
        List<String> ids = body.getOrDefault("deadlineIds", List.of());
        if (ids.isEmpty()) {
            ctx.status(400).json(Map.of("error", "deadlineIds must not be empty"));
            return;
        }
        int ok = 0;
        for (String id : ids) {
            Deadline d = api.deadlines.findById(id);
            if (d != null && !d.isSynced()) {
                String r = api.calendarSync.syncOne(d);
                if (r.startsWith("Synced")) ok++;
            }
        }
        ctx.json(Map.of("synced", ok, "requested", ids.size(),
                "gateway", api.calendarSync.gatewayDescription(),
                "message", ok + " of " + ids.size() + " deadline(s) synced"));
    }

    public void syncAll(Context ctx) {
        ApiContext.require(ctx, "write:case");
        List<Deadline> pending = api.deadlines.pending();
        String receipt = api.calendarSync.syncAll(pending);
        ctx.json(Map.of("message", receipt, "gateway", api.calendarSync.gatewayDescription()));
    }

    /** Export the full calendar as an .ics file for the user's phone/computer calendar. */
    public void exportIcs(Context ctx) {
        ApiContext.require(ctx, "read:case");
        String ics = api.calendarSync.exportIcs();
        ctx.contentType("text/calendar");
        ctx.header("Content-Disposition", "attachment; filename=legal-case-erp-calendar.ics");
        ctx.result(ics);
    }

    /** Export a single case's synced deadlines as .ics (per-case calendar sync). */
    public void caseIcs(Context ctx) {
        String caseId = ctx.pathParam("id");
        ApiContext.requireCaseRead(ctx, caseId, api);
        if (api.cases.findById(caseId) == null) { api.notFound(ctx, "Case"); return; }
        String ics = api.calendarSync.exportCaseIcs(caseId);
        ctx.contentType("text/calendar");
        ctx.header("Content-Disposition", "attachment; filename=case-" + caseId + "-calendar.ics");
        ctx.result(ics);
    }

    /** Unsync selected deadlines (toggle sync off). */
    public void unsync(Context ctx) {
        ApiContext.require(ctx, "write:case");
        Map<String, List<String>> body = ctx.bodyAsClass(Map.class);
        List<String> ids = body.getOrDefault("deadlineIds", List.of());
        if (ids.isEmpty()) {
            ctx.status(400).json(Map.of("error", "deadlineIds must not be empty"));
            return;
        }
        int removed = 0;
        for (String id : ids) {
            Deadline d = api.deadlines.findById(id);
            if (d != null && d.isSynced()) {
                api.calendarSync.unsyncOne(d);
                removed++;
            }
        }
        ctx.json(Map.of("removed", removed, "requested", ids.size(),
                "message", removed + " deadline(s) removed from " + api.calendarSync.gatewayDescription()));
    }

    /** Sync every deadline on a case (synchronise a case). */
    public void syncCase(Context ctx) {
        ApiContext.require(ctx, "write:case");
        String caseId = ctx.pathParam("id");
        if (api.cases.findById(caseId) == null) { api.notFound(ctx, "Case"); return; }
        List<Deadline> ds = api.deadlines.byCase(caseId);
        String receipt = api.calendarSync.syncAll(ds);
        ctx.json(Map.of("message", receipt, "gateway", api.calendarSync.gatewayDescription()));
    }

    /** Unsync every deadline on a case (unsynchronise a case). */
    public void unsyncCase(Context ctx) {
        ApiContext.require(ctx, "write:case");
        String caseId = ctx.pathParam("id");
        if (api.cases.findById(caseId) == null) { api.notFound(ctx, "Case"); return; }
        List<Deadline> ds = api.deadlines.byCase(caseId);
        String receipt = api.calendarSync.unsyncAll(ds);
        ctx.json(Map.of("message", receipt, "gateway", api.calendarSync.gatewayDescription()));
    }

    /**
     * GET /api/calendar/status - which calendar backend is active (local .ics or Google),
     * whether it is configured, and which variables are still missing. No secrets.
     */
    public void status(Context ctx) {
        ApiContext.require(ctx, "read:case");
        var s = api.calendarSync.status();
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("gateway", s.gateway());
        out.put("remote", s.remote());
        out.put("configured", s.configured());
        out.put("calendarId", s.calendarId());
        out.put("detail", s.detail());
        out.put("missingVariables", s.missingVariables());
        out.put("description", api.calendarSync.gatewayDescription());
        ctx.json(out);
    }

    /**
     * POST /api/calendar/test-connection - proves the credentials work. Always answers
     * 200 with {ok:true|false, message}, because a refused credential is a result the UI
     * has to display, not a server fault.
     */
    public void testConnection(Context ctx) {
        ApiContext.require(ctx, "write:case");
        try {
            String message = api.calendarSync.testConnection();
            ctx.json(Map.of("ok", true, "message", message,
                    "gateway", api.calendarSync.gatewayDescription()));
        } catch (Exception e) {
            ctx.json(Map.of("ok", false, "message", String.valueOf(e.getMessage()),
                    "gateway", api.calendarSync.gatewayDescription()));
        }
    }
}
