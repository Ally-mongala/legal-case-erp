package za.co.mongala.legal.backend.api;

import io.javalin.http.Context;
import za.co.mongala.legal.backend.service.ReminderService;

import java.util.Map;

/**
 *   GET  /api/reminders        recent reminders (read:case)
 *   POST /api/reminders/run    queue due reminders now and send them if SMTP is set (write:case)
 */
public class ReminderController {

    private final ApiContext api;

    public ReminderController(ApiContext api) {
        this.api = api;
    }

    public void list(Context ctx) {
        ApiContext.require(ctx, "read:case");
        ctx.json(Map.of(
                "reminders", api.reminders.recent(100),
                "mailConfigured", api.reminders.mailConfigured()));
    }

    public void runNow(Context ctx) {
        ApiContext.require(ctx, "write:case");
        ReminderService.RunResult r = api.reminders.runOnce();
        ctx.json(Map.of("queued", r.queued(), "sent", r.sent(), "failed", r.failed(),
                "waiting", r.waiting(), "mailConfigured", r.mailConfigured()));
    }
}
