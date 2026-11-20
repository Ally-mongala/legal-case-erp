package za.co.mongala.legal.ui;

import javafx.scene.layout.StackPane;
import za.co.mongala.legal.MainApp;
import za.co.mongala.legal.client.ApiClient;
import za.co.mongala.legal.model.User;
import za.co.mongala.legal.service.*;

/**
 * Shared application context handed to every controller. Bundles the service
 * layer (either the in-memory DEMO services or the CONNECTED-mode REST clients),
 * the current user and the document workflow.
 */
public class AppContext {

    private final MainApp app;
    private final boolean remote;
    private final ApiClient api;
    private final IAuthService authService;
    private final IDataStore dataStore;
    private final ICalendarSyncService calendarSyncService;
    private final IAuditService auditService;
    private final DocumentWorkflow workflow;

    private User currentUser;
    private StackPane toastPane;   // set by the shell; used to show transient notifications

    public AppContext(MainApp app, ApiClient api, IAuthService auth, IDataStore data,
                      ICalendarSyncService calendar, IAuditService audit, DocumentWorkflow workflow) {
        this.app = app;
        this.api = api;
        this.remote = api != null;
        this.authService = auth;
        this.dataStore = data;
        this.calendarSyncService = calendar;
        this.auditService = audit;
        this.workflow = workflow;
    }

    public MainApp app() { return app; }
    public boolean isRemote() { return remote; }
    public ApiClient api() { return api; }
    public IAuthService authService() { return authService; }
    public IDataStore dataStore() { return dataStore; }
    public ICalendarSyncService calendarSyncService() { return calendarSyncService; }
    public IAuditService auditService() { return auditService; }
    public DocumentWorkflow workflow() { return workflow; }

    public User currentUser() { return currentUser; }
    public void setCurrentUser(User u) { this.currentUser = u; }

    public StackPane toastPane() { return toastPane; }
    public void setToastPane(StackPane pane) { this.toastPane = pane; }
}
