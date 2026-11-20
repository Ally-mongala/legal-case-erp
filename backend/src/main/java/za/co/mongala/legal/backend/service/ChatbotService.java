package za.co.mongala.legal.backend.service;

import za.co.mongala.legal.backend.auth.JwtUtil;
import za.co.mongala.legal.backend.service.BillingService.CaseStatement;
import za.co.mongala.legal.backend.service.TrustAccountingService.TrustStatement;
import za.co.mongala.legal.model.Case;
import za.co.mongala.legal.model.Deadline;
import za.co.mongala.legal.model.LegalDocument;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-based help assistant (no external AI service, works offline).
 *
 * It answers two kinds of question:
 *   1. General help (how to pay, log in, use messages, documents, trust, ratings).
 *   2. Case questions (next court date, status, amount owed, document count).
 *
 * Access rules: a CLIENT can only get answers about their own cases (the same
 * owner check used by the client portal). Lawyers and administrators (read:case)
 * can ask about any case by its reference. The chatbot never stores messages.
 */
public class ChatbotService {

    public enum Intent {
        HELP, LOGIN_HELP, HOW_TO_PAY, TRUST_INFO, DOCUMENTS_HELP, MESSAGES_HELP, RATING_HELP,
        NEXT_HEARING, CASE_STATUS, BALANCE, DOCUMENT_COUNT, UNREAD, SEARCH, WEB, AI, UNKNOWN
    }

    public record Reply(Intent intent, String text, List<String> suggestions) {}

    private static final Pattern CASE_REF = Pattern.compile("C-\\d{4}-\\d{3}", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private static final List<String> CLIENT_SUGGESTIONS = List.of(
            "When is my next court date?", "How much do I owe?", "How do I pay my invoice?",
            "How do I message my attorney?");
    private static final List<String> STAFF_SUGGESTIONS = List.of(
            "What is the status of C-2026-001?", "How many documents are on C-2026-001?", "help");

    private final CaseService cases;
    private final DeadlineService deadlines;
    private final BillingService billing;
    private final TrustAccountingService trust;
    private final DocumentService documents;
    private final MessageService messages;

    public ChatbotService(CaseService cases, DeadlineService deadlines, BillingService billing,
                          TrustAccountingService trust, DocumentService documents, MessageService messages) {
        this.cases = cases;
        this.deadlines = deadlines;
        this.billing = billing;
        this.trust = trust;
        this.documents = documents;
        this.messages = messages;
    }

    /* ---------------- intent matching (pure, unit-testable) ---------------- */

    /**
     * Works out what the user is asking. Order matters: the more specific rules
     * come first so, for example, "trust balance" is treated as a balance question.
     */
    public static Intent classify(String raw) {
        String n = raw == null ? "" : raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
        if (n.isEmpty()) return Intent.HELP;
        String p = " " + n + " ";
        if (n.startsWith("search ")) return Intent.SEARCH; // an explicit search always searches

        if (n.contains("unread") || n.contains("new message") || n.contains("any message")) return Intent.UNREAD;
        if (n.contains("log in") || n.contains("login") || n.contains("sign in") || n.contains("locked")
                || n.contains("password") || n.contains("cant log") || n.contains("cannot log")) return Intent.LOGIN_HELP;
        if (n.contains("how many document") || n.contains("number of document") || n.contains("how many file")) return Intent.DOCUMENT_COUNT;
        if (n.contains("how much") || p.contains(" owe ") || n.contains("balance") || n.contains("total")
                || n.contains("amount")) return Intent.BALANCE;
        if (n.contains("trust")) return Intent.TRUST_INFO;
        if (n.contains("rating") || n.contains("feedback") || p.contains(" rate ") || p.contains(" review ")) return Intent.RATING_HELP;
        if (n.contains("pay") || n.contains("invoice") || n.contains("fee note") || n.contains("fees")) return Intent.HOW_TO_PAY;
        if (n.contains("document") || n.contains("upload") || n.contains("download") || p.contains(" pdf ")) return Intent.DOCUMENTS_HELP;
        if (n.contains("hearing") || n.contains("deadline") || n.contains("next date") || n.contains("court date")
                || (n.contains("court") && (n.contains("when") || n.contains("next") || n.contains("my")))) return Intent.NEXT_HEARING;
        if (n.contains("status") || n.contains("progress") || n.contains("stage")) return Intent.CASE_STATUS;
        if (n.contains("message") || n.contains("attorney") || n.contains("lawyer") || n.contains("contact")) return Intent.MESSAGES_HELP;
        if (p.contains(" help ") || p.contains(" hello ") || p.contains(" hi ") || n.contains("what can you")
                || n.contains("menu") || n.contains("option")) return Intent.HELP;
        if (n.startsWith("search ") || n.startsWith("find ") || n.startsWith("look up ") || n.startsWith("lookup ")
                || n.startsWith("show me ")) return Intent.SEARCH;
        if (n.contains("how to use") || n.contains("how do i use") || n.contains("use the system")
                || n.contains("how does this work") || n.contains("how does it work") || n.contains("guide")
                || n.contains("instruction") || n.contains("tutorial") || n.contains("getting started")) return Intent.HELP;
        return Intent.UNKNOWN;
    }

    /* ---------------- answers ---------------- */

    public Reply answer(JwtUtil.Session s, String raw) {
        Intent intent = classify(raw);
        boolean staff = s.has("read:case");

        if (intent == Intent.UNKNOWN) {
            // A plain case name (for example "Dlamini v Nedbank Ltd") is a case question, not an unknown one.
            List<Case> named = staff ? cases.search(null) : cases.byClient(s.userId());
            Case byName = findCaseByName(raw, named);
            if (byName != null) {
                String text = caseText(Intent.CASE_STATUS, byName) + " " + caseText(Intent.NEXT_HEARING, byName);
                return new Reply(Intent.CASE_STATUS, text, staff ? STAFF_SUGGESTIONS : CLIENT_SUGGESTIONS);
            }
            if (looksLikeCaseName(raw)) {
                return new Reply(intent, staff
                        ? "I could not find a case named \"" + raw.trim() + "\" in the system. Check the spelling, or use the case reference."
                        : "I could not find a case named \"" + raw.trim() + "\" on your account.",
                        staff ? STAFF_SUGGESTIONS : CLIENT_SUGGESTIONS);
            }
        }

        if (intent == Intent.SEARCH || intent == Intent.UNKNOWN) {
            List<Case> pool = staff ? cases.search(null) : cases.byClient(s.userId());
            Reply found = searchReply(raw, pool, staff, intent == Intent.UNKNOWN);
            if (found != null) return found;
            if (staff && AiAssistant.configured() && intent == Intent.UNKNOWN) {
                List<String> fields = new java.util.ArrayList<>();
                for (Case c : pool) {
                    fields.add(c.getTitle()); fields.add(c.getReference());
                    fields.add(c.getClientName()); fields.add(c.getOpposingParty());
                }
                String scrubbed = AiAssistant.scrub(raw, fields);
                try {
                    return new Reply(Intent.AI, AiAssistant.ask(scrubbed), STAFF_SUGGESTIONS);
                } catch (Exception e) {
                    // Local AI not running or failed: say why in the backend window, then fall back below.
                    System.err.println("[help chat] local AI at " + System.getenv("LCE_OLLAMA_URL")
                            + " could not answer: " + e + ". Is Ollama running, and was the backend started after LCE_OLLAMA_URL was set?");
                }
            }
            if (staff) {
                Reply web = internetReply(raw, pool);
                if (web != null) return web;
            }
            if (intent == Intent.SEARCH) {
                return new Reply(intent, "I found nothing for that in the cases you can see. Try the case name, "
                        + "the reference (for example C-2026-001), or a document file name.",
                        staff ? STAFF_SUGGESTIONS : CLIENT_SUGGESTIONS);
            }
        }

        switch (intent) {
            case HELP, LOGIN_HELP, HOW_TO_PAY, TRUST_INFO, DOCUMENTS_HELP, MESSAGES_HELP, RATING_HELP, UNKNOWN:
                return new Reply(intent, faqText(intent, staff), staff ? STAFF_SUGGESTIONS : CLIENT_SUGGESTIONS);
            case UNREAD: {
                long n = staff ? messages.unreadCountForLawyer() : messages.unreadCountForClient(s.userId());
                String text = n == 0 ? "You have no unread messages."
                        : "You have " + n + (n == 1 ? " unread message." : " unread messages.")
                        + (staff ? " Open the case to read it." : " Open Messages to read them.");
                return new Reply(intent, text, staff ? STAFF_SUGGESTIONS : CLIENT_SUGGESTIONS);
            }
            default:
                break;
        }

        // Case questions below need one specific case.
        List<Case> visible = staff ? cases.search(null) : cases.byClient(s.userId());
        if (visible.isEmpty()) {
            return new Reply(intent, staff ? "No cases were found." : "No case is linked to your account yet.",
                    staff ? STAFF_SUGGESTIONS : CLIENT_SUGGESTIONS);
        }
        Matcher refMatch = CASE_REF.matcher(raw == null ? "" : raw);
        String wantedRef = refMatch.find() ? refMatch.group().toUpperCase(Locale.ROOT) : null;
        Case c = resolveCase(raw, visible);
        if (c == null && wantedRef != null) {
            return new Reply(intent, staff
                    ? "I could not find a case with reference " + wantedRef + " in the system."
                    : "I could not find a case with reference " + wantedRef + " on your account.",
                    staff ? STAFF_SUGGESTIONS : CLIENT_SUGGESTIONS);
        }
        if (c == null) {
            String refs = visible.stream().limit(5).map(Case::getReference).reduce((a, b) -> a + ", " + b).orElse("");
            String text = staff
                    ? "Which case do you mean? Include its reference, for example: " + refs + "."
                    : "You have " + visible.size() + " cases. Please include the case reference in your question, for example: " + refs + ".";
            List<String> sugg = visible.stream().limit(3)
                    .map(x -> "What is the status of " + x.getReference() + "?").toList();
            return new Reply(intent, text, sugg);
        }
        return new Reply(intent, caseText(intent, c), staff ? STAFF_SUGGESTIONS : CLIENT_SUGGESTIONS);
    }

    private Case resolveCase(String raw, List<Case> visible) {
        String text = raw == null ? "" : raw;
        boolean refTyped = CASE_REF.matcher(text).find();
        Matcher m = CASE_REF.matcher(text);
        while (m.find()) {
            String ref = m.group();
            for (Case c : visible) {
                if (c.getReference() != null && c.getReference().equalsIgnoreCase(ref)) return c;
            }
        }
        if (!refTyped) {
            Case byName = findCaseByName(text, visible);
            if (byName != null) return byName;
        }
        // A reference was typed but matched nothing: do not guess another case.
        if (refTyped) return null;
        if (visible.size() == 1) return visible.get(0);
        return null;
    }

    private static final java.util.Set<String> STOP_WORDS = java.util.Set.of(
            "what", "show", "about", "find", "search", "look", "lookup", "tell", "give", "please", "that", "this",
            "with", "from", "have", "does", "case", "cases", "need", "want", "the", "and", "for", "are", "how", "can",
            "you", "me", "my", "your", "our", "any", "all", "there", "where", "when", "who", "which", "joke",
            "was", "were", "did", "why", "its", "also", "been", "being", "meaning", "mean");

    /** Words in the question that can be matched against case and document names. */
    static List<String> searchTokens(String raw) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String t : norm(raw).split(" ")) {
            if (t.length() >= 3 && !STOP_WORDS.contains(t) && t.chars().noneMatch(Character::isDigit)) out.add(t);
        }
        return out;
    }

    private static boolean containsAny(List<String> tokens, String... fields) {
        for (String f : fields) {
            String nf = norm(f);
            if (nf.isEmpty()) continue;
            for (String t : tokens) {
                if (nf.contains(t)) return true;
            }
        }
        return false;
    }

    /** Searches case titles, references, courts and document file names the caller can see. Null when nothing matches. */
    private Reply searchReply(String raw, List<Case> pool, boolean staff, boolean fallback) {
        List<String> tokens = searchTokens(raw);
        if (tokens.isEmpty()) return null;
        List<Case> hitCases = pool.stream()
                .filter(c -> fallback
                        ? containsAny(tokens, c.getTitle(), c.getReference())
                        : containsAny(tokens, c.getTitle(), c.getReference(), c.getCourt(), c.getClientName(),
                                c.getOpposingParty(), c.getMatterType()))
                .limit(5).toList();
        List<String> deadlineHits = new java.util.ArrayList<>();
        if (!fallback) {
            for (Case c : pool) {
                for (Deadline d : deadlines.byCase(c.getId())) {
                    if (deadlineHits.size() >= 5) break;
                    if (containsAny(tokens, d.getTitle())) {
                        deadlineHits.add(d.getTitle() + " on " + d.getDate().format(DATE_FMT) + " (" + c.getReference() + ")");
                    }
                }
            }
        }
        List<String> docHits = new java.util.ArrayList<>();
        for (Case c : pool) {
            for (LegalDocument d : documents.byCase(c.getId())) {
                if (docHits.size() >= 5) break;
                if (containsAny(tokens, d.getFileName())) docHits.add(d.getFileName() + " (" + c.getReference() + ")");
            }
        }
        if (hitCases.isEmpty() && docHits.isEmpty() && deadlineHits.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        if (!hitCases.isEmpty()) {
            sb.append("Cases: ");
            sb.append(hitCases.stream().map(c -> c.getTitle() + " (" + c.getReference() + ")")
                    .reduce((a, b) -> a + "; " + b).orElse(""));
            sb.append(". ");
        }
        if (!docHits.isEmpty()) {
            sb.append("Documents: ").append(String.join("; ", docHits)).append(". ");
        }
        if (!deadlineHits.isEmpty()) {
            sb.append("Deadlines: ").append(String.join("; ", deadlineHits)).append(". ");
        }
        sb.append("Ask about any of them by name or reference.");
        List<String> sugg = hitCases.isEmpty()
                ? (staff ? STAFF_SUGGESTIONS : CLIENT_SUGGESTIONS)
                : hitCases.stream().limit(3).map(c -> "What is the status of " + c.getReference() + "?").toList();
        return new Reply(Intent.SEARCH, sb.toString(), sugg);
    }

    /* ---------------- general questions on the internet (lawyers and admins only) ---------------- */

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Turns a question into safe search words for the internet. Case references, client names,
     * party names, court names and anything containing a digit are removed. Returns null when fewer
     * than two words are left, so nothing is sent.
     */
    static List<String> safeWebWords(String raw, List<String> privateFields) {
        java.util.Set<String> blocked = new java.util.HashSet<>();
        for (String f : privateFields) {
            for (String t : norm(f).split(" ")) {
                if (!t.isEmpty()) blocked.add(t);
            }
        }
        java.util.LinkedHashSet<String> words = new java.util.LinkedHashSet<>();
        for (String t : searchTokens(raw)) {
            if (blocked.contains(t) || t.chars().anyMatch(Character::isDigit)) continue;
            words.add(t);
        }
        if (words.size() < 2) return null;
        return words.stream().limit(8).toList();
    }

    private Reply internetReply(String raw, List<Case> pool) {
        List<String> private_ = new java.util.ArrayList<>();
        for (Case c : pool) {
            private_.add(c.getTitle());
            private_.add(c.getReference());
            private_.add(c.getClientName());
            private_.add(c.getOpposingParty());
        }
        List<String> words = safeWebWords(raw, private_);
        if (words == null) return null;
        String q = String.join(" ", words);
        if (googleConfigured()) {
            try {
                return googleReply(q);
            } catch (Exception e) {
                // Google unavailable or key rejected: fall back to Wikipedia below.
            }
        }
        try {
            // Try the whole question first, then each word on its own (longest first).
            List<String> attempts = new java.util.ArrayList<>();
            attempts.add(q);
            words.stream().sorted((a, b) -> b.length() - a.length()).forEach(attempts::add);
            JsonNode search = null;
            for (String attempt : attempts.stream().distinct().limit(4).toList()) {
                JsonNode r = JSON.readTree(get("https://en.wikipedia.org/w/api.php?action=opensearch&limit=1&namespace=0&format=json&search="
                        + URLEncoder.encode(attempt, StandardCharsets.UTF_8)));
                if (r.isArray() && r.size() >= 4 && !r.get(1).isEmpty()) { search = r; break; }
            }
            if (search == null) {
                return new Reply(Intent.WEB, "I could not find an article about \"" + q + "\" on Wikipedia. "
                        + "Try different words.", STAFF_SUGGESTIONS);
            }
            String title = search.get(1).get(0).asText();
            String url = search.get(3).get(0).asText();
            JsonNode summary = JSON.readTree(get("https://en.wikipedia.org/api/rest_v1/page/summary/"
                    + URLEncoder.encode(title.replace(' ', '_'), StandardCharsets.UTF_8)));
            String extract = summary.path("extract").asText("");
            if (extract.length() > 600) extract = extract.substring(0, 600).replaceAll("\\s+\\S*$", "") + "...";
            return new Reply(Intent.WEB, "From Wikipedia (" + title + "): " + extract + " Source: " + url
                    + ". This is general information, not legal advice. I did not send any case details.",
                    STAFF_SUGGESTIONS);
        } catch (Exception e) {
            return new Reply(Intent.WEB, "I could not reach the internet source just now. Try again later.",
                    STAFF_SUGGESTIONS);
        }
    }

    /** Google search is used only when the firm has set its own key and search engine ID. */
    static boolean googleConfigured() {
        String key = System.getenv("LCE_GOOGLE_SEARCH_KEY");
        String cx = System.getenv("LCE_GOOGLE_CSE_ID");
        return key != null && !key.isBlank() && cx != null && !cx.isBlank();
    }

    private Reply googleReply(String q) throws Exception {
        String url = "https://www.googleapis.com/customsearch/v1?key="
                + URLEncoder.encode(System.getenv("LCE_GOOGLE_SEARCH_KEY"), StandardCharsets.UTF_8)
                + "&cx=" + URLEncoder.encode(System.getenv("LCE_GOOGLE_CSE_ID"), StandardCharsets.UTF_8)
                + "&num=3&q=" + URLEncoder.encode(q, StandardCharsets.UTF_8);
        JsonNode root = JSON.readTree(get(url));
        JsonNode items = root.path("items");
        if (!items.isArray() || items.isEmpty()) {
            return new Reply(Intent.WEB, "Google found no results for \"" + q + "\". Try different words.", STAFF_SUGGESTIONS);
        }
        StringBuilder sb = new StringBuilder("From Google search: ");
        int i = 1;
        for (JsonNode item : items) {
            sb.append(i++).append(") ").append(item.path("title").asText(""))
                    .append(" - ").append(item.path("snippet").asText("").replace("\n", " "))
                    .append(" (").append(item.path("link").asText("")).append(") ");
        }
        sb.append("This is general information, not legal advice. I did not send any case details.");
        return new Reply(Intent.WEB, sb.toString(), STAFF_SUGGESTIONS);
    }

    private static String get(String url) throws java.io.IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(6))
                .header("User-Agent", "LegalCaseERP/1.0 (help chat)").GET().build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new java.io.IOException("HTTP " + resp.statusCode());
        return resp.body();
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    /** Finds a visible case whose title (for example "Dlamini v Nedbank Ltd") appears in the question. */
    static Case findCaseByName(String raw, List<Case> visible) {
        String q = norm(raw);
        if (q.length() < 5) return null;
        for (Case c : visible) {
            String t = norm(c.getTitle());
            if (!t.isEmpty() && (q.equals(t) || q.contains(t) || (t.contains(q) && q.length() >= 8))) return c;
        }
        return null;
    }

    /** True for text shaped like a case title ("Party v Party"). */
    static boolean looksLikeCaseName(String raw) {
        String q = " " + norm(raw) + " ";
        return q.contains(" v ") && q.trim().length() >= 8;
    }

    private String caseText(Intent intent, Case c) {
        String ref = c.getReference();
        switch (intent) {
            case NEXT_HEARING: {
                List<Deadline> upcoming = deadlines.byCase(c.getId()).stream()
                        .filter(d -> d.daysLeft() >= 0)
                        .sorted(Comparator.comparing(Deadline::getDate))
                        .toList();
                if (upcoming.isEmpty()) {
                    return "No upcoming dates are recorded for " + ref + ". Your attorney adds them as soon as they are set.";
                }
                Deadline d = upcoming.get(0);
                long days = d.daysLeft();
                return "The next date on " + ref + " is \"" + d.getTitle() + "\" on " + d.getDate().format(DATE_FMT)
                        + (days == 0 ? " (today)." : " (in " + days + (days == 1 ? " day)." : " days)."));
            }
            case CASE_STATUS: {
                String status = c.getStatus() == null ? "not recorded" : c.getStatus().getDisplayName();
                String court = c.getCourt() == null || c.getCourt().isBlank() ? "not recorded" : c.getCourt();
                return c.getTitle() + " (" + ref + ") is currently: " + status + ". Court: " + court + ".";
            }
            case BALANCE: {
                CaseStatement st = billing.statementForCase(c.getId());
                TrustStatement tr = trust.statementForCase(c.getId());
                return "Fee note for " + ref + " (including 15% VAT): " + money(st.totalInclVat())
                        + ". Trust credit available: " + money(tr.trustBalance()) + ". "
                        + "You can download the invoice and trust statement from the My Case page.";
            }
            case DOCUMENT_COUNT: {
                long n = documents.byCase(c.getId()).stream()
                        .filter(d -> d.getProcessingStatus() == LegalDocument.ProcessingStatus.PROCESSED)
                        .count();
                return "There " + (n == 1 ? "is " : "are ") + n + (n == 1 ? " processed document" : " processed documents")
                        + " on " + ref + ".";
            }
            default:
                return "Sorry, I could not work that out. Try asking about the next court date, what is owed, or the case status.";
        }
    }

    private static String faqText(Intent intent, boolean staff) {
        switch (intent) {
            case LOGIN_HELP:
                return "If the wrong password is entered 3 times, the account is locked for 15 minutes. "
                        + "Wait 15 minutes and try again. If you are still locked out, ask your administrator to unlock the account.";
            case HOW_TO_PAY:
                return "Your fee note includes 15% VAT. On the My Case page, select \"Download invoice (PDF)\" and follow the payment "
                        + "instructions on the invoice. If you have paid, send a message to your attorney so they can confirm it.";
            case TRUST_INFO:
                return "Trust money is money the firm holds for you in a trust account. Deposits add to your trust credit, and the firm "
                        + "can use that credit to settle your fee note. The \"Trust statement (PDF)\" on the My Case page lists every movement.";
            case DOCUMENTS_HELP:
                return "Open Documents from the menu to download documents your attorney has shared, or to share a new document with them.";
            case MESSAGES_HELP:
                return "Open Messages from the menu to send a secure message to your attorney. Replies appear there, and the bell icon "
                        + "at the top shows when something new arrives.";
            case RATING_HELP:
                return "Open Rate Lawyer from the menu to give your attorney a star rating and a comment.";
            case UNKNOWN:
                return "Sorry, I did not understand that. Try asking about the next court date, what you owe, how to pay, "
                        + "or how to message your attorney.";
            case HELP:
            default:
                return staff
                        ? "To use the system: open Cases to find a case. Each case page holds its documents, deadlines, billing and "
                          + "messages. Calendar shows deadlines, and Billing & Trust shows fees and trust. "
                          + "Ask me about a case by its reference (for example C-2026-001) or its name (for example Dlamini v Nedbank Ltd). "
                          + "I can also tell you the next court date, status, amount owed, document count and unread messages."
                        : "I can help with: your next court date, what you owe, your case status, your documents, your messages, "
                          + "paying your invoice, and how the trust account works.";
        }
    }

    private static String money(double v) {
        return "R " + String.format(Locale.US, "%,.2f", v);
    }
}
