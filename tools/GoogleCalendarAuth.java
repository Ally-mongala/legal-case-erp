import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One-time helper that obtains a Google Calendar OAuth2 REFRESH TOKEN for LegalCase ERP.
 *
 * Run it with the JDK's single-file launcher (no build step, no dependencies):
 *
 *   java tools/GoogleCalendarAuth.java
 *
 * It asks for your OAuth client id and secret (or reads them from the environment), opens
 * the Google consent page in your browser, catches the one-time code on
 * http://localhost:8765/oauth2callback, exchanges it for tokens and prints the three
 * values to paste into run-backend.bat.
 *
 * Full instructions: docs/GOOGLE_CALENDAR_SETUP.md
 */
public class GoogleCalendarAuth {

    private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String SCOPE = "https://www.googleapis.com/auth/calendar.events";
    private static final int PORT = 8765;
    private static final String REDIRECT = "http://localhost:" + PORT + "/oauth2callback";

    public static void main(String[] args) throws Exception {
        System.out.println();
        System.out.println("LegalCase ERP - Google Calendar refresh-token helper");
        System.out.println("====================================================");
        System.out.println();

        String clientId = read("LCE_GOOGLE_CLIENT_ID", args.length > 0 ? args[0] : null,
                "OAuth client id (ends with .apps.googleusercontent.com)");
        String clientSecret = read("LCE_GOOGLE_CLIENT_SECRET", args.length > 1 ? args[1] : null,
                "OAuth client secret");

        String consentUrl = AUTH_URL
                + "?client_id=" + enc(clientId)
                + "&redirect_uri=" + enc(REDIRECT)
                + "&response_type=code"
                + "&scope=" + enc(SCOPE)
                + "&access_type=offline"
                + "&prompt=consent"
                + "&include_granted_scopes=true";

        System.out.println();
        System.out.println("1. Open this address in your browser and sign in with the Google");
        System.out.println("   account that owns the calendar:");
        System.out.println();
        System.out.println("   " + consentUrl);
        System.out.println();
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(consentUrl));
        } catch (Exception ignored) {
        }
        System.out.println("2. Approve access. The browser will land on a page that says");
        System.out.println("   \"This site can't be reached\" - that is expected: this helper");
        System.out.println("   already caught the code. You can close that tab.");
        System.out.println();

        String code = waitForCode();
        System.out.println("3. Exchanging the code for tokens...");

        String form = "code=" + enc(code)
                + "&client_id=" + enc(clientId)
                + "&client_secret=" + enc(clientSecret)
                + "&redirect_uri=" + enc(REDIRECT)
                + "&grant_type=authorization_code";

        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        HttpResponse<String> res = http.send(HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build(), HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() / 100 != 2) {
            System.out.println();
            System.out.println("[ERROR] Google refused the exchange (HTTP " + res.statusCode() + ")");
            System.out.println(res.body());
            System.out.println();
            System.out.println("Common causes:");
            System.out.println("  - the redirect URI http://localhost:8765/oauth2callback is not listed");
            System.out.println("    on the OAuth client (it must be a \"Desktop app\" client)");
            System.out.println("  - the code was already used (run the helper again)");
            System.exit(1);
        }

        String refresh = field(res.body(), "refresh_token");
        if (refresh == null || refresh.isBlank()) {
            System.out.println();
            System.out.println("[ERROR] Google returned no refresh_token.");
            System.out.println("Run the helper again and keep prompt=consent (it is set for you), or");
            System.out.println("remove LegalCase ERP from https://myaccount.google.com/permissions first.");
            System.exit(1);
        }

        System.out.println();
        System.out.println("====================================================");
        System.out.println(" SUCCESS - paste these three lines into run-backend.bat");
        System.out.println(" (just below the @echo off line), then start the backend.");
        System.out.println("====================================================");
        System.out.println();
        System.out.println("set LCE_GOOGLE_CAL_ID=primary");
        System.out.println("set LCE_GOOGLE_CLIENT_ID=" + clientId);
        System.out.println("set LCE_GOOGLE_CLIENT_SECRET=" + clientSecret);
        System.out.println("set LCE_GOOGLE_REFRESH_TOKEN=" + refresh);
        System.out.println();
        System.out.println("Keep this window open until you have copied the values.");
        System.out.println("Treat the refresh token like a password: never commit it to GitHub.");
        System.out.println();

        // Also write a ready-to-use file so nothing is lost by closing the window.
        Path out = Path.of("google-calendar.env");
        Files.writeString(out, "LCE_GOOGLE_CAL_ID=primary\n"
                + "LCE_GOOGLE_CLIENT_ID=" + clientId + "\n"
                + "LCE_GOOGLE_CLIENT_SECRET=" + clientSecret + "\n"
                + "LCE_GOOGLE_REFRESH_TOKEN=" + refresh + "\n");
        System.out.println("Saved to " + out.toAbsolutePath());
        System.out.println("(.gitignore already excludes this file - do not commit it.)");
        System.out.println();
    }

    /** Starts a loopback listener and blocks until Google redirects back with the code. */
    private static String waitForCode() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
        CountDownLatch latch = new CountDownLatch(1);
        String[] code = {null};
        String[] error = {null};

        server.createContext("/oauth2callback", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            if (query != null) {
                for (String pair : query.split("&")) {
                    String[] kv = pair.split("=", 2);
                    if (kv.length == 2 && kv[0].equals("code")) code[0] = decode(kv[1]);
                    if (kv.length == 2 && kv[0].equals("error")) error[0] = decode(kv[1]);
                }
            }
            String html = "<html><body style=\"font-family:Segoe UI,Arial,sans-serif;padding:40px\">"
                    + "<h2>" + (code[0] != null ? "Authorised" : "Authorisation failed") + "</h2>"
                    + "<p>" + (code[0] != null
                        ? "You can close this tab and return to the terminal window."
                        : "Error: " + error[0]) + "</p></body></html>";
            byte[] body = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
            latch.countDown();
        });
        server.start();
        System.out.println("   Waiting for the browser to come back (up to 5 minutes)...");
        boolean arrived = latch.await(5, TimeUnit.MINUTES);
        server.stop(0);

        if (!arrived) {
            System.out.println("[ERROR] No response from Google within 5 minutes. Run the helper again.");
            System.exit(1);
        }
        if (code[0] == null) {
            System.out.println("[ERROR] Google returned an error: " + error[0]);
            System.exit(1);
        }
        return code[0];
    }

    /* ----------------------------- small helpers ----------------------------- */

    private static String read(String envKey, String fromArgs, String label) throws Exception {
        if (fromArgs != null && !fromArgs.isBlank()) return fromArgs.trim();
        String fromEnv = System.getenv(envKey);
        if (fromEnv != null && !fromEnv.isBlank()) return fromEnv.trim();
        System.out.print(label + ": ");
        System.out.flush();
        String line = System.console() != null
                ? System.console().readLine()
                : new java.io.BufferedReader(new java.io.InputStreamReader(System.in)).readLine();
        if (line == null || line.isBlank()) {
            System.out.println("[ERROR] A value is required.");
            System.exit(1);
        }
        return line.trim();
    }

    private static String field(String json, String name) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String decode(String s) {
        return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}
