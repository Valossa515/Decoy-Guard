package io.github.valossa515.decoyguard.spring;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Tells Decoy Guard which session an incoming request belongs to.
 * Replace the default bean to read a JWT claim, a session cookie, etc.
 */
@FunctionalInterface
public interface SessionIdExtractor {

    /** Returns the session id, or {@code null} when the request is anonymous. */
    String extract(HttpServletRequest request);
}
