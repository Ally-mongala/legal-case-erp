package za.co.mongala.legal.ui.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import za.co.mongala.legal.MainApp;
import za.co.mongala.legal.model.Case;
import za.co.mongala.legal.model.Role;
import za.co.mongala.legal.model.User;
import za.co.mongala.legal.ui.AppContext;
import za.co.mongala.legal.ui.Toast;

import java.util.Timer;
import java.util.TimerTask;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * The workspace shell: a persistent header (user + role badge + logout),
 * a role-aware navigation rail, and a content area into which individual
 * views are loaded.
 *
 * Navigation is role-based (NFR1): lawyers see case management screens,
 * clients see their portal. View loading is handled by a small registry so
 * views can also open other views (e.g. clicking a case opens case detail).
 */
public class ShellController {

    /** View keys registered here must match FXML files under /fxml/views/. */
    private static final Map<String, String> VIEW_FXML = new LinkedHashMap<>();

    static {
        // lawyer workspace
        VIEW_FXML.put("dashboard",        "/fxml/views/dashboard.fxml");
        VIEW_FXML.put("cases",            "/fxml/views/cases.fxml");
        VIEW_FXML.put("case_detail",      "/fxml/views/case_detail.fxml");
        VIEW_FXML.put("calendar",         "/fxml/views/calendar.fxml");
        VIEW_FXML.put("activity",         "/fxml/views/activity.fxml");
        VIEW_FXML.put("ratings",          "/fxml/views/lawyer_ratings.fxml");
        // client portal
        VIEW_FXML.put("client_home",      "/fxml/views/client_home.fxml");
        VIEW_FXML.put("client_documents", "/fxml/views/client_documents.fxml");
        VIEW_FXML.put("client_messages",  "/fxml/views/client_messages.fxml");
        VIEW_FXML.put("client_ratings",   "/fxml/views/client_ratings.fxml");
        // admin portal
        VIEW_FXML.put("admin_users",      "/fxml/views/admin_users.fxml");
        VIEW_FXML.put("admin_audit",      "/fxml/views/admin_audit.fxml");
        VIEW_FXML.put("admin_system",     "/fxml/views/admin_system.fxml");
    }

    private final AppContext ctx;

    @FXML private Label userNameLabel;
    @FXML private Label alertLabel;
    @FXML private Label roleBadge;
    @FXML private Button logoutButton;
    @FXML private VBox navList;
    @FXML private StackPane contentPane;
    @FXML private Label statusBarLabel;

    private User user;
    private final Map<String, Button> navButtons = new LinkedHashMap<>();

    public ShellController(AppContext ctx) {
        this.ctx = ctx;
    }

    /** Called by MainApp after the shell FXML is loaded. */
    public void init(User user) {
        this.user = user;
        ctx.setToastPane(contentPane);

        userNameLabel.setText(user.getName());
        roleBadge.setText(user.getRole().getDisplayName().toUpperCase());
        roleBadge.getStyleClass().add(roleStyleClass(user.getRole()));

        buildNavigation(user.getRole());
        logoutButton.setOnAction(e -> ctx.app().logout());

        // Status bar: show the connection mode so reviewers can tell where data comes from
        statusBarLabel.setText(ctx.isRemote()
                ? "LegalCase ERP \u00B7 ISJ107V Assignment 2 \u00B7 CONNECTED to backend: " + ctx.api().baseUrl()
                : "LegalCase ERP \u00B7 ISJ107V Assignment 2 \u00B7 DEMO mode (in-memory data)");

        startAlertPoller(user);

        // Open the first view of the role
        String first = user.getRole() == Role.CLIENT ? "client_home"
                : user.getRole() == Role.ADMIN ? "admin_users" : "dashboard";
        showView(first, null);
    }

    /** Polls for new messages every 8 seconds and updates the alert icon (FR: alert for new message). */
    private void startAlertPoller(User user) {
        if (user.getRole() == Role.ADMIN) return;
        alertLabel.setCursor(javafx.scene.Cursor.HAND);
        alertLabel.setVisible(false);
        alertLabel.setOnMouseClicked(e -> {
            if (user.getRole() == Role.CLIENT) {
                showView("client_messages", null);
            } else {
                // lawyer: jump to the first case with an unread client message
                var store = ctx.dataStore();
                Case target = store.getAllCases().stream()
                        .filter(c -> store.messagesForCase(c.getId()).stream()
                                .anyMatch(m -> !m.isFromLawyer() && !m.isReadByLawyer()))
                        .findFirst().orElse(null);
                showView(target != null ? "case_detail" : "dashboard", target);
            }
        });
        new Timer(true).scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                try {
                    long count = user.getRole() == Role.CLIENT
                            ? ctx.dataStore().unreadCountForClient(user.getId())
                            : ctx.dataStore().lawyerUnreadCount();
                    javafx.application.Platform.runLater(() -> {
                        if (count > 0) {
                            alertLabel.setText("\uD83D\uDCE8 " + count);
                            alertLabel.getStyleClass().setAll("badge", "badge-alert");
                            alertLabel.setVisible(true);
                        } else {
                            alertLabel.setVisible(false);
                        }
                    });
                } catch (Exception ignored) {}
            }
        }, 0, 3000);
    }

    private String roleStyleClass(Role role) {
        return switch (role) {
            case LAWYER -> "badge-lawyer";
            case CLIENT -> "badge-client";
            case ADMIN -> "badge-admin";
        };
    }

    private void buildNavigation(Role role) {
        navList.getChildren().clear();
        navButtons.clear();

        if (role == Role.CLIENT) {
            addNavItem("client_home", "My Case");
            addNavItem("client_documents", "Documents");
            addNavItem("client_messages", "Messages");
            addNavItem("client_ratings", "Rate Lawyer");
        } else if (role == Role.ADMIN) {
            addNavItem("admin_users", "Users & Roles");
            addNavItem("admin_audit", "Audits & Reports");
            addNavItem("admin_system", "System & Model");
        } else {
            addNavItem("dashboard", "Dashboard");
            addNavItem("cases", "Cases");
            addNavItem("calendar", "Calendar Sync");
            addNavItem("ratings", "Client Ratings");
            addNavItem("activity", "Activity & Audit");
        }
    }

    private void addNavItem(String key, String label) {
        Button b = new Button(label);
        b.getStyleClass().add("nav-item");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setOnAction(e -> showView(key, null));
        navButtons.put(key, b);
        navList.getChildren().add(b);
    }

    /* ------------------------------------------------------------------
     * View switching
     * ------------------------------------------------------------------ */

    public void showView(String key, Object param) {
        String fxml = VIEW_FXML.get(key);
        if (fxml == null) {
            Toast.error(ctx, "Unknown view: " + key);
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxml));
            loader.setControllerFactory(c -> createController(key, param));
            Parent view = loader.load();
            contentPane.getChildren().setAll(view);

            if (navButtons.containsKey(key)) {
                navButtons.forEach((k, btn) -> btn.getStyleClass().remove("nav-item-selected"));
                navButtons.get(key).getStyleClass().add("nav-item-selected");
            }
        } catch (IOException e) {
            Toast.error(ctx, "Failed to open view: " + key);
            e.printStackTrace();
        }
    }

    /** Factory for the controller of each view - services arrive via the context. */
    private Object createController(String key, Object param) {
        return switch (key) {
            case "dashboard" -> new LawyerDashboardController(ctx);
            case "cases" -> new CasesController(ctx, this::openCase);
            case "case_detail" -> new CaseDetailController(ctx, (Case) param);
            case "calendar" -> new CalendarController(ctx);
            case "activity" -> new ActivityController(ctx);
            case "ratings" -> new LawyerRatingsController(ctx);
            case "client_home" -> new ClientHomeController(ctx);
            case "client_documents" -> new ClientDocumentsController(ctx);
            case "client_messages" -> new ClientMessagesController(ctx);
            case "client_ratings" -> new ClientRatingsController(ctx);
            case "admin_users" -> new AdminUsersController(ctx);
            case "admin_audit" -> new AdminAuditController(ctx);
            case "admin_system" -> new AdminSystemController(ctx);
            default -> throw new IllegalArgumentException("No controller for view " + key);
        };
    }

    private void openCase(Case c) {
        showView("case_detail", c);
    }
}
