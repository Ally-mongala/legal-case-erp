package za.co.mongala.legal.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Local AI for the help chat, using an Ollama server on the firm's own machine.
 *
 * Off unless LCE_OLLAMA_URL is set (for example http://localhost:11434). Nothing leaves the
 * machine. The model sees only the scrubbed question and the fixed help text below, never
 * case facts. Case facts still come from the rule-based answers in ChatbotService.
 */
public final class AiAssistant {

    private static final Pattern CASE_REF = Pattern.compile("C-\\d{4}-\\d{3}", Pattern.CASE_INSENSITIVE);
    private static final Pattern WORD = Pattern.compile("[A-Za-z][A-Za-z'-]*");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Fixed facts about the app. This is the only app context the model receives. */
    static final String APP_HELP =
            "LegalCase ERP is a case management system for a South African law firm. "
          + "The menu has Dashboard, Cases, Billing & Trust, Precedent Search, Calendar, Activity and Client Ratings. "
          + "Each case page has its documents, deadlines, billing and messages, a Case file (PDF) button, and status buttons. "
          + "Documents can be uploaded with the upload button or by dragging a PDF or Word file onto the Documents card. "
          + "The Calendar shows deadlines and can sync them. The dark mode button is in the top bar. "
          + "Passwords lock an account for 15 minutes after 3 wrong attempts.";

    private AiAssistant() {}

    public static boolean configured() {
        String url = System.getenv("LCE_OLLAMA_URL");
        return url != null && !url.isBlank();
    }

    /**
     * Removes case references and any word that belongs to a case, client, party or court name.
     * Removed words become [private], so the question keeps its shape.
     */
    static String scrub(String raw, List<String> privateFields) {
        if (raw == null) return "";
        Set<String> blocked = new HashSet<>();
        for (String f : privateFields) {
            if (f == null) continue;
            for (Matcher m = WORD.matcher(f); m.find(); ) {
                String w = m.group().toLowerCase(Locale.ROOT);
                if (w.length() >= 4) blocked.add(w); // short words such as "of" or "SA" are not private on their own
            }
        }
        String noRefs = CASE_REF.matcher(raw).replaceAll("[private]");
        Matcher m = WORD.matcher(noRefs);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String w = m.group();
            m.appendReplacement(out, Matcher.quoteReplacement(blocked.contains(w.toLowerCase(Locale.ROOT)) ? "[private]" : w));
        }
        m.appendTail(out);
        return out.toString().trim();
    }

    /** Asks the local model. Throws on any network or format problem, so the caller can fall back. */
    public static String ask(String scrubbedQuestion) throws Exception {
        String base = System.getenv("LCE_OLLAMA_URL").replaceAll("/+$", "");
        String model = System.getenv("LCE_OLLAMA_MODEL") != null && !System.getenv("LCE_OLLAMA_MODEL").isBlank()
                ? System.getenv("LCE_OLLAMA_MODEL") : "llama3.2:3b";

        ObjectNode body = JSON.createObjectNode();
        body.put("model", model);
        body.put("stream", false);
        var messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content",
                "You are the help assistant inside LegalCase ERP. Answer in short, plain English. "
              + "You cannot see case data: if asked about a case's dates, amounts or status, say the user should ask "
              + "by case reference or open the case. Do not give legal advice. "
              + "App facts: " + APP_HELP);
        messages.addObject().put("role", "user").put("content", scrubbedQuestion);

        HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/api/chat"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new IllegalStateException("AI server answered HTTP " + resp.statusCode());
        JsonNode content = JSON.readTree(resp.body()).path("message").path("content");
        String text = content.asText("").trim();
        if (text.isEmpty()) throw new IllegalStateException("AI server gave an empty answer");
        return text.length() > 1200 ? text.substring(0, 1200) + "..." : text;
    }
}
