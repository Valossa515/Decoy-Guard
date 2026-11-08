package io.github.valossa515.decoyguard.spring;

import io.github.valossa515.decoyguard.core.DuressContextHolder;
import io.github.valossa515.decoyguard.core.DuressSessionRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Resolves the session mode once per request and binds it to the serving thread.
 * Deliberately writes nothing to the response: the mode must never be observable by the client.
 */
public class DuressContextFilter extends OncePerRequestFilter {

    private final SessionIdExtractor sessionIdExtractor;
    private final DuressSessionRegistry registry;

    public DuressContextFilter(SessionIdExtractor sessionIdExtractor, DuressSessionRegistry registry) {
        this.sessionIdExtractor = sessionIdExtractor;
        this.registry = registry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        DuressContextHolder.set(registry.resolve(sessionIdExtractor.extract(request)));
        try {
            chain.doFilter(request, response);
        } finally {
            DuressContextHolder.clear();
        }
    }
}
