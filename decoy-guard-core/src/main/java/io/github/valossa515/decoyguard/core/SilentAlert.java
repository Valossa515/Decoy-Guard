package io.github.valossa515.decoyguard.core;

import java.time.Instant;
import java.util.Objects;

/**
 * Raised when a session enters duress mode. Delivered out of band (fraud team, trusted contact),
 * never through any channel visible on the user's screen.
 */
public record SilentAlert(String sessionId, ActivationTrigger trigger, Instant raisedAt) {

    public SilentAlert {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(raisedAt, "raisedAt");
    }

    public static SilentAlert of(DuressContext context) {
        if (!context.isDuress()) {
            throw new IllegalArgumentException("Silent alerts are only raised for DURESS sessions");
        }
        return new SilentAlert(context.sessionId(), context.trigger(), context.activatedAt());
    }
}
