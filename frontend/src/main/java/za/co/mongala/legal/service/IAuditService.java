package za.co.mongala.legal.service;

import za.co.mongala.legal.model.ActivityLog;

/**
 * Audit trail contract (FR10).
 * DEMO mode records locally; CONNECTED mode posts to /api/audit so entries
 * land in the backend's tamper-evident log alongside server-side events.
 */
public interface IAuditService {

    void log(String userName, String action, String details, ActivityLog.Level level);

    void info(String userName, String action, String details);

    void success(String userName, String action, String details);

    void warning(String userName, String action, String details);
}
