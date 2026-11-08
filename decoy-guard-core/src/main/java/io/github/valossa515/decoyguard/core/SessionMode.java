package io.github.valossa515.decoyguard.core;

/**
 * How the current session is being served.
 */
public enum SessionMode {

    /** Real data, real side effects. */
    NORMAL,

    /** Decoy data, side effects held. Must be indistinguishable from NORMAL to anyone watching the screen. */
    DURESS
}
