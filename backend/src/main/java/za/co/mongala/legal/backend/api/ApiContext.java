package za.co.mongala.legal.backend.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.UnauthorizedResponse;
import za.co.mongala.legal.backend.auth.JwtUtil;
import za.co.mongala.legal.backend.config.AppConfig;
import za.co.mongala.legal.backend.security.EncryptionUtil;
import za.co.mongala.legal.backend.service.*;

import java.nio.file.Path;

/**
 * Bundles every backend service so controllers stay thin.
 */
public class ApiContext {

    public final AppConfig cfg;
    public final JwtUtil jwt;
    public final UserService users;
    public final CaseService cases;
    public final DocumentService documents;
    public final ExtractionService extractions;
    public final SummaryService summaries;
    public final DeadlineService deadlines;
    public final MessageService messages;
    public final AuditService audit;
    public final CalendarSyncService calendarSync;
    public final AiPipeline pipeline;
    public final RatingService ratings;
    public final TrainingService training;
    public final MetricsService metrics;
    public final EncryptionUtil encryption;
    public final Path uploadDir;
    public final ObjectMapper mapper;
    public final ConflictCheckService conflicts;
    public final BillingService billing;
    public final BackupService backups;
    public final za.co.mongala.legal.backend.auth.LoginGuard loginGuard;
    public final CaseIntelligenceService intelligence;
    public final LegalTemplateService templates;
    public final CourtBundleService courtBundle;
    public final TrustAccountingService trust;
    public final PrecedentSearchService precedentSearch;
    public final ExcelWorkbookService excelWorkbook;
    public final ReminderService reminders;

    public ApiContext(AppConfig cfg, JwtUtil jwt, UserService users, CaseService cases,
                      DocumentService documents, ExtractionService extractions,
                      SummaryService summaries, DeadlineService deadlines,
                      MessageService messages, AuditService audit,
                      CalendarSyncService calendarSync, AiPipeline pipeline,
                      RatingService ratings, TrainingService training, MetricsService metrics,
                      EncryptionUtil encryption, Path uploadDir,
                      javax.sql.DataSource dataSource,
                      ConflictCheckService conflicts, BillingService billing) {
        this.cfg = cfg;
        this.jwt = jwt;
        this.users = users;
        this.cases = cases;
        this.documents = documents;
        this.extractions = extractions;
        this.summaries = summaries;
        this.deadlines = deadlines;
        this.messages = messages;
        this.audit = audit;
        this.calendarSync = calendarSync;
        this.pipeline = pipeline;
        this.ratings = ratings;
        this.training = training;
        this.metrics = metrics;
        this.encryption = encryption;
        this.uploadDir = uploadDir;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.conflicts = conflicts;
        this.billing = billing;
        this.backups = new BackupService(dataSource, encryption, uploadDir, this.mapper);
        this.loginGuard = new za.co.mongala.legal.backend.auth.LoginGuard();
        this.intelligence = new CaseIntelligenceService(cases, documents, extractions, deadlines);
        this.templates = new LegalTemplateService();
        this.courtBundle = new CourtBundleService(documents, encryption, uploadDir);
        this.trust = new TrustAccountingService(dataSource);
        this.precedentSearch = new PrecedentSearchService(dataSource);
        this.excelWorkbook = new ExcelWorkbookService();
        this.reminders = new ReminderService(dataSource, users, cases, deadlines,
                MailSender.fromEnv(), audit, System.getenv("LCE_FIRM_EMAIL"));
    }

    /* ---------------- auth helpers ---------------- */

    /** Reads the JWT session set by the auth filter. */
    public static JwtUtil.Session session(Context ctx) {
        JwtUtil.Session s = ctx.attribute("session");
        if (s == null) throw new UnauthorizedResponse("Missing or invalid token");
        return s;
    }

    public static JwtUtil.Session require(Context ctx, String scope) {
        JwtUtil.Session s = session(ctx);
        if (!s.has(scope)) throw new ForbiddenResponse("Insufficient scope: " + scope);
        return s;
    }

    /** Clients may only access their own matters (role-based access, NFR1). */
    public static void requireClientOwner(Context ctx, String caseId, ApiContext api) {
        JwtUtil.Session s = session(ctx);
        if (s.role() == za.co.mongala.legal.model.Role.CLIENT) {
            var c = api.cases.findById(caseId);
            if (c == null) throw new UnauthorizedResponse("Case not found");
            if (!c.getClientId().equals(s.userId())) {
                throw new ForbiddenResponse("This case belongs to another client");
            }
        }
    }

    /**
     * Read access to one case: lawyers/admins need read:case, a client is
     * allowed only when the case is their own (read:client + owner check).
     */
    public static void requireCaseRead(Context ctx, String caseId, ApiContext api) {
        JwtUtil.Session s = session(ctx);
        if (s.has("read:case")) return;
        if (s.has("read:client")) {
            requireClientOwner(ctx, caseId, api);
            return;
        }
        throw new ForbiddenResponse("Insufficient scope");
    }

    public void notFound(Context ctx, String what) {
        ctx.status(404).json(java.util.Map.of("error", what + " not found"));
    }
}
