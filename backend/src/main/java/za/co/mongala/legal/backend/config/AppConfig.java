package za.co.mongala.legal.backend.config;

/**
 * Runtime configuration read from environment variables (with sensible dev defaults).
 * Env                      Default
 * -------------------------
 * LCE_PORT                 8080   (falls back to PORT, which most cloud hosts inject)
 * LCE_DATA_DIR             ./data
 * LCE_JWT_SECRET           dev-only secret (override in production!)
 * LCE_ENCRYPTION_KEY       derived from LCE_JWT_SECRET (FR11 encryption at rest)
 * LCE_DATA_RESIDENCY       "ZA"  (FR11 data residency)
 * LCE_TOKEN_HOURS          12
 * LCE_DEMO_AUTH            false
 * LCE_GOOGLE_CAL_ID / refresh token / client id / client secret (optional)
 * LCE_GOOGLE_TOKEN_URL     https://oauth2.googleapis.com/token        (override for tests)
 * LCE_GOOGLE_API_BASE      https://www.googleapis.com/calendar/v3     (override for tests)
 */
public record AppConfig(int port, String dataDir, String jwtSecret, String encryptionKey,
                        String dataResidency, int tokenHours, boolean demoAuth,
                        String googleCalendarId, String googleRefreshToken,
                        String googleClientId, String googleClientSecret,
                        String dbUrl, String dbUser, String dbPassword,
                        String googleTokenUrl, String googleApiBase) {

    public static final String DEFAULT_GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    public static final String DEFAULT_GOOGLE_API_BASE = "https://www.googleapis.com/calendar/v3";

    public static AppConfig fromEnv() {
        String dataDir = env("LCE_DATA_DIR", "./data");
        String jwtSecret = env("LCE_JWT_SECRET", "dev-only-secret-change-me-219361629");
        return new AppConfig(
                // LCE_PORT wins; otherwise use PORT, which Render / Cloud Run / Railway / Koyeb inject
                Integer.parseInt(env("LCE_PORT", env("PORT", "8080"))),
                dataDir,
                jwtSecret,
                env("LCE_ENCRYPTION_KEY", jwtSecret + "-aes-key-2026"),
                env("LCE_DATA_RESIDENCY", "ZA"),
                Integer.parseInt(env("LCE_TOKEN_HOURS", "12")),
                Boolean.parseBoolean(env("LCE_DEMO_AUTH", "false")),
                env("LCE_GOOGLE_CAL_ID", null),
                env("LCE_GOOGLE_REFRESH_TOKEN", null),
                env("LCE_GOOGLE_CLIENT_ID", null),
                env("LCE_GOOGLE_CLIENT_SECRET", null),
                env("LCE_DB_URL", "jdbc:h2:file:" + dataDir + "/erpdb;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL"),
                env("LCE_DB_USER", "sa"),
                env("LCE_DB_PASSWORD", ""),
                env("LCE_GOOGLE_TOKEN_URL", DEFAULT_GOOGLE_TOKEN_URL),
                env("LCE_GOOGLE_API_BASE", DEFAULT_GOOGLE_API_BASE));
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }

    public boolean googleConfigured() {
        return googleCalendarId != null && googleRefreshToken != null
                && googleClientId != null && googleClientSecret != null;
    }

    /** True when some, but not all, of the four Google values are present. */
    public boolean googlePartiallyConfigured() {
        int present = 0;
        if (googleCalendarId != null) present++;
        if (googleRefreshToken != null) present++;
        if (googleClientId != null) present++;
        if (googleClientSecret != null) present++;
        return present > 0 && present < 4;
    }

    /** Names of the missing Google variables (empty when fully configured). */
    public java.util.List<String> missingGoogleVariables() {
        java.util.List<String> missing = new java.util.ArrayList<>();
        if (googleCalendarId == null) missing.add("LCE_GOOGLE_CAL_ID");
        if (googleRefreshToken == null) missing.add("LCE_GOOGLE_REFRESH_TOKEN");
        if (googleClientId == null) missing.add("LCE_GOOGLE_CLIENT_ID");
        if (googleClientSecret == null) missing.add("LCE_GOOGLE_CLIENT_SECRET");
        return missing;
    }
}
