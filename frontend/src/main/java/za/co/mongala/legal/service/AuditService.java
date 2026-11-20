package za.co.mongala.legal.service;

import za.co.mongala.legal.model.ActivityLog;

import java.time.LocalDateTime;

/**
 * AuditService - DEMO mode: records audit entries in memory (FR10).
 * In CONNECTED mode the UI uses {@link za.co.mongala.legal.client.RemoteAuditService}
 * so entries are written to the backend's tamper-evident audit_log table.
 */
public class AuditService implements IAuditService {

    private final DataStore store;

    public AuditService(DataStore store) {
        this.store = store;
    }

    public void log(String userName, String action, String details, ActivityLog.Level level) {
        store.addActivity(new ActivityLog(LocalDateTime.now(), userName, action, details, level));
    }

    public void info(String userName, String action, String details) {
        log(userName, action, details, ActivityLog.Level.INFO);
    }

    public void success(String userName, String action, String details) {
        log(userName, action, details, ActivityLog.Level.SUCCESS);
    }

    public void warning(String userName, String action, String details) {
        log(userName, action, details, ActivityLog.Level.WARNING);
    }
}
