package za.co.mongala.legal.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Thin REST client for the LegalCase ERP backend (Assignment 2 section 11).
 * Holds the JWT after login and attaches it as a Bearer token to every request.
 */
public class ApiClient {

    public static final String DEFAULT_BASE_URL = "http://localhost:8080";

    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper mapper;

    private volatile String token;
    private volatile Runnable onUnauthorized;

    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public String baseUrl() { return baseUrl; }
    public void setToken(String token) { this.token = token; }
    public boolean hasToken() { return token != null && !token.isBlank(); }

    /** Called when the backend returns 401 (e.g. expired token) - the UI logs out. */
    public void setOnUnauthorized(Runnable r) { this.onUnauthorized = r; }

    public boolean ping() {
        try {
            return 200 == execute("GET", "/api/health", null, 0).statusCode();
        } catch (Exception e) {
            return false;
        }
    }

    /* ---------------- typed calls ---------------- */

    public <T> T get(String path, Class<T> type) {
        HttpResponse<String> res = execute("GET", path, null, 3);
        return parse(res, type);
    }

    public <T> T get(String path, TypeReference<T> type) {
        HttpResponse<String> res = execute("GET", path, null, 3);
        return parse(res, type);
    }

    public <T> T post(String path, Object body, Class<T> type) {
        HttpResponse<String> res = execute("POST", path, body, 3);
        return parse(res, type);
    }

    public <T> T post(String path, Object body, TypeReference<T> type) {
        HttpResponse<String> res = execute("POST", path, body, 3);
        return parse(res, type);
    }

    public <T> T put(String path, Object body, Class<T> type) {
        HttpResponse<String> res = execute("PUT", path, body, 3);
        return parse(res, type);
    }

    public <T> T patch(String path, Object body, Class<T> type) {
        HttpResponse<String> res = execute("PATCH", path, body, 3);
        return parse(res, type);
    }

    public <T> T delete(String path, Class<T> type) {
        HttpResponse<String> res = execute("DELETE", path, null, 3);
        return parse(res, type);
    }

    /** Downloads raw bytes (e.g. a shared document) with the auth token attached. */
    public byte[] downloadBytes(String path) {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .GET();
        withAuth(b);
        try {
            HttpResponse<byte[]> res = http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
            checkStatus(res.statusCode(), new String(res.body(), java.nio.charset.StandardCharsets.UTF_8));
            return res.body();
        } catch (java.io.IOException | InterruptedException e) {
            throw new IllegalStateException("Download failed: " + e.getMessage(), e);
        }
    }

    private void checkStatus(int code, String body) {
        if (code == 401 && onUnauthorized != null) {
            onUnauthorized.run();
            throw new IllegalStateException("Session expired - please sign in again.");
        }
        if (code >= 400) {
            String msg = body;
            try {
                var node = mapper.readTree(body);
                msg = node.path("error").asText(msg);
            } catch (Exception ignored) {}
            throw new IllegalStateException("API error " + code + ": " + msg);
        }
    }

    /** Multipart upload of a PDF/Word file (POST /api/cases/{id}/documents). */
    public Map<String, Object> uploadFile(String path, java.io.File file, String fieldName) {
        return uploadFile(path, file, fieldName, Map.of());
    }

    /** Multipart upload with extra form fields (e.g. POPIA consent - FR11). */
    public Map<String, Object> uploadFile(String path, java.io.File file, String fieldName,
                                          Map<String, String> extraFields) {
        try {
            String lower = file.getName().toLowerCase();
            String contentType = lower.endsWith(".pdf") ? "application/pdf"
                    : lower.endsWith(".docx") ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    : lower.endsWith(".doc") ? "application/msword" : "application/octet-stream";
            String boundary = "----LegalCaseERP" + System.currentTimeMillis();
            String dashes = "--" + boundary;
            StringBuilder payload = new StringBuilder();
            for (var e : extraFields.entrySet()) {
                payload.append(dashes).append("\r\n")
                        .append("Content-Disposition: form-data; name=\"").append(e.getKey()).append("\"\r\n\r\n")
                        .append(e.getValue()).append("\r\n");
            }
            payload.append(dashes).append("\r\n")
                    .append("Content-Disposition: form-data; name=\"").append(fieldName)
                    .append("\"; filename=\"").append(file.getName()).append("\"\r\n")
                    .append("Content-Type: ").append(contentType).append("\r\n\r\n");
            byte[] head = payload.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] foot = ("\r\n" + dashes + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] content = java.nio.file.Files.readAllBytes(file.toPath());

            byte[] body = new byte[head.length + content.length + foot.length];
            System.arraycopy(head, 0, body, 0, head.length);
            System.arraycopy(content, 0, body, head.length, content.length);
            System.arraycopy(foot, 0, body, head.length + content.length, foot.length);

            HttpRequest.Builder b = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body));
            withAuth(b);
            HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            checkStatus(res);
            return mapper.readValue(res.body(), new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Upload failed: " + e.getMessage(), e);
        }
    }

    /* ---------------- internals ---------------- */

    private HttpResponse<String> execute(String method, String path, Object body, int retries) {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30));
        if (body != null) {
            try {
                b.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            } catch (Exception e) {
                throw new IllegalStateException("Could not serialize request", e);
            }
        } else {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        }
        withAuth(b);
        try {
            HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            checkStatus(res);
            return res;
        } catch (java.io.IOException | InterruptedException e) {
            if (retries > 0) {
                try { Thread.sleep(400); } catch (InterruptedException ignored) {}
                return execute(method, path, body, retries - 1);
            }
            throw new IllegalStateException("Backend unreachable at " + baseUrl
                    + " - is the LegalCase ERP server running?", e);
        }
    }

    private void withAuth(HttpRequest.Builder b) {
        if (token != null) b.header("Authorization", "Bearer " + token);
    }

    private void checkStatus(HttpResponse<String> res) {
        if (res.statusCode() == 401 && onUnauthorized != null) {
            onUnauthorized.run();
            throw new IllegalStateException("Session expired - please sign in again.");
        }
        if (res.statusCode() >= 400) {
            String msg = res.body();
            try {
                var node = mapper.readTree(res.body());
                msg = node.path("error").asText(msg);
            } catch (Exception ignored) {}
            throw new IllegalStateException("API error " + res.statusCode() + ": " + msg);
        }
    }

    private <T> T parse(HttpResponse<String> res, Class<T> type) {
        try {
            if (type == String.class) return type.cast(res.body());
            if (res.body() == null || res.body().isBlank()) return null;
            return mapper.readValue(res.body(), type);
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse response: " + e.getMessage(), e);
        }
    }

    private <T> T parse(HttpResponse<String> res, TypeReference<T> type) {
        try {
            if (res.body() == null || res.body().isBlank()) return null;
            return mapper.readValue(res.body(), type);
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse response: " + e.getMessage(), e);
        }
    }
}
