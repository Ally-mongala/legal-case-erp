package za.co.mongala.legal.backend.calendar;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import za.co.mongala.legal.backend.config.AppConfig;
import za.co.mongala.legal.model.Case;
import za.co.mongala.legal.model.CaseStatus;
import za.co.mongala.legal.model.Deadline;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the calendar integration without any real Google account. A local stub server
 * plays the roles of Google's OAuth token endpoint and the Calendar v3 API, so the
 * request shape, token caching, error translation and delete path are all exercised.
 */
class CalendarGatewayTest {

    @TempDir Path tmp;

    private HttpServer stub;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private volatile int eventsStatus = 200;
    private volatile int tokenCalls = 0;
    private volatile String tokenBody = "{\"access_token\":\"ya29.test\",\"expires_in\":3599}";
    private volatile int tokenStatus = 200;

    private Case legalCase() {
        return new Case("c1", "C-2026-014", "Test matter", "u2", "Thabo Nkosi", "", CaseStatus.OPEN,
                "Personal Injury", "Johannesburg High Court", "", LocalDate.of(2026, 11, 1), 0);
    }

    private Deadline deadline(String id, String title, LocalDate date) {
        return new Deadline(id, "c1", "C-2026-014", title, date, Deadline.DeadlineType.FILING, 3, false);
    }

    @BeforeEach
    void startStub() throws Exception {
        stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.createContext("/token", ex -> {
            tokenCalls++;
            respond(ex, tokenBody, tokenStatus);
        });
        stub.createContext("/calendar/v3", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(ex.getRequestMethod() + " " + ex.getRequestURI().getRawPath()
                    + " auth=" + ex.getRequestHeaders().getFirst("Authorization") + " body=" + body);
            if (ex.getRequestMethod().equals("POST")) {
                respond(ex, "{\"id\":\"google-evt-123\"}", eventsStatus);
            } else if (ex.getRequestMethod().equals("DELETE")) {
                respond(ex, "", eventsStatus == 200 ? 204 : eventsStatus);
            } else {
                respond(ex, "{\"id\":\"primary\",\"summary\":\"Attorney Diary\"}", 200);
            }
        });
        stub.start();
    }

    @AfterEach
    void stopStub() {
        stub.stop(0);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange ex, String body, int status) throws java.io.IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, b.length == 0 ? -1 : b.length);
        if (b.length > 0) ex.getResponseBody().write(b);
        ex.close();
    }

    private GoogleCalendarGateway google() {
        String base = "http://127.0.0.1:" + stub.getAddress().getPort();
        AppConfig cfg = new AppConfig(8080, tmp.toString(), "s", "k", "ZA", 12, false,
                "primary", "refresh-xyz", "client-id", "client-secret",
                "jdbc:h2:mem:x", "sa", "", base + "/token", base + "/calendar/v3");
        return new GoogleCalendarGateway(cfg, base + "/token", base + "/calendar/v3");
    }

    @Test
    @DisplayName("CAL-01: Google create posts a valid all-day event with a reminder and traceability")
    void googleCreatePostsEvent() throws Exception {
        var gw = google();
        var receipt = gw.createEvent(deadline("dl1", "Plea due \"urgent\"", LocalDate.of(2026, 11, 16)), legalCase());

        assertEquals("google-evt-123", receipt.eventId());
        assertEquals("primary", receipt.calendarId());
        assertEquals(1, requests.size());
        String req = requests.get(0);
        assertTrue(req.startsWith("POST /calendar/v3/calendars/primary/events"), req);
        assertTrue(req.contains("auth=Bearer ya29.test"), req);
        assertTrue(req.contains("\"date\":\"2026-11-16\""), req);
        assertTrue(req.contains("\"date\":\"2026-11-17\""), "end date must be exclusive");
        assertTrue(req.contains("\"minutes\":4320"), "3 days = 4320 minutes");
        assertTrue(req.contains("legalCaseErpDeadlineId"), req);
        // quotes in the title must be escaped by Jackson, not break the JSON
        assertTrue(req.contains("Plea due \\\"urgent\\\""), req);
    }

    @Test
    @DisplayName("CAL-02: the access token is cached across several events (one token call)")
    void tokenIsCached() throws Exception {
        var gw = google();
        gw.createEvent(deadline("a", "One", LocalDate.of(2026, 11, 20)), legalCase());
        gw.createEvent(deadline("b", "Two", LocalDate.of(2026, 11, 21)), legalCase());
        gw.createEvent(deadline("c", "Three", LocalDate.of(2026, 11, 22)), legalCase());
        assertEquals(1, tokenCalls, "token must be fetched once and reused");
    }

    @Test
    @DisplayName("CAL-03: Google 403 is translated into an actionable message")
    void forbiddenIsTranslated() {
        eventsStatus = 403;
        var gw = google();
        Exception e = assertThrows(IllegalStateException.class,
                () -> gw.createEvent(deadline("a", "One", LocalDate.of(2026, 11, 20)), legalCase()));
        assertTrue(e.getMessage().contains("403"), e.getMessage());
        assertTrue(e.getMessage().contains("calendar.events"), e.getMessage());
    }

    @Test
    @DisplayName("CAL-04: revoked refresh token (invalid_grant) tells the user to re-authorise")
    void revokedTokenIsExplained() {
        tokenStatus = 400;
        tokenBody = "{\"error\":\"invalid_grant\",\"error_description\":\"Token has been expired or revoked.\"}";
        String base = "http://127.0.0.1:" + stub.getAddress().getPort();
        var gw = new GoogleCalendarGateway(cfgWithBadToken(), base + "/token", base + "/calendar/v3");
        Exception e = assertThrows(Exception.class,
                () -> gw.createEvent(deadline("a", "One", LocalDate.of(2026, 11, 20)), legalCase()));
        assertTrue(e.getMessage().contains("invalid_grant"), e.getMessage());
        assertTrue(e.getMessage().contains("GoogleCalendarAuth"), e.getMessage());
        tokenStatus = 200;
        tokenBody = "{\"access_token\":\"ya29.test\",\"expires_in\":3599}";
    }

    private AppConfig cfgWithBadToken() {
        String base = "http://127.0.0.1:" + stub.getAddress().getPort();
        return new AppConfig(8080, tmp.toString(), "s", "k", "ZA", 12, false,
                "primary", "bad-token", "client-id", "client-secret",
                "jdbc:h2:mem:x", "sa", "", base + "/token", base + "/calendar/v3");
    }

    @Test
    @DisplayName("CAL-05: delete sends DELETE for the stored event id, and 404 counts as already gone")
    void deleteEvent() throws Exception {
        var gw = google();
        gw.deleteEvent("google-evt-123", "primary");
        assertTrue(requests.get(0).startsWith("DELETE /calendar/v3/calendars/primary/events/google-evt-123"),
                requests.get(0));

        eventsStatus = 404;
        assertDoesNotThrow(() -> gw.deleteEvent("gone", "primary"));
    }

    @Test
    @DisplayName("CAL-06: probe reads the calendar and reports its name")
    void probeReadsCalendar() throws Exception {
        String msg = google().probe();
        assertTrue(msg.contains("Attorney Diary"), msg);
    }

    @Test
    @DisplayName("CAL-07: local .ics feed keeps every deadline, de-duplicates, and removes on delete")
    void localIcsAppendsAndDeletes() throws Exception {
        var gw = new LocalCalendarGateway(tmp);
        var r1 = gw.createEvent(deadline("d1", "Summons", LocalDate.of(2026, 11, 13)), legalCase());
        var r2 = gw.createEvent(deadline("d2", "Plea", LocalDate.of(2026, 11, 16)), legalCase());

        String ics = Files.readString(tmp.resolve("calendar.ics"));
        assertEquals(2, count(ics, "BEGIN:VEVENT"), "both deadlines must be present");
        assertTrue(ics.contains("BEGIN:VCALENDAR") && ics.trim().endsWith("END:VCALENDAR"));
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20261113"));

        gw.deleteEvent(r1.eventId(), r1.calendarId());
        ics = Files.readString(tmp.resolve("calendar.ics"));
        assertEquals(1, count(ics, "BEGIN:VEVENT"));
        assertTrue(ics.contains(r2.eventId()));
        assertFalse(ics.contains(r1.eventId()));
    }

    @Test
    @DisplayName("CAL-08: status shows missing Google variables and never returns secrets")
    void statusHasNoSecrets() {
        AppConfig partial = new AppConfig(8080, tmp.toString(), "s", "k", "ZA", 12, false,
                "primary", null, "client-id", null,
                "jdbc:h2:mem:x", "sa", "", "t", "a");
        assertTrue(partial.googlePartiallyConfigured());
        List<String> missing = new ArrayList<>(partial.missingGoogleVariables());
        assertEquals(List.of("LCE_GOOGLE_REFRESH_TOKEN", "LCE_GOOGLE_CLIENT_SECRET"), missing);

        var status = new GoogleCalendarGateway(partial).status();
        assertFalse(status.configured());
        assertFalse(status.toString().contains("client-id"), "client id must not leak into status");
    }

    private static int count(String s, String needle) {
        int n = 0, i = 0;
        while ((i = s.indexOf(needle, i)) >= 0) { n++; i += needle.length(); }
        return n;
    }
}
