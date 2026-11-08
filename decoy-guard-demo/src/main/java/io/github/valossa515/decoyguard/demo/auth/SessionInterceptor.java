package io.github.valossa515.decoyguard.demo.auth;

import io.github.valossa515.decoyguard.spring.SessionIdExtractor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Rejects /account calls without an open session.
 */
@Configuration(proxyBeanMethods = false)
public class SessionInterceptor implements WebMvcConfigurer {

    private final DemoSessions sessions;
    private final SessionIdExtractor sessionIdExtractor;

    public SessionInterceptor(DemoSessions sessions, SessionIdExtractor sessionIdExtractor) {
        this.sessions = sessions;
        this.sessionIdExtractor = sessionIdExtractor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (sessions.isOpen(sessionIdExtractor.extract(request))) {
                    return true;
                }
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return false;
            }
        }).addPathPatterns("/account/**");
    }
}
