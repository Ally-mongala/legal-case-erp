package za.co.mongala.legal.backend.api;

import io.javalin.http.Context;
import za.co.mongala.legal.model.Rating;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client ratings of lawyers (FR7):
 *   POST /api/ratings          - client submits a rating (write:client)
 *   GET  /api/ratings          - list (lawyer/admin - read:case)
 *   GET  /api/ratings/average  - average score
 */
public class RatingController {

    private final ApiContext api;

    public RatingController(ApiContext api) {
        this.api = api;
    }

    public void submit(Context ctx) {
        ApiContext.require(ctx, "write:client");
        var s = ApiContext.session(ctx);
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        String caseId = (String) body.get("caseId");
        if (caseId == null || api.cases.findById(caseId) == null) {
            ctx.status(400).json(Map.of("error", "A valid caseId is required"));
            return;
        }
        ApiContext.requireClientOwner(ctx, caseId, api);
        int score;
        try {
            score = Integer.parseInt(String.valueOf(body.get("score")));
        } catch (Exception e) {
            score = 0;
        }
        if (score < 1 || score > 5) {
            ctx.status(400).json(Map.of("error", "Score must be between 1 and 5"));
            return;
        }
        String lawyerName = api.cases.findById(caseId).getClientName() == null ? "" : "";
        // find the lawyer who uploaded the most recent document on the case, else "Assigned attorney"
        String lawyer = api.documents.byCase(caseId).stream().findFirst()
                .map(d -> d.getUploadedBy()).orElse("Assigned attorney");

        Rating r = new Rating("r" + UUID.randomUUID().toString().substring(0, 10),
                caseId, s.userId(), lawyer, score,
                body.get("comment") == null ? "" : String.valueOf(body.get("comment")),
                LocalDateTime.now());
        api.ratings.insert(r);
        api.audit.success(s.name(), "RATING_SUBMITTED",
                "Client rated lawyer \"" + lawyer + "\" " + score + "/5 on case " + caseId);
        ctx.status(201).json(r);
    }

    public void list(Context ctx) {
        var session = ApiContext.session(ctx);
        List<za.co.mongala.legal.model.Rating> all = api.ratings.all();
        if (session.has("read:case")) {
            ctx.json(all);
            return;
        }
        if (session.has("read:client")) {
            // clients may see the ratings on their own cases only
            List<String> myCaseIds = api.cases.byClient(session.userId()).stream()
                    .map(za.co.mongala.legal.model.Case::getId).toList();
            ctx.json(all.stream().filter(r -> myCaseIds.contains(r.getCaseId())).toList());
            return;
        }
        throw new io.javalin.http.ForbiddenResponse("Insufficient scope");
    }

    public void average(Context ctx) {
        var session = ApiContext.session(ctx);
        List<za.co.mongala.legal.model.Rating> all = api.ratings.all();
        if (!session.has("read:case") && session.has("read:client")) {
            List<String> myCaseIds = api.cases.byClient(session.userId()).stream()
                    .map(za.co.mongala.legal.model.Case::getId).toList();
            all = all.stream().filter(r -> myCaseIds.contains(r.getCaseId())).toList();
        }
        if (all.isEmpty()) {
            ctx.json(Map.of("average", 0.0, "count", 0));
            return;
        }
        double avg = all.stream().mapToInt(za.co.mongala.legal.model.Rating::getScore).average().orElse(0);
        ctx.json(Map.of("average", avg, "count", all.size()));
    }
}
