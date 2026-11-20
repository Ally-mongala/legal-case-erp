package za.co.mongala.legal.backend.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brute-force login protection: locks an account key after 3 consecutive failed
 * password attempts for a configurable cooldown window, with administrator unlock support.
 */
public class LoginGuard {

    public static final int DEFAULT_MAX_ATTEMPTS = 3;
    public static final Duration DEFAULT_LOCKOUT = Duration.ofMinutes(15);

    public record LockStatus(
            String key,
            boolean locked,
            int failedAttempts,
            int remainingAttempts,
            long secondsUntilUnlock
    ) {}

    private static final class Entry {
        int failures;
        Instant lockedUntil;
    }

    private final int maxAttempts;
    private final Duration lockoutDuration;
    private final ConcurrentHashMap<String, Entry> table = new ConcurrentHashMap<>();

    public LoginGuard() {
        this(DEFAULT_MAX_ATTEMPTS, DEFAULT_LOCKOUT);
    }

    public LoginGuard(int maxAttempts, Duration lockoutDuration) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.lockoutDuration = lockoutDuration == null ? DEFAULT_LOCKOUT : lockoutDuration;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    /** Checks current lock state before verifying credentials. */
    public LockStatus status(String rawKey) {
        String key = normaliseKey(rawKey);
        Entry e = table.get(key);
        if (e == null) {
            return new LockStatus(key, false, 0, maxAttempts, 0);
        }
        synchronized (e) {
            Instant now = Instant.now();
            if (e.lockedUntil != null) {
                if (now.isBefore(e.lockedUntil)) {
                    long secs = Math.max(1, Duration.between(now, e.lockedUntil).getSeconds());
                    return new LockStatus(key, true, e.failures, 0, secs);
                }
                // Lockout window expired -> reset automatically
                e.failures = 0;
                e.lockedUntil = null;
            }
            return new LockStatus(key, false, e.failures, Math.max(0, maxAttempts - e.failures), 0);
        }
    }

    /** Records a failed login attempt and locks the key on reaching maxAttempts (default 3). */
    public LockStatus recordFailure(String rawKey) {
        String key = normaliseKey(rawKey);
        Entry e = table.computeIfAbsent(key, k -> new Entry());
        synchronized (e) {
            Instant now = Instant.now();
            if (e.lockedUntil != null && now.isBefore(e.lockedUntil)) {
                long secs = Math.max(1, Duration.between(now, e.lockedUntil).getSeconds());
                return new LockStatus(key, true, e.failures, 0, secs);
            }
            if (e.lockedUntil != null && !now.isBefore(e.lockedUntil)) {
                e.failures = 0;
                e.lockedUntil = null;
            }
            e.failures++;
            if (e.failures >= maxAttempts) {
                e.lockedUntil = now.plus(lockoutDuration);
                return new LockStatus(key, true, e.failures, 0, Math.max(1, lockoutDuration.getSeconds()));
            }
            return new LockStatus(key, false, e.failures, Math.max(0, maxAttempts - e.failures), 0);
        }
    }

    /** Clears failures upon successful authentication or administrator unlock. */
    public void reset(String rawKey) {
        if (rawKey == null) return;
        table.remove(normaliseKey(rawKey));
    }

    public List<LockStatus> activeLocks() {
        List<LockStatus> out = new ArrayList<>();
        for (Map.Entry<String, Entry> kv : table.entrySet()) {
            LockStatus st = status(kv.getKey());
            if (st.locked()) out.add(st);
        }
        return out;
    }

    private static String normaliseKey(String raw) {
        if (raw == null || raw.isBlank()) return "_anonymous_";
        return raw.trim().toLowerCase(Locale.ROOT);
    }
}
