package za.co.mongala.legal.backend.api;

import io.javalin.http.Context;
import za.co.mongala.legal.model.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client portal endpoints (FR5 / US3) - scoped to read:client / write:client,
 * and restricted so a client can only ever see their own matters (NFR1).
 *
 *   GET  /api/client/cases
 *   GET  /api/me/unread
 *   GET  /api/cases/{id}/client-view      - approved summary + deadlines + docs + messages
 *   GET  /api/cases/{id}/messages
 *   POST /api/cases/{id}/messages
 *   GET  /api/documents                   - documents shared with the client
 */
public class ClientPortalController {

    private final ApiContext api;

    public ClientPortalController(ApiContext api) {
        this.api = api;
    }

    public void myCases(Context ctx) {
        ApiContext.require(ctx, "read:client");
        var s = ApiContext.session(ctx);
        List<Case> cases = s.role() == Role.CLIENT
                ? api.cases.byClient(s.userId())
                : api.cases.search(null);
        ctx.json(cases);
    }

    public void unreadCount(Context ctx) {
        ApiContext.require(ctx, "read:client");
        ctx.json(Map.of("unread", api.messages.unreadCountForClient(ApiContext.session(ctx).userId())));
    }

    public void clientView(Context ctx) {
        ApiContext.require(ctx, "read:client");
        String caseId = ctx.pathParam("id");
        ApiContext.requireClientOwner(ctx, caseId, api);
        Case c = api.cases.findById(caseId);
        if (c == null) { api.notFound(ctx, "Case"); return; }

        Summary approved = api.summaries.byCase(caseId).stream()
                .filter(s -> s.getStatus() == Summary.SummaryStatus.APPROVED)
                .findFirst().orElse(null);
        List<Deadline> deadlines = api.deadlines.byCase(caseId).stream()
                .filter(d -> d.daysLeft() >= 0).toList();
        List<LegalDocument> docs = api.documents.byCase(caseId).stream()
                .filter(d -> d.getProcessingStatus() == LegalDocument.ProcessingStatus.PROCESSED).toList();
        List<ClientMessage> messages = api.messages.byCase(caseId);
        long unread = api.messages.unreadCountForClient(ApiContext.session(ctx).userId());

        ctx.json(Map.of(
                "legalCase", c,
                "summary", approved == null ? Map.of() : approved,
                "deadlines", deadlines,
                "documents", docs,
                "messages", messages,
                "unread", unread,
                "billing", api.billing.statementForCase(caseId),
                "trust", api.trust.statementForCase(caseId)));
    }

    public void messages(Context ctx) {
        ApiContext.require(ctx, "read:client");
        String caseId = ctx.pathParam("id");
        ApiContext.requireClientOwner(ctx, caseId, api);
        ctx.json(api.messages.byCase(caseId));
    }

    /** Marks lawyer->client messages as read when the client opens the thread. */
    public void markReadByClient(Context ctx) {
        ApiContext.require(ctx, "write:client");
        String caseId = ctx.pathParam("id");
        ApiContext.requireClientOwner(ctx, caseId, api);
        api.messages.markCaseRead(caseId);
        ctx.json(Map.of("ok", true));
    }

    public void sendMessage(Context ctx) {
        ApiContext.require(ctx, "write:client");
        String caseId = ctx.pathParam("id");
        ApiContext.requireClientOwner(ctx, caseId, api);
        Map<String, String> body = ctx.bodyAsClass(Map.class);
        var s = ApiContext.session(ctx);
        boolean fromLawyer = s.role() != Role.CLIENT;
        ClientMessage m = new ClientMessage(
                "m" + UUID.randomUUID().toString().substring(0, 10),
                caseId,
                s.name(),
                body.getOrDefault("subject", fromLawyer ? "Message from your attorney" : "Message from client"),
                body.getOrDefault("body", ""),
                LocalDateTime.now(),
                fromLawyer, false, !fromLawyer ? false : true);
        api.messages.insert(m);
        api.audit.info(s.name(), "CLIENT_MESSAGE", "Message sent on case " + caseId);
        ctx.status(201).json(m);
    }

    /** Unread client->lawyer messages (alert icon for the lawyer). */
    public void lawyerUnread(Context ctx) {
        ApiContext.require(ctx, "read:client");
        ctx.json(Map.of("unread", api.messages.unreadCountForLawyer()));
    }

    /** Mark a case's client->lawyer messages as read by the lawyer. */
    public void markReadByLawyer(Context ctx) {
        ApiContext.require(ctx, "read:client");
        String caseId = ctx.pathParam("id");
        ApiContext.requireCaseRead(ctx, caseId, api);
        api.messages.markCaseReadByLawyer(caseId);
        ctx.json(Map.of("ok", true));
    }

    /** Lawyer sends a message to a client (from the case detail chat). */
    public void lawyerSendMessage(Context ctx) {
        ApiContext.require(ctx, "write:client");
        String caseId = ctx.pathParam("id");
        if (api.cases.findById(caseId) == null) { api.notFound(ctx, "Case"); return; }
        Map<String, String> body = ctx.bodyAsClass(Map.class);
        var s = ApiContext.session(ctx);
        ClientMessage m = new ClientMessage(
                "m" + UUID.randomUUID().toString().substring(0, 10),
                caseId,
                s.name(),
                body.getOrDefault("subject", "Message from your attorney"),
                body.getOrDefault("body", ""),
                LocalDateTime.now(), true, false, true);
        api.messages.insert(m);
        api.audit.info(s.name(), "CLIENT_MESSAGE", "Message sent to client on case " + caseId);
        ctx.status(201).json(m);
    }

    public void sharedDocuments(Context ctx) {
        ApiContext.require(ctx, "read:client");
        var s = ApiContext.session(ctx);
        List<Case> myCases = s.role() == Role.CLIENT ? api.cases.byClient(s.userId()) : api.cases.search(null);
        List<LegalDocument> docs = myCases.stream()
                .flatMap(c -> api.documents.byCase(c.getId()).stream())
                .filter(d -> d.getProcessingStatus() == LegalDocument.ProcessingStatus.PROCESSED)
                .toList();
        ctx.json(docs);
    }
}
