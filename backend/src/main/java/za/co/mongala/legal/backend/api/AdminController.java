package za.co.mongala.legal.backend.api;

import io.javalin.http.Context;
import za.co.mongala.legal.model.User;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Admin portal endpoints (FR8 / FR9 / FR10) - all require admin:model scope:
 *
 *  FR8  GET  /api/admin/users                     - list users + roles + active status
 *       POST /api/admin/users/{id}/revoke         - revoke access
 *       POST /api/admin/users/{id}/restore        - restore access
 *  FR9  GET  /api/admin/reports/audit.csv         - download the audit trail
 *       GET  /api/admin/reports/cases.csv         - download the case register
 *  FR10 GET  /api/admin/metrics                   - usage metrics
 *       GET  /api/admin/training-runs             - retraining runs
 *       POST /api/admin/training-runs/{id}/approve- approve a retraining run
 *       GET  /api/admin/datasets                  - training datasets
 */
public class AdminController {

    private final ApiContext api;

    public AdminController(ApiContext api) {
        this.api = api;
    }

    /* ---------------- FR8: users & roles ---------------- */

    public void listUsers(Context ctx) {
        ApiContext.require(ctx, "admin:model");
        ctx.json(api.users.listAll());
    }

    /** Provisions a new Lawyer, Client or Administrator account (FR8) and optionally links a Client to an existing matter. */
    @SuppressWarnings("unchecked")
    public void createUser(Context ctx) {
        ApiContext.require(ctx, "admin:model");
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        String name = String.valueOf(body.getOrDefault("name", "")).trim();
        String email = String.valueOf(body.getOrDefault("email", "")).trim().toLowerCase(java.util.Locale.ROOT);
        String password = String.valueOf(body.getOrDefault("password", ""));
        String roleStr = String.valueOf(body.getOrDefault("role", "CLIENT")).trim().toUpperCase(java.util.Locale.ROOT);
        String caseId = body.get("caseId") == null ? "" : String.valueOf(body.get("caseId")).trim();

        if (name.isEmpty()) {
            ctx.status(400).json(Map.of("error", "Full name is required"));
            return;
        }
        if (email.isEmpty() || !email.contains("@")) {
            ctx.status(400).json(Map.of("error", "A valid email address is required"));
            return;
        }
        if (password.length() < 6) {
            ctx.status(400).json(Map.of("error", "Password must be at least 6 characters"));
            return;
        }
        if (api.users.findByEmail(email) != null) {
            ctx.status(409).json(Map.of("error", "An account with email " + email + " already exists"));
            return;
        }
        za.co.mongala.legal.model.Role role;
        try {
            role = za.co.mongala.legal.model.Role.valueOf(roleStr);
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", "Invalid role - choose LAWYER, CLIENT or ADMIN"));
            return;
        }

        String id = "u" + UUID.randomUUID().toString().substring(0, 10);
        api.users.create(id, name, email, password, role);

        String linkedRef = null;
        if (role == za.co.mongala.legal.model.Role.CLIENT && !caseId.isEmpty()) {
            var existingCase = api.cases.findById(caseId);
            if (existingCase != null) {
                api.cases.assignClient(caseId, id, name);
                linkedRef = existingCase.getReference();
            }
        }

        String actor = ApiContext.session(ctx).name();
        api.audit.success(actor, "USER_CREATED",
                "Provisioned " + role.name() + " account for " + name + " (" + email + ")"
                        + (linkedRef != null ? " linked to matter " + linkedRef : ""));
        ctx.status(201).json(api.users.findByEmail(email));
    }

    public void revoke(Context ctx) {
        ApiContext.require(ctx, "admin:model");
        String id = ctx.pathParam("id");
        User u = api.users.listAll().stream().filter(x -> x.getId().equals(id)).findFirst().orElse(null);
        if (u == null) { api.notFound(ctx, "User"); return; }
        if (u.getRole() == za.co.mongala.legal.model.Role.ADMIN) {
            ctx.status(400).json(Map.of("error", "Administrators cannot revoke another administrator"));
            return;
        }
        api.users.setActive(id, false);
        api.audit.warning(ApiContext.session(ctx).name(), "ACCESS_REVOKED",
                "Access revoked for " + u.getName() + " (" + u.getEmail() + ", " + u.getRole().name() + ")");
        ctx.json(Map.of("ok", true, "message", "Access revoked for " + u.getName()));
    }

    public void restore(Context ctx) {
        ApiContext.require(ctx, "admin:model");
        String id = ctx.pathParam("id");
        User u = api.users.listAll().stream().filter(x -> x.getId().equals(id)).findFirst().orElse(null);
        if (u == null) { api.notFound(ctx, "User"); return; }
        api.users.setActive(id, true);
        api.loginGuard.reset(u.getEmail());
        api.audit.info(ApiContext.session(ctx).name(), "ACCESS_RESTORED",
                "Access restored for " + u.getName() + " (" + u.getEmail() + ")");
        ctx.json(Map.of("ok", true, "message", "Access restored for " + u.getName()));
    }

    public void lockedAccounts(Context ctx) {
        ApiContext.require(ctx, "admin:model");
        ctx.json(api.loginGuard.activeLocks());
    }

    public void unlockUser(Context ctx) {
        ApiContext.require(ctx, "admin:model");
        String id = ctx.pathParam("id");
        User u = api.users.listAll().stream().filter(x -> x.getId().equals(id)).findFirst().orElse(null);
        if (u != null) {
            api.loginGuard.reset(u.getEmail());
            api.audit.info(ApiContext.session(ctx).name(), "LOGIN_UNLOCKED",
                    "Cleared brute-force lockout for " + u.getName() + " (" + u.getEmail() + ")");
            ctx.json(Map.of("ok", true, "message", "Login lock cleared for " + u.getName()));
            return;
        }
        // Also support unlocking an arbitrary email key directly
        api.loginGuard.reset(id);
        api.audit.info(ApiContext.session(ctx).name(), "LOGIN_UNLOCKED",
                "Cleared brute-force lockout for " + id);
        ctx.json(Map.of("ok", true, "message", "Login lock cleared"));
    }

    /* ---------------- FR9: audits & reports ---------------- */

    public void auditCsv(Context ctx) {
        ApiContext.require(ctx, "admin:model");
        ctx.contentType("text/csv");
        ctx.header("Content-Disposition", "attachment; filename=audit-report.csv");
        ctx.result(za.co.mongala.legal.backend.service.ReportService.auditCsv(api.audit.all()));
    }

    public void casesCsv(Context ctx) {
        ApiContext.require(ctx, "admin:model");
        ctx.contentType("text/csv");
        ctx.header("Content-Disposition", "attachment; filename=cases-report.csv");
        ctx.result(za.co.mongala.legal.backend.service.ReportService.casesCsv(api.cases.search(null)));
    }

    /* ---------------- FR10: system, metrics, training ---------------- */

    public void metrics(Context ctx) {
        ApiContext.require(ctx, "admin:model");
        Map<String, Object> m = api.metrics.metrics();
        m.put("dataResidency", api.cfg.dataResidency());
        ctx.json(m);
    }

    public void trainingRuns(Context ctx) {
        ApiContext.require(ctx, "admin:model");
        ctx.json(api.training.runs());
    }

    public void approveTrainingRun(Context ctx) {
        ApiContext.require(ctx, "admin:model");
        String id = ctx.pathParam("id");
        api.training.approve(id, ApiContext.session(ctx).name(), LocalDateTime.now());
        api.audit.success(ApiContext.session(ctx).name(), "TRAINING_APPROVED",
                "Model retraining run " + id + " approved (ETR3 human-in-the-loop)");
        ctx.json(Map.of("ok", true, "message", "Retraining run approved"));
    }

    public void datasets(Context ctx) {
        ApiContext.require(ctx, "admin:model");
        ctx.json(api.training.datasets());
    }

    /** Rejected documents report - JSON list (admin). */
    public void rejections(Context ctx) {
        ApiContext.require(ctx, "admin:model");
        ctx.json(api.documents.rejectedDocuments());
    }

    /** Rejected documents report - CSV download (admin). */
    public void rejectionsCsv(Context ctx) {
        ApiContext.require(ctx, "admin:model");
        ctx.contentType("text/csv");
        ctx.header("Content-Disposition", "attachment; filename=rejection-reasons.csv");
        ctx.result(za.co.mongala.legal.backend.service.ReportService.rejectionsCsv(api.documents.rejectedDocuments()));
    }

    /** Upload a new training dataset (FR10 / ETR3). */
    public void uploadDataset(Context ctx) {
        ApiContext.require(ctx, "admin:model");
        var files = ctx.uploadedFiles("file");
        if (files.isEmpty()) {
            ctx.status(400).json(Map.of("error", "No dataset file provided (multipart field: file)"));
            return;
        }
        var uf = files.get(0);
        String name = ctx.formParam("name");
        if (name == null || name.isBlank()) name = uf.filename();
        String id = "ds" + UUID.randomUUID().toString().substring(0, 10);
        String storage = "datasets/" + id + "_" + uf.filename().replaceAll("[^a-zA-Z0-9._-]", "_");
        try {
            java.nio.file.Files.createDirectories(api.uploadDir.resolve("datasets"));
            byte[] bytes;
            try (java.io.InputStream in = uf.content()) { bytes = in.readAllBytes(); }
            java.nio.file.Files.write(api.uploadDir.resolve(storage), bytes);
            long records = new String(bytes, java.nio.charset.StandardCharsets.UTF_8).lines().count();
            long kb = Math.max(1, bytes.length / 1024);
            api.training.insert(id, name, records, kb, storage);
            api.audit.success(ApiContext.session(ctx).name(), "DATASET_UPLOADED",
                    "Training dataset \"" + name + "\" uploaded (" + records + " records)");
            ctx.status(201).json(api.training.findById(id));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Could not store dataset: " + e.getMessage()));
        }
    }

    /** Download a training dataset file (FR10 - download the training dataset). */
    public void downloadDataset(Context ctx) {
        ApiContext.require(ctx, "admin:model");
        var d = api.training.findById(ctx.pathParam("id"));
        if (d == null) { api.notFound(ctx, "Dataset"); return; }
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(api.uploadDir.resolve(d.getStoragePath()));
            ctx.contentType("text/plain");
            String safeName = d.getName().replaceAll("[^a-zA-Z0-9._-]", "_");
            String ext = d.getStoragePath().endsWith(".jsonl") ? ".jsonl" : ".csv";
            ctx.header("Content-Disposition", "attachment; filename=" + safeName + ext);
            ctx.result(bytes);
            api.audit.info(ApiContext.session(ctx).name(), "DATASET_DOWNLOAD",
                    "Admin downloaded dataset \"" + d.getName() + "\"");
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Dataset file not found on disk"));
        }
    }

    /** Download the whole system & model management report (FR10 - download all system management). */
    public void systemReport(Context ctx) {
        ApiContext.require(ctx, "admin:model");
        var m = api.metrics.metrics();
        m.put("dataResidency", api.cfg.dataResidency());
        ctx.contentType("text/csv");
        ctx.header("Content-Disposition", "attachment; filename=system-model-report.csv");
        ctx.result(za.co.mongala.legal.backend.service.ReportService.systemReport(
                m, api.training.runs(), api.training.datasets()));
    }

    /* ---------------- Live audit-chain verification & encrypted backups ---------------- */

    public void verifyAuditChain(Context ctx) {
        ApiContext.require(ctx, "admin:model");
        var res = api.audit.verifyChain();
        ctx.json(res);
    }

    public void listBackups(Context ctx) {
        ApiContext.require(ctx, "admin:model");
        ctx.json(api.backups.listBackups());
    }

    public void createBackup(Context ctx) {
        ApiContext.require(ctx, "admin:model");
        String actor = ApiContext.session(ctx).name();
        var rec = api.backups.createEncryptedBackup(actor);
        api.audit.success(actor, "ENCRYPTED_BACKUP_CREATED",
                "Encrypted backup " + rec.filename() + " created (" + rec.totalRows()
                        + " rows, backup " + rec.backupMs() + " ms, restore check " + rec.restoreVerifyMs() + " ms)");
        ctx.status(201).json(rec);
    }

    public void downloadBackup(Context ctx) {
        ApiContext.require(ctx, "admin:model");
        String filename = ctx.pathParam("filename");
        byte[] bytes = api.backups.readBackupBytes(filename);
        if (bytes == null) {
            api.notFound(ctx, "Backup file");
            return;
        }
        api.audit.info(ApiContext.session(ctx).name(), "BACKUP_DOWNLOADED",
                "Administrator downloaded encrypted backup " + filename);
        ctx.contentType("application/octet-stream");
        ctx.header("Content-Disposition", "attachment; filename=" + filename.replaceAll("[^a-zA-Z0-9._-]", ""));
        ctx.result(bytes);
    }

    /* ---------------- Practitioner AI corrections ---------------- */

    public void listCorrections(Context ctx) {
        ApiContext.require(ctx, "admin:model");
        ctx.json(api.training.listCorrections());
    }
}
