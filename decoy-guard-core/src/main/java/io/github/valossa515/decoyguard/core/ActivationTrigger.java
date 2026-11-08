package io.github.valossa515.decoyguard.core;

/**
 * What caused a session to enter {@link SessionMode#DURESS}.
 */
public enum ActivationTrigger {

    /** The user authenticated with their duress PIN or duress biometric. */
    DURESS_CREDENTIAL,

    /** A risk signal (active phone call, screen mirroring, remote access app) switched the session. */
    RISK_SIGNAL,

    /** An operator or trusted contact switched the session. */
    MANUAL
}
