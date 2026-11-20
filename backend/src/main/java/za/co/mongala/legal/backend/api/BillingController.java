package za.co.mongala.legal.backend.api;

import io.javalin.http.Context;
import za.co.mongala.legal.backend.service.BillingService;
import za.co.mongala.legal.model.Case;

import java.time.LocalDate;
import java.util.Map;

/**
 * Time-tracking, disbursements and PDF fee-note endpoints:
 *   GET    /api/cases/{id}/billing      - statement + entries (lawyer or owning client)
 *   POST   /api/cases/{id}/billing      - record time or disbursement (write:case)
 *   DELETE /api/billing/{entryId}       - delete entry (write:case)
 *   GET    /api/cases/{id}/invoice.pdf  - generate & download PDF fee note (lawyer or owning client)
 */
public class BillingController {

    private final ApiContext api;

    public BillingController(ApiContext api) {
        this.api = api;
    }

    public void statement(Context ctx) {
        String caseId = ctx.pathParam("id");
        ApiContext.requireCaseRead(ctx, caseId, api);
        if (api.cases.findById(caseId) == null) {
            api.notFound(ctx, "Case");
            return;
        }
        ctx.json(api.billing.statementForCase(caseId));
    }

    @SuppressWarnings("unchecked")
    public void addEntry(Context ctx) {
        ApiContext.require(ctx, "write:case");
        String caseId = ctx.pathParam("id");
        Case c = api.cases.findById(caseId);
        if (c == null) {
            api.notFound(ctx, "Case");
            return;
        }
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        String type = String.valueOf(body.getOrDefault("entryType", "TIME"));
        String desc = String.valueOf(body.getOrDefault("description", "")).trim();
        if (desc.isEmpty()) {
            ctx.status(400).json(Map.of("error", "Description is required"));
            return;
        }
        LocalDate date = LocalDate.now();
        if (body.get("entryDate") != null && !String.valueOf(body.get("entryDate")).isBlank()) {
            try { date = LocalDate.parse(String.valueOf(body.get("entryDate"))); } catch (Exception ignored) {}
        }
        double hours = parseDouble(body.get("hours"), 1.0);
        double rate = parseDouble(body.get("hourlyRate"), 1850.0);
        double amount = parseDouble(body.get("amount"), 0.0);
        if ("DISBURSEMENT".equalsIgnoreCase(type) && amount <= 0) {
            ctx.status(400).json(Map.of("error", "Disbursement amount must be greater than zero"));
            return;
        }

        var s = ApiContext.session(ctx);
        BillingService.BillingEntry created = api.billing.addEntry(
                caseId, date, type, desc, hours, rate, amount, s.name());
        api.audit.info(s.name(), "BILLING_RECORDED",
                "Recorded " + created.entryType() + " on " + c.getReference()
                        + ": R " + String.format(java.util.Locale.US, "%.2f", created.amount()) + " (" + created.description() + ")");
        ctx.status(201).json(created);
    }

    public void deleteEntry(Context ctx) {
        ApiContext.require(ctx, "write:case");
        String id = ctx.pathParam("entryId");
        var existing = api.billing.findById(id);
        if (existing == null) {
            api.notFound(ctx, "Billing entry");
            return;
        }
        api.billing.delete(id);
        api.audit.warning(ApiContext.session(ctx).name(), "BILLING_DELETED",
                "Deleted billing entry " + id + " (" + existing.description() + ")");
        ctx.json(Map.of("ok", true));
    }

    public void downloadInvoicePdf(Context ctx) {
        String caseId = ctx.pathParam("id");
        ApiContext.requireCaseRead(ctx, caseId, api);
        Case c = api.cases.findById(caseId);
        if (c == null) {
            api.notFound(ctx, "Case");
            return;
        }
        var statement = api.billing.statementForCase(caseId);
        double settled = api.trust.transfersToBusinessForCase(caseId) + api.cases.getDirectPayments(caseId);
        byte[] pdf = api.billing.generateInvoicePdf(c, statement, settled);
        api.audit.info(ApiContext.session(ctx).name(), "INVOICE_GENERATED",
                "Generated PDF fee note for " + c.getReference() + " (Total R "
                        + String.format(java.util.Locale.US, "%.2f", statement.totalInclVat()) + ")");
        ctx.contentType("application/pdf");
        ctx.header("Content-Disposition", "attachment; filename=Invoice-" + c.getReference() + ".pdf");
        ctx.result(pdf);
    }

    /** Firm-wide ERP billing, trust & AI claim-quantum overview (Lawyer & Admin). */
    public void firmOverview(Context ctx) {
        ApiContext.require(ctx, "read:case");
        ctx.json(api.billing.firmOverview(api.cases, api.trust, api.documents, api.extractions));
    }

    /** Settles a matter's unpaid fee note directly from its LPC Section 86 client trust credit. */
    @SuppressWarnings("unchecked")
    public void settleFromTrust(Context ctx) {
        ApiContext.require(ctx, "write:case");
        String caseId = ctx.pathParam("id");
        Case c = api.cases.findById(caseId);
        if (c == null) { api.notFound(ctx, "Case"); return; }

        var row = api.billing.matterRow(c, api.cases, api.trust, api.documents, api.extractions);
        if (row.outstandingBalance() <= 0.01) {
            ctx.status(400).json(Map.of("error", "No outstanding fee-note balance to settle on " + c.getReference()));
            return;
        }
        if (row.availableTrustBalance() <= 0.01) {
            ctx.status(400).json(Map.of("error", "No available Section 86 trust credit on " + c.getReference()));
            return;
        }

        Map<String, Object> body = ctx.body().isBlank() ? Map.of() : ctx.bodyAsClass(Map.class);
        double requested = body.get("amount") != null
                ? parseDouble(body.get("amount"), 0.0)
                : Math.min(row.outstandingBalance(), row.availableTrustBalance());
        if (requested <= 0) {
            ctx.status(400).json(Map.of("error", "Settlement amount must be greater than zero"));
            return;
        }
        double settleAmt = Math.min(requested, row.outstandingBalance());

        var s = ApiContext.session(ctx);
        try {
            api.trust.recordTransaction(
                    caseId, LocalDate.now(), "TRANSFER_TO_BUSINESS",
                    "Settlement of fee note INV-" + c.getReference() + " (incl. 15% VAT)",
                    "INV-" + c.getReference(), settleAmt, s.name());
            var updated = api.billing.matterRow(c, api.cases, api.trust, api.documents, api.extractions);
            api.audit.success(s.name(), "INVOICE_SETTLED_FROM_TRUST",
                    "Settled R " + String.format(java.util.Locale.US, "%.2f", settleAmt)
                            + " from Section 86 trust on " + c.getReference()
                            + " (status: " + updated.paymentStatus() + ")");
            ctx.json(updated);
        } catch (IllegalArgumentException e) {
            api.audit.warning(s.name(), "TRUST_OVERDRAFT_BLOCKED",
                    "Blocked trust settlement on " + c.getReference() + ": " + e.getMessage());
            ctx.status(400).json(Map.of("error", e.getMessage()));
        }
    }

    /** Records a direct client payment (EFT/card into business account) against an unpaid fee note. */
    @SuppressWarnings("unchecked")
    public void recordDirectPayment(Context ctx) {
        ApiContext.require(ctx, "write:case");
        String caseId = ctx.pathParam("id");
        Case c = api.cases.findById(caseId);
        if (c == null) { api.notFound(ctx, "Case"); return; }

        var row = api.billing.matterRow(c, api.cases, api.trust, api.documents, api.extractions);
        if (row.outstandingBalance() <= 0.01) {
            ctx.status(400).json(Map.of("error", "No outstanding fee-note balance on " + c.getReference()));
            return;
        }
        Map<String, Object> body = ctx.body().isBlank() ? Map.of() : ctx.bodyAsClass(Map.class);
        double amt = parseDouble(body.get("amount"), row.outstandingBalance());
        if (amt <= 0) {
            ctx.status(400).json(Map.of("error", "Payment amount must be greater than zero"));
            return;
        }
        double applied = Math.min(amt, row.outstandingBalance());
        api.cases.recordDirectPayment(caseId, applied);
        var updated = api.billing.matterRow(c, api.cases, api.trust, api.documents, api.extractions);
        api.audit.success(ApiContext.session(ctx).name(), "INVOICE_PAYMENT_RECORDED",
                "Recorded direct payment of R " + String.format(java.util.Locale.US, "%.2f", applied)
                        + " on " + c.getReference() + " (status: " + updated.paymentStatus() + ")");
        ctx.json(updated);
    }

    /** 1-click AI-assisted document review billing pre-filled from NLP extractions. */
    @SuppressWarnings("unchecked")
    public void billAiDocumentReview(Context ctx) {
        ApiContext.require(ctx, "write:case");
        String docId = ctx.pathParam("id");
        var doc = api.documents.findById(docId);
        if (doc == null) { api.notFound(ctx, "Document"); return; }
        Case c = api.cases.findById(doc.getCaseId());
        if (c == null) { api.notFound(ctx, "Case"); return; }

        var exList = api.extractions.byDocument(docId);
        String docType = exList.stream()
                .filter(e -> "Document Type".equalsIgnoreCase(e.getFieldName()))
                .map(za.co.mongala.legal.model.Extraction::getFieldValue)
                .findFirst().orElse(doc.getDocType() + " pleading");
        String caseNo = exList.stream()
                .filter(e -> "Case Number".equalsIgnoreCase(e.getFieldName()))
                .map(za.co.mongala.legal.model.Extraction::getFieldValue)
                .findFirst().orElse(null);

        Map<String, Object> body = ctx.body().isBlank() ? Map.of() : ctx.bodyAsClass(Map.class);
        double defaultHours = Math.max(0.5, Math.min(1.5, Math.round((doc.getSizeKb() / 1200.0) * 2.0) / 2.0));
        double hours = parseDouble(body.get("hours"), defaultHours);
        double rate = parseDouble(body.get("hourlyRate"), 1850.0);

        String desc = "AI-assisted review & verification of " + docType + " (" + doc.getFileName()
                + (caseNo != null ? ", " + caseNo : "") + ")";
        var s = ApiContext.session(ctx);
        var entry = api.billing.addEntry(c.getId(), LocalDate.now(), "TIME", desc, hours, rate, 0.0, s.name());
        api.audit.info(s.name(), "AI_REVIEW_BILLED",
                "Billed AI-assisted document review on " + c.getReference() + ": " + hours + "h (" + doc.getFileName() + ")");
        ctx.status(201).json(entry);
    }

    /** Multi-sheet Microsoft Excel (.xlsx) ERP & AI Intelligence workbook download. */
    public void downloadErpWorkbookXlsx(Context ctx) {
        ApiContext.require(ctx, "read:case");
        byte[] xlsx = api.excelWorkbook.generateFirmWorkbook(
                api.cases, api.billing, api.trust, api.documents, api.extractions, api.deadlines);
        api.audit.info(ApiContext.session(ctx).name(), "ERP_WORKBOOK_EXPORTED",
                "Exported multi-sheet Excel (.xlsx) ERP & AI Intelligence workbook");
        ctx.contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        ctx.header("Content-Disposition", "attachment; filename=LegalCase-ERP-Workbook.xlsx");
        ctx.result(xlsx);
    }

    /* ---------------- Section 86 Trust Account Ledger ---------------- */

    public void trustStatement(Context ctx) {
        String caseId = ctx.pathParam("id");
        ApiContext.requireCaseRead(ctx, caseId, api);
        if (api.cases.findById(caseId) == null) { api.notFound(ctx, "Case"); return; }
        ctx.json(api.trust.statementForCase(caseId));
    }

    @SuppressWarnings("unchecked")
    public void addTrustTransaction(Context ctx) {
        ApiContext.require(ctx, "write:case");
        String caseId = ctx.pathParam("id");
        Case c = api.cases.findById(caseId);
        if (c == null) { api.notFound(ctx, "Case"); return; }
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        String txType = String.valueOf(body.getOrDefault("txType", "DEPOSIT"));
        String desc = String.valueOf(body.getOrDefault("description", "Trust account entry")).trim();
        String ref = body.get("reference") == null ? null : String.valueOf(body.get("reference"));
        double amount = parseDouble(body.get("amount"), 0.0);
        LocalDate date = LocalDate.now();
        if (body.get("txDate") != null && !String.valueOf(body.get("txDate")).isBlank()) {
            try { date = LocalDate.parse(String.valueOf(body.get("txDate"))); } catch (Exception ignored) {}
        }

        var s = ApiContext.session(ctx);
        try {
            var entry = api.trust.recordTransaction(caseId, date, txType, desc, ref, amount, s.name());
            api.audit.success(s.name(), "TRUST_LEDGER_" + entry.txType(),
                    c.getReference() + ": " + entry.txType() + " R "
                            + String.format(java.util.Locale.US, "%.2f", entry.amount())
                            + " (balance R " + String.format(java.util.Locale.US, "%.2f", entry.balanceAfter()) + ")");
            ctx.status(201).json(entry);
        } catch (IllegalArgumentException e) {
            api.audit.warning(s.name(), "TRUST_OVERDRAFT_BLOCKED",
                    "Blocked invalid trust transaction on " + c.getReference() + ": " + e.getMessage());
            ctx.status(400).json(Map.of("error", e.getMessage()));
        }
    }

    public void downloadTrustStatementPdf(Context ctx) {
        String caseId = ctx.pathParam("id");
        ApiContext.requireCaseRead(ctx, caseId, api);
        Case c = api.cases.findById(caseId);
        if (c == null) { api.notFound(ctx, "Case"); return; }
        var st = api.trust.statementForCase(caseId);
        byte[] pdf = api.trust.generateTrustStatementPdf(c, st);
        api.audit.info(ApiContext.session(ctx).name(), "TRUST_STATEMENT_DOWNLOADED",
                "Downloaded Section 86 trust statement PDF for " + c.getReference());
        ctx.contentType("application/pdf");
        ctx.header("Content-Disposition", "attachment; filename=TrustStatement-" + c.getReference() + ".pdf");
        ctx.result(pdf);
    }

    private static double parseDouble(Object o, double def) {
        if (o == null) return def;
        try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return def; }
    }
}
