package za.co.mongala.legal.client;

import za.co.mongala.legal.model.ActivityLog;
import za.co.mongala.legal.service.IAuditService;

import java.util.Map;

/**
 * CONNECTED-mode audit: posts client-side actions to the backend's audit log
 * (FR10), where they sit alongside server-side events.
 */
public class RemoteAuditService implements IAuditService {

    private final ApiClient api;

    public RemoteAuditService(ApiClient api) {
        this.api = api;
    }

    @Override
    public void log(String userName, String action, String details, ActivityLog.Level level) {
        try {
            api.post("/api/audit", Map.of("action", action, "details", details == null ? "" : details,
                    "level", level.name()), Map.class);
        } catch (Exception ignored) {
            // audit must never crash the UI
        }
    }

    @Override
    public void info(String u, String a, String d) { log(u, a, d, ActivityLog.Level.INFO); }
    @Override
    public void success(String u, String a, String d) { log(u, a, d, ActivityLog.Level.SUCCESS); }
    @Override
    public void warning(String u, String a, String d) { log(u, a, d, ActivityLog.Level.WARNING); }
}
