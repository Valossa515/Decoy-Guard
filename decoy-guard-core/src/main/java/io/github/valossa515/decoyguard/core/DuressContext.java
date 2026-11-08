package io.github.valossa515.decoyguard.core;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable snapshot of how a session must be served.
 *
 * @param sessionId   opaque session identifier, never the user's credential
 * @param mode        whether the session is real or decoy
 * @param trigger     what activated duress mode, {@code null} when NORMAL
 * @param activatedAt when duress mode was activated, {@code null} when NORMAL
 */
public record DuressContext(String sessionId, SessionMode mode, ActivationTrigger trigger, Instant activatedAt) {

    private static final DuressContext ANONYMOUS = new DuressContext(null, SessionMode.NORMAL, null, null);

    public DuressContext {
        Objects.requireNonNull(mode, "mode");
        if (mode == SessionMode.DURESS) {
            Objects.requireNonNull(sessionId, "sessionId is required in DURESS mode");
            Objects.requireNonNull(trigger, "trigger is required in DURESS mode");
            Objects.requireNonNull(activatedAt, "activatedAt is required in DURESS mode");
        }
    }

    public static DuressContext normal(String sessionId) {
        return new DuressContext(sessionId, SessionMode.NORMAL, null, null);
    }

    public static DuressContext duress(String sessionId, ActivationTrigger trigger, Instant activatedAt) {
        return new DuressContext(sessionId, SessionMode.DURESS, trigger, activatedAt);
    }

    /** Context used when no session could be identified. Always NORMAL. */
    public static DuressContext anonymous() {
        return ANONYMOUS;
    }

    public boolean isDuress() {
        return mode == SessionMode.DURESS;
    }
}
