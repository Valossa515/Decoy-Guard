package io.github.valossa515.decoyguard.core;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single-instance registry. Fine for tests and demos, not for clustered deployments.
 */
public class InMemoryDuressSessionRegistry implements DuressSessionRegistry {

    private final Map<String, DuressContext> sessions = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryDuressSessionRegistry() {
        this(Clock.systemUTC());
    }

    public InMemoryDuressSessionRegistry(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public DuressContext activate(String sessionId, ActivationTrigger trigger) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(trigger, "trigger");
        return sessions.computeIfAbsent(sessionId, id -> DuressContext.duress(id, trigger, clock.instant()));
    }

    @Override
    public DuressContext resolve(String sessionId) {
        if (sessionId == null) {
            return DuressContext.anonymous();
        }
        DuressContext context = sessions.get(sessionId);
        return context != null ? context : DuressContext.normal(sessionId);
    }

    @Override
    public void release(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }
}
