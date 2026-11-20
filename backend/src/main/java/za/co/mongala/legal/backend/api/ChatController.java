package za.co.mongala.legal.backend.api;

import io.javalin.http.Context;
import za.co.mongala.legal.backend.service.ChatbotService;

import java.util.Map;

/**
 * Help chat robot (rule-based, see ChatbotService).
 *
 *   POST /api/chat   body: {"message": "..."}   ->  {"reply": "...", "suggestions": [...]}
 *
 * Any signed-in user may ask. Case answers are limited to the caller's own cases
 * for clients. Only the intent name is written to the audit log, never the text.
 */
public class ChatController {

    private static final int MAX_CHARS = 500;

    private final ApiContext api;
    private final ChatbotService bot;

    public ChatController(ApiContext api) {
        this.api = api;
        this.bot = new ChatbotService(api.cases, api.deadlines, api.billing, api.trust, api.documents, api.messages);
    }

    public void ask(Context ctx) {
        ApiContext.require(ctx, "read:client");
        var s = ApiContext.session(ctx);
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        Object raw = body == null ? null : body.get("message");
        String message = raw == null ? "" : String.valueOf(raw).trim();
        if (message.length() > MAX_CHARS) message = message.substring(0, MAX_CHARS);

        ChatbotService.Reply r = bot.answer(s, message);
        api.audit.info(s.name(), "CHATBOT_QUERY", "Help chat answered: " + r.intent());
        ctx.json(Map.of("reply", r.text(), "suggestions", r.suggestions()));
    }
}
