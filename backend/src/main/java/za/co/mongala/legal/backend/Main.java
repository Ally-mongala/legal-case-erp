package za.co.mongala.legal.backend;

import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import io.javalin.http.UnauthorizedResponse;
import za.co.mongala.legal.backend.api.*;
import za.co.mongala.legal.backend.auth.JwtUtil;
import za.co.mongala.legal.backend.calendar.CalendarGateway;
import za.co.mongala.legal.backend.calendar.GoogleCalendarGateway;
import za.co.mongala.legal.backend.calendar.LocalCalendarGateway;
import za.co.mongala.legal.backend.config.AppConfig;
import za.co.mongala.legal.backend.security.EncryptionUtil;
import za.co.mongala.legal.backend.db.Database;
import za.co.mongala.legal.backend.seed.SeedData;
import za.co.mongala.legal.backend.service.*;

/**
 * LegalCase ERP backend - entry point.
 *
 * Start with:   mvn -pl backend exec:java
 * or the fat jar:  java -jar backend/target/legal-backend.jar
 * Configure via environment variables (see AppConfig).
 */
public class Main {

    public static void main(String[] args) {
        AppConfig cfg = AppConfig.fromEnv();
        Database db = new Database(cfg);

        // services
        UserService users = new UserService(db.dataSource());
        CaseService cases = new CaseService(db.dataSource());
        DocumentService documents = new DocumentService(db.dataSource());
        ExtractionService extractions = new ExtractionService(db.dataSource());
        SummaryService summaries = new SummaryService(db.dataSource());
        DeadlineService deadlines = new DeadlineService(db.dataSource());
        MessageService messages = new MessageService(db.dataSource());
        AuditService audit = new AuditService(db.dataSource());
        DeadlineExtractor deadlineExtractor = new DeadlineExtractor(deadlines, cases);

        CalendarGateway gateway = cfg.googleConfigured()
                ? new GoogleCalendarGateway(cfg)
                : new LocalCalendarGateway(db.uploadDir());

        // A half-configured Google account is a common mistake - say so loudly at start-up
        // instead of silently falling back to the local .ics calendar.
        if (cfg.googlePartiallyConfigured()) {
            System.err.println("[WARN] Google Calendar is partly configured - missing: "
                    + String.join(", ", cfg.missingGoogleVariables())
                    + ". Falling back to the local .ics calendar. "
                    + "See docs/GOOGLE_CALENDAR_SETUP.md");
        }

        EncryptionUtil encryption = new EncryptionUtil(cfg.encryptionKey());

        AiPipeline pipeline = new AiPipeline(extractions, summaries, deadlineExtractor,
                documents, cases, audit, db.uploadDir(), encryption);
        CalendarSyncService calendarSync = new CalendarSyncService(db.dataSource(), deadlines,
                cases, audit, gateway, db.uploadDir());

        RatingService ratings = new RatingService(db.dataSource());
        TrainingService training = new TrainingService(db.dataSource());
        MetricsService metricsService = new MetricsService(db.dataSource());
        ConflictCheckService conflicts = new ConflictCheckService(db.dataSource());
        BillingService billing = new BillingService(db.dataSource());

        // seed demo data on first run
        new SeedData(db.dataSource(), db.uploadDir(), users, cases, deadlines, messages, audit, documents, extractions,
                summaries, deadlineExtractor, ratings, training).seedIfEmpty();

        ApiContext api = new ApiContext(cfg, new JwtUtil(cfg.jwtSecret(), cfg.tokenHours()),
                users, cases, documents, extractions, summaries, deadlines, messages,
                audit, calendarSync, pipeline, ratings, training, metricsService,
                encryption, db.uploadDir(), db.dataSource(), conflicts, billing);

        // hourly reminder job: queues reminders for deadlines inside their window and sends them if SMTP is set
        java.util.concurrent.ScheduledExecutorService reminderTimer =
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "reminder-timer");
                    t.setDaemon(true);
                    return t;
                });
        reminderTimer.scheduleAtFixedRate(() -> {
            try {
                api.reminders.runOnce();
            } catch (Exception e) {
                System.err.println("[WARN] Reminder run failed: " + e.getMessage());
            }
        }, 1, 60, java.util.concurrent.TimeUnit.MINUTES);

        Javalin app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.jsonMapper(new JavalinJackson(api.mapper, true));
            // allow PDF uploads up to 25 MB
            config.jetty.modifyServletContextHandler(h -> h.setMaxFormContentSize(25 * 1024 * 1024));
            // serve the built-in browser client at http://localhost:8080/
            config.staticFiles.add(st -> {
                st.directory = "/web";
                st.hostedPath = "/";
            });
        });

        registerSecurityHeaders(app);
        registerAuthFilter(app, api);
        registerRoutes(app, api);

        app.exception(Exception.class, (e, ctx) -> {
            if (ctx.status().getCode() < 400) ctx.status(500);
            var msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            if (ctx.status().getCode() >= 500) {
                System.err.println("[ERROR] " + e);
            }
            ctx.json(java.util.Map.of("error", msg));
        });

        app.start(cfg.port());
        audit.success("System", "SYSTEM_START",
                "Backend online on port " + cfg.port() + " - calendar: " + gateway.description()
                        + (cfg.googleConfigured() ? "" : " (set LCE_GOOGLE_* env vars to enable real Google Calendar API)"));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            audit.info("System", "SYSTEM_STOP", "Backend shutting down");
            reminderTimer.shutdownNow();
            app.stop();
            db.close();
        }));
    }

    /* ---------------- OWASP security headers & auth filter ---------------- */

    private static void registerSecurityHeaders(Javalin app) {
        app.before(ctx -> {
            ctx.header("X-Content-Type-Options", "nosniff");
            ctx.header("Referrer-Policy", "strict-origin-when-cross-origin");
            ctx.header("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
            ctx.header("Content-Security-Policy",
                    "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'");
            if (ctx.path().startsWith("/api/") && !ctx.path().equals("/api/events")) {
                ctx.header("Cache-Control", "no-store");
            }
        });
    }

    private static void registerAuthFilter(Javalin app, ApiContext api) {
        app.before("/api/*", ctx -> {
            String path = ctx.path();
            if (path.equals("/api/auth/login") || path.equals("/api/auth/demo")
                    || path.equals("/api/health")) {
                return;
            }
            String auth = ctx.header("Authorization");
            if (auth == null || !auth.startsWith("Bearer ")) {
                throw new UnauthorizedResponse("Missing bearer token");
            }
            JwtUtil.Session session = api.jwt.parse(auth.substring(7));
            if (session == null) {
                throw new UnauthorizedResponse("Invalid or expired token");
            }
            // FR8: a revoked account loses access immediately, even with a valid token
            if (api.users.isDisabled(session.email())) {
                throw new UnauthorizedResponse("Account access has been revoked by an administrator");
            }
            ctx.attribute("session", session);
        });
    }

    /* ---------------- routes (Assignment 2 section 11) ---------------- */

    private static void registerRoutes(Javalin app, ApiContext api) {
        AuthController auth = new AuthController(api);
        CaseController caseCtl = new CaseController(api);
        DocumentController docCtl = new DocumentController(api);
        SummaryController sumCtl = new SummaryController(api);
        CalendarController calCtl = new CalendarController(api);
        ActivityController actCtl = new ActivityController(api);
        ClientPortalController portal = new ClientPortalController(api);
        RatingController ratingCtl = new RatingController(api);
        AdminController adminCtl = new AdminController(api);
        BillingController billingCtl = new BillingController(api);
        ChatController chatCtl = new ChatController(api);
        CaseFileController caseFileCtl = new CaseFileController(api);
        ReminderController reminderCtl = new ReminderController(api);

        // health
        app.get("/api/health", ctx -> ctx.json(java.util.Map.of("status", "ok")));

        // Server-Sent Events: real-time push so the unread bell updates instantly.
        // The browser connects with its JWT; the backend broadcasts "new-message"
        // the moment any message is inserted.
        app.get("/api/events", new io.javalin.http.sse.SseHandler(sse -> {
            ApiContext.require(sse.ctx(), "read:client");
            za.co.mongala.legal.backend.service.EventBus.subscribe(sse);
            sse.sendEvent("hello", "connected");
            // Keep the request open (classic SSE pattern): the connection stays
            // alive until the client disconnects, at which point onClose removes
            // the subscriber from the EventBus.
            var latch = new java.util.concurrent.CountDownLatch(1);
            sse.onClose(() -> latch.countDown());
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));

        // matter types for the case-creation drop-down (lawyer)
        app.get("/api/matter-types", ctx -> ctx.json(za.co.mongala.legal.model.MatterTypes.LIST));

        // Serve the built-in browser client at the root - explicit routes so GET / always works
        app.get("/", ctx -> serveWebPage(ctx, "index.html"));
        app.get("/index.html", ctx -> serveWebPage(ctx, "index.html"));

        // auth
        app.post("/api/auth/login", auth::login);
        app.post("/api/auth/demo", auth::demoLogin);
        app.get("/api/me", auth::me);

        // cases + conflict-of-interest + intelligence + templates + court bundle + precedents
        app.get("/api/conflicts/check", caseCtl::checkConflicts);
        app.get("/api/search/precedents", caseCtl::searchPrecedents);
        app.get("/api/cases", caseCtl::list);
        app.post("/api/cases", caseCtl::create);
        app.get("/api/cases/{id}", caseCtl::get);
        app.get("/api/cases/{id}/documents", caseCtl::documents);
        app.get("/api/cases/{id}/intelligence", caseCtl::caseIntelligence);
        app.get("/api/cases/{id}/templates/{templateType}", caseCtl::downloadTemplate);
        app.get("/api/cases/{id}/court-bundle.pdf", caseCtl::downloadCourtBundle);
        app.patch("/api/cases/{id}", caseCtl::update);
        app.patch("/api/cases/{id}/status", caseCtl::updateStatus);
        app.delete("/api/cases/{id}", caseCtl::delete);

        // help chat robot (rule-based, any signed-in user)
        app.post("/api/chat", chatCtl::ask);

        // one-page case file PDF and email reminders
        app.get("/api/cases/{id}/case-file.pdf", caseFileCtl::caseFile);
        app.get("/api/reminders", reminderCtl::list);
        app.post("/api/reminders/run", reminderCtl::runNow);

        // billing, PDF fee notes, Section 86 client trust ledger & multi-sheet Excel ERP workbook
        app.get("/api/billing/overview", billingCtl::firmOverview);
        app.get("/api/reports/erp-workbook.xlsx", billingCtl::downloadErpWorkbookXlsx);
        app.get("/api/cases/{id}/billing", billingCtl::statement);
        app.post("/api/cases/{id}/billing", billingCtl::addEntry);
        app.post("/api/cases/{id}/billing/settle-from-trust", billingCtl::settleFromTrust);
        app.post("/api/cases/{id}/billing/record-payment", billingCtl::recordDirectPayment);
        app.delete("/api/billing/{entryId}", billingCtl::deleteEntry);
        app.get("/api/cases/{id}/invoice.pdf", billingCtl::downloadInvoicePdf);
        app.get("/api/cases/{id}/trust", billingCtl::trustStatement);
        app.post("/api/cases/{id}/trust", billingCtl::addTrustTransaction);
        app.get("/api/cases/{id}/trust-statement.pdf", billingCtl::downloadTrustStatementPdf);

        // documents / AI
        app.post("/api/cases/{id}/documents", docCtl::upload);
        app.get("/api/documents/{id}", docCtl::status);
        app.get("/api/documents/{id}/extractions", docCtl::extractions);
        app.post("/api/documents/{id}/bill-review", billingCtl::billAiDocumentReview);
        app.post("/api/documents/{id}/retry", docCtl::retry);
        app.get("/api/documents/{id}/download", docCtl::download);
        app.get("/api/documents/{id}/content", docCtl::content);
        app.delete("/api/documents/{id}", docCtl::delete);
        app.get("/api/extractions", docCtl::allExtractions);

        // summaries
        app.get("/api/cases/{id}/summaries", sumCtl::byCase);
        app.get("/api/documents/{id}/summaries", sumCtl::byDocument);
        app.put("/api/summaries/{id}", sumCtl::update);
        app.post("/api/summaries/{id}/approve", sumCtl::approve);

        // calendar
        app.get("/api/deadlines", calCtl::allDeadlines);
        app.get("/api/calendar/export.ics", calCtl::exportIcs);
        app.get("/api/cases/{id}/deadlines", calCtl::caseDeadlines);
        app.post("/api/calendar/sync", calCtl::sync);
        app.post("/api/calendar/sync-all", calCtl::syncAll);
        app.post("/api/calendar/unsync", calCtl::unsync);
        app.post("/api/cases/{id}/calendar-sync", calCtl::syncCase);
        app.post("/api/cases/{id}/calendar-unsync", calCtl::unsyncCase);
        app.get("/api/cases/{id}/calendar.ics", calCtl::caseIcs);
        app.get("/api/calendar/status", calCtl::status);
        app.post("/api/calendar/test-connection", calCtl::testConnection);

        // audit
        app.get("/api/activity", actCtl::list);
        app.get("/api/activity.csv", actCtl::downloadCsv);
        app.post("/api/audit", actCtl::record);

        // ratings (FR7)
        app.post("/api/ratings", ratingCtl::submit);
        app.get("/api/ratings", ratingCtl::list);
        app.get("/api/ratings/average", ratingCtl::average);

        // admin portal (FR8/FR9/FR10)
        app.get("/api/admin/users", adminCtl::listUsers);
        app.post("/api/admin/users", adminCtl::createUser);
        app.post("/api/admin/users/{id}/revoke", adminCtl::revoke);
        app.post("/api/admin/users/{id}/restore", adminCtl::restore);
        app.get("/api/admin/locked-accounts", adminCtl::lockedAccounts);
        app.post("/api/admin/users/{id}/unlock", adminCtl::unlockUser);
        app.get("/api/admin/reports/audit.csv", adminCtl::auditCsv);
        app.get("/api/admin/reports/cases.csv", adminCtl::casesCsv);
        app.get("/api/admin/metrics", adminCtl::metrics);
        app.get("/api/admin/training-runs", adminCtl::trainingRuns);
        app.post("/api/admin/training-runs/{id}/approve", adminCtl::approveTrainingRun);
        app.get("/api/admin/datasets", adminCtl::datasets);
        app.get("/api/admin/rejections", adminCtl::rejections);
        app.get("/api/admin/reports/rejections.csv", adminCtl::rejectionsCsv);
        app.post("/api/admin/datasets", adminCtl::uploadDataset);
        app.get("/api/admin/datasets/{id}/download", adminCtl::downloadDataset);
        app.get("/api/admin/reports/system.csv", adminCtl::systemReport);
        app.get("/api/admin/audit/verify", adminCtl::verifyAuditChain);
        app.get("/api/admin/backups", adminCtl::listBackups);
        app.post("/api/admin/backups", adminCtl::createBackup);
        app.get("/api/admin/backups/{filename}/download", adminCtl::downloadBackup);
        app.get("/api/admin/corrections", adminCtl::listCorrections);

        // manual deadline (FR4)
        app.post("/api/cases/{id}/deadlines", calCtl::add);

        // client portal
        app.get("/api/client/cases", portal::myCases);
        app.get("/api/me/unread", portal::unreadCount);
        app.get("/api/me/unread-lawyer", portal::lawyerUnread);
        app.post("/api/cases/{id}/messages/read-by-lawyer", portal::markReadByLawyer);
        app.post("/api/cases/{id}/messages/lawyer", portal::lawyerSendMessage);
        app.get("/api/cases/{id}/client-view", portal::clientView);
        app.get("/api/cases/{id}/messages", portal::messages);
        app.post("/api/cases/{id}/messages/read", portal::markReadByClient);
        app.post("/api/cases/{id}/messages", portal::sendMessage);
        app.get("/api/documents", portal::sharedDocuments);
    }

    /** Serves a web resource from /web with the right content type. */
    private static void serveWebPage(io.javalin.http.Context ctx, String name) {
        try (java.io.InputStream in = Main.class.getResourceAsStream("/web/" + name)) {
            if (in == null) {
                ctx.status(404).result("Web client not found - rebuild the backend (run-backend.sh) and try again.");
                return;
            }
            ctx.contentType("text/html; charset=utf-8");
            ctx.result(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            ctx.status(500).result("Could not load web client: " + e.getMessage());
        }
    }
}
