package io.github.valossa515.decoyguard.spring;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Default extractor: uses the opaque bearer token as the session id.
 */
public class BearerTokenSessionIdExtractor implements SessionIdExtractor {

    private static final String PREFIX = "Bearer ";

    @Override
    public String extract(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            return null;
        }
        String token = header.substring(PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
