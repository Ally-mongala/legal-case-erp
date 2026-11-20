package za.co.mongala.legal.backend.calendar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import za.co.mongala.legal.backend.config.AppConfig;
import za.co.mongala.legal.model.Case;
import za.co.mongala.legal.model.Deadline;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * Real Google Calendar integration - creates, updates and deletes events through the
 * Google Calendar v3 REST API using an OAuth2 refresh token. No extra dependencies:
 * JDK HttpClient + Jackson.
 *
 * Activation (environment variables):
 *   LCE_GOOGLE_CAL_ID          - "primary" or a calendar id such as abc123@group.calendar.google.com
 *   LCE_GOOGLE_REFRESH_TOKEN   - from tools/GoogleCalendarAuth.java (see docs/GOOGLE_CALENDAR_SETUP.md)
 *   LCE_GOOGLE_CLIENT_ID       - OAuth client id
 *   LCE_GOOGLE_CLIENT_SECRET   - OAuth client secret
 *
 * Design points worth defending in the presentation:
 *  - the access token is cached until shortly before it expires, so "sync all" makes
 *    one token call rather than one per deadline;
 *  - transient Google failures (429 / 5xx) are retried with a short backoff;
 *  - Google's error bodies are translated into messages an attorney can act on;
 *  - the JSON is built with Jackson, so a title containing quotes or backslashes
 *    cannot corrupt the request.
 */
public class GoogleCalendarGateway implements CalendarGateway {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(20);
    /** Google rejects reminder overrides longer than four weeks. */
    private static final int MAX_REMINDER_MINUTES = 40_320;

    private final AppConfig cfg;
    private final String tokenUrl;
    private final String apiBase;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

    /** Access-token cache (guarded by this). */
    private String cachedToken;
    private Instant cachedTokenExpiry = Instant.EPOCH;

    public GoogleCalendarGateway(AppConfig cfg) {
        this(cfg, cfg.googleTokenUrl(), cfg.googleApiBase());
    }

    /** Endpoint-injectable constructor - used by the tests with a local stub server. */
    public GoogleCalendarGateway(AppConfig cfg, String tokenUrl, String apiBase) {
        this.cfg = cfg;
        this.tokenUrl = tokenUrl;
        this.apiBase = apiBase.endsWith("/") ? apiBase.substring(0, apiBase.length() - 1) : apiBase;
    }

    /* ------------------------------ create ------------------------------ */

    @Override
    public Receipt createEvent(Deadline d, Case c) throws Exception {
        String body = eventBody(d, c).toString();

        HttpResponse<String> res = send("POST", eventsUrl() + "?sendUpdates=none", body);
        if (res.statusCode() / 100 != 2) throw googleError("create the event", res);

        JsonNode node = mapper.readTree(res.body());
        String eventId = node.path("id").asText();
        if (eventId.isBlank()) throw new IllegalStateException("Google returned no event id");
        return new Receipt(eventId, cfg.googleCalendarId());
    }

    private ObjectNode eventBody(Deadline d, Case c) {
        String summary = notBlank(d.getTitle()) + " - " + notBlank(d.getCaseReference());
        int reminderMinutes = Math.min(Math.max(0, d.getReminderDays()) * 24 * 60, MAX_REMINDER_MINUTES);

        ObjectNode root = mapper.createObjectNode();
        root.put("summary", summary);
        String client = c == null ? "" : notBlank(c.getClientName());
        root.put("description", "Deadline created by LegalCase ERP"
                + (client.isEmpty() ? "" : " for " + client)
                + ". Reminder " + d.getReminderDays() + " day(s) before.");
        ObjectNode start = root.putObject("start");
        start.put("date", d.getDate().toString());
        ObjectNode end = root.putObject("end");
        end.put("date", d.getDate().plusDays(1).toString());   // Google's end date is exclusive
        ObjectNode reminders = root.putObject("reminders");
        reminders.put("useDefault", false);
        ObjectNode override = reminders.putArray("overrides").addObject();
        override.put("method", "popup");
        override.put("minutes", reminderMinutes);
        // traceability: the event can be traced back to the matter and the deadline
        ObjectNode trace = root.putObject("extendedProperties").putObject("private");
        trace.put("legalCaseErpDeadlineId", notBlank(d.getId()));
        trace.put("legalCaseErpCaseId", notBlank(d.getCaseId()));
        return root;
    }

    /* ------------------------------ delete ------------------------------ */

    @Override
    public void deleteEvent(String eventId, String calendarId) throws Exception {
        if (eventId == null || eventId.isBlank()) return;
        String cal = notBlank(calendarId) != null ? calendarId : cfg.googleCalendarId();
        HttpResponse<String> res = send("DELETE",
                api() + "/calendars/" + encode(cal) + "/events/" + encode(eventId), null);
        // 404 / 410 = already gone, which is exactly the state we want
        if (res.statusCode() == 404 || res.statusCode() == 410) return;
        if (res.statusCode() / 100 != 2) throw googleError("remove the event", res);
    }

    /* ------------------------------ probe ------------------------------- */

    /** Reads the calendar metadata: the cheapest call that proves the credentials work. */
    @Override
    public String probe() throws Exception {
        HttpResponse<String> res = send("GET",
                api() + "/calendars/" + encode(cfg.googleCalendarId()), null);
        if (res.statusCode() / 100 != 2) throw googleError("read the calendar", res);
        JsonNode node = mapper.readTree(res.body());
        String name = node.path("summary").asText(cfg.googleCalendarId());
        return "Connected to Google Calendar \"" + name + "\" (id: "
                + node.path("id").asText(cfg.googleCalendarId()) + ") and able to write events.";
    }

    /* ------------------------------ status ------------------------------ */

    @Override
    public Status status() {
        return new Status("google", true, cfg.googleConfigured(), cfg.googleCalendarId(),
                cfg.googleConfigured()
                        ? "Events are pushed to the Google calendar \"" + cfg.googleCalendarId() + "\"."
                        : "Google Calendar is not configured yet - the local .ics calendar is used instead.",
                cfg.missingGoogleVariables());
    }

    @Override
    public String description() {
        return "Google Calendar API (" + cfg.googleCalendarId() + ")";
    }

    /* --------------------------- HTTP plumbing --------------------------- */

    private String api() {
        return apiBase;
    }

    private String eventsUrl() {
        return api() + "/calendars/" + encode(cfg.googleCalendarId()) + "/events";
    }

    /** Sends an authorised request, refreshing the token when needed and retrying transient failures. */
    private HttpResponse<String> send(String method, String url, String body) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            HttpRequest.Builder b = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(READ_TIMEOUT)
                    .header("Authorization", "Bearer " + accessToken())
                    .header("Accept", "application/json");
            if (body != null) b.header("Content-Type", "application/json; charset=UTF-8");
            HttpRequest req = switch (method) {
                case "POST" -> b.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
                case "DELETE" -> b.DELETE().build();
                default -> b.GET().build();
            };
            try {
                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                boolean transientFailure = res.statusCode() == 429 || res.statusCode() >= 500;
                if (transientFailure && attempt < 2) {
                    Thread.sleep(400L * (attempt + 1));
                    continue;
                }
                // A 401 usually means the cached token was revoked: drop it and try once more.
                if (res.statusCode() == 401 && attempt < 2) {
                    synchronized (this) {
                        cachedToken = null;
                        cachedTokenExpiry = Instant.EPOCH;
                    }
                    continue;
                }
                return res;
            } catch (java.io.IOException e) {
                last = e;
                if (attempt < 2) Thread.sleep(400L * (attempt + 1));
            }
        }
        throw new IllegalStateException("Could not reach Google Calendar ("
                + (last == null ? "network error" : last.getMessage())
                + "). Check the internet connection; the local .ics calendar still works offline.");
    }

    /** Returns a cached access token, or exchanges the refresh token for a new one. */
    private synchronized String accessToken() throws Exception {
        if (cachedToken != null && Instant.now().isBefore(cachedTokenExpiry)) return cachedToken;

        String form = "client_id=" + encode(cfg.googleClientId())
                + "&client_secret=" + encode(cfg.googleClientSecret())
                + "&refresh_token=" + encode(cfg.googleRefreshToken())
                + "&grant_type=refresh_token";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .timeout(READ_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new IllegalStateException(tokenErrorMessage(res.statusCode(), res.body()));
        }
        JsonNode node = mapper.readTree(res.body());
        String token = node.path("access_token").asText("");
        if (token.isBlank()) throw new IllegalStateException("OAuth response contained no access_token");
        long expiresIn = node.path("expires_in").asLong(3600);
        cachedToken = token;
        // refresh one minute early so a token cannot expire mid-request
        cachedTokenExpiry = Instant.now().plusSeconds(Math.max(60, expiresIn - 60));
        return token;
    }

    private static String tokenErrorMessage(int status, String body) {
        String detail = body == null ? "" : body;
        if (detail.contains("invalid_grant")) {
            return "Google rejected the refresh token (invalid_grant). It has expired or was revoked - "
                    + "run tools/GoogleCalendarAuth.java again to get a new one. Note: while the Google "
                    + "Cloud project is in \"Testing\" mode, refresh tokens expire after 7 days.";
        }
        if (detail.contains("invalid_client")) {
            return "Google rejected the client credentials (invalid_client). Check "
                    + "LCE_GOOGLE_CLIENT_ID and LCE_GOOGLE_CLIENT_SECRET against the OAuth client in "
                    + "Google Cloud Console.";
        }
        return "OAuth token refresh failed (HTTP " + status + "): " + detail;
    }

    private static IllegalStateException googleError(String action, HttpResponse<String> res) {
        int code = res.statusCode();
        String body = res.body() == null ? "" : res.body();
        String reason = "";
        try {
            reason = new ObjectMapper().readTree(body).path("error").path("message").asText("");
        } catch (Exception ignored) {
        }
        String hint = switch (code) {
            case 400 -> "Google rejected the request (400). "
                    + (reason.isBlank() ? "" : reason + " ");
            case 401 -> "Google refused the access token (401). The credentials were rejected - "
                    + "re-run tools/GoogleCalendarAuth.java and restart the backend.";
            case 403 -> "Google refused permission (403). " + (reason.isBlank() ? "" : reason + " ")
                    + "Check that the Calendar API is enabled for the project and that the signed-in "
                    + "account granted the calendar.events scope.";
            case 404 -> "Calendar or event not found (404). Check LCE_GOOGLE_CAL_ID - use \"primary\" "
                    + "or the calendar's full id (something@group.calendar.google.com).";
            case 429 -> "Google rate limit reached (429). Try again in a minute.";
            default -> "Google Calendar API error " + code + ".";
        };
        return new IllegalStateException("Could not " + action + ". " + hint
                + (reason.isBlank() || code == 404 ? "" : "(" + reason + ")"));
    }

    private static String encode(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String notBlank(String s) {
        return s == null || s.isBlank() ? "" : s;
    }
}
