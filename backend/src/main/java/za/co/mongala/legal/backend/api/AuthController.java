package za.co.mongala.legal.backend.api;

import io.javalin.http.Context;
import za.co.mongala.legal.model.ActivityLog;
import za.co.mongala.legal.model.User;

import java.util.Map;

/**
 * POST /api/auth/login - credential check + JWT issue (OAuth2-style bearer token).
 * POST /api/auth/demo  - password-free login for screenshots/e2e (LCE_DEMO_AUTH=true).
 * GET  /api/me         - who am I.
 */
public class AuthController {

    private final ApiContext api;

    public AuthController(ApiContext api) {
        this.api = api;
    }

    public void login(Context ctx) {
        Map<String, String> body = ctx.bodyAsClass(Map.class);
        String email = body.get("email");
        String password = body.get("password");
        String lockKey = (email == null || email.isBlank()) ? ("ip:" + ctx.ip()) : email.trim();

        var preStatus = api.loginGuard.status(lockKey);
        if (preStatus.locked()) {
            long mins = Math.max(1, (preStatus.secondsUntilUnlock() + 59) / 60);
            ctx.header("Retry-After", String.valueOf(preStatus.secondsUntilUnlock()));
            ctx.status(429).json(Map.of(
                    "error", "Account temporarily locked after " + api.loginGuard.maxAttempts()
                            + " failed login attempts. Try again in " + mins + " min or contact an administrator.",
                    "locked", true,
                    "retryAfterSeconds", preStatus.secondsUntilUnlock()));
            return;
        }

        User user = api.users.verify(email == null ? "" : email, password == null ? "" : password);
        if (user == null) {
            if (email != null && api.users.isDisabled(email)) {
                ctx.status(403).json(Map.of("error", "Account disabled - access revoked by an administrator"));
                return;
            }
            var afterFail = api.loginGuard.recordFailure(lockKey);
            if (afterFail.locked()) {
                api.audit.warning(email == null || email.isBlank() ? "Unknown" : email,
                        "LOGIN_LOCKED",
                        "Account locked after " + api.loginGuard.maxAttempts() + " consecutive failed login attempts (IP " + ctx.ip() + ")");
                ctx.header("Retry-After", String.valueOf(afterFail.secondsUntilUnlock()));
                ctx.status(429).json(Map.of(
                        "error", "Too many failed login attempts (" + api.loginGuard.maxAttempts() + "/"
                                + api.loginGuard.maxAttempts() + "). Account locked for 15 minutes.",
                        "locked", true,
                        "remainingAttempts", 0,
                        "retryAfterSeconds", afterFail.secondsUntilUnlock()));
                return;
            }
            String remText = afterFail.remainingAttempts() == 1
                    ? " (1 attempt remaining before lockout)"
                    : " (" + afterFail.remainingAttempts() + " attempts remaining before lockout)";
            ctx.status(401).json(Map.of(
                    "error", "Invalid email or password" + remText,
                    "remainingAttempts", afterFail.remainingAttempts()));
            return;
        }
        api.loginGuard.reset(lockKey);
        String token = api.jwt.issue(user.getId(), user.getName(), user.getEmail(), user.getRole());
        api.audit.info(user.getName(), "LOGIN", user.getRole().getDisplayName() + " signed in");
        ctx.json(Map.of("token", token, "user", user));
    }

    public void demoLogin(Context ctx) {
        if (!api.cfg.demoAuth()) {
            ctx.status(403).json(Map.of("error", "Demo auth disabled - set LCE_DEMO_AUTH=true"));
            return;
        }
        Map<String, String> body = ctx.bodyAsClass(Map.class);
        User user = api.users.findByEmail(body.get("email"));
        if (user == null) {
            ctx.status(401).json(Map.of("error", "Unknown user"));
            return;
        }
        String token = api.jwt.issue(user.getId(), user.getName(), user.getEmail(), user.getRole());
        api.audit.info(user.getName(), "LOGIN", user.getRole().getDisplayName() + " signed in (demo auth)");
        ctx.json(Map.of("token", token, "user", user));
    }

    public void me(Context ctx) {
        var s = ApiContext.session(ctx);
        User user = api.users.findByEmail(s.email());
        ctx.json(user == null ? Map.of() : user);
    }
}
