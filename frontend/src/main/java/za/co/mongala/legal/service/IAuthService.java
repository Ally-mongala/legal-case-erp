package za.co.mongala.legal.service;

import za.co.mongala.legal.model.User;

/**
 * Authentication contract. DEMO mode validates in-memory credentials;
 * CONNECTED mode calls POST /api/auth/login and stores the JWT.
 */
public interface IAuthService {

    /** @return the authenticated user or null when credentials are invalid. */
    User authenticate(String email, String password);

    User findByEmail(String email);
}
