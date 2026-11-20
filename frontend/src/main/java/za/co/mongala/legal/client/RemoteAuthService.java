package za.co.mongala.legal.client;

import za.co.mongala.legal.model.User;
import za.co.mongala.legal.service.IAuthService;

import java.util.Map;

/**
 * CONNECTED-mode authentication: POST /api/auth/login, keep the JWT in the
 * ApiClient, return the user to the UI.
 */
public class RemoteAuthService implements IAuthService {

    private final ApiClient api;

    public RemoteAuthService(ApiClient api) {
        this.api = api;
    }

    @Override
    public User authenticate(String email, String password) {
        Map<String, Object> resp = api.post("/api/auth/login",
                Map.of("email", email == null ? "" : email, "password", password == null ? "" : password),
                Map.class);
        Object token = resp.get("token");
        if (token == null) return null;
        api.setToken(token.toString());
        Object userNode = resp.get("user");
        if (userNode instanceof Map<?, ?> m) {
            return new User(
                    str(m.get("id")), str(m.get("name")), str(m.get("email")),
                    "", za.co.mongala.legal.model.Role.valueOf(str(m.get("role"))));
        }
        return null;
    }

    @Override
    public User findByEmail(String email) {
        return null; // session-based: the token defines the user in CONNECTED mode
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }
}
