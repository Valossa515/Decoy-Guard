package io.github.valossa515.decoyguard.core;

import java.util.Objects;

/**
 * Entry point for switching a session into duress mode.
 * Activates the session and raises exactly one {@link SilentAlert} per activation.
 * A failing alert channel never breaks the request: the decoy must keep working
 * even when the alert could not be delivered.
 */
public class DuressActivator {

    private static final System.Logger LOG = System.getLogger(DuressActivator.class.getName());

    private final DuressSessionRegistry registry;
    private final SilentAlertPublisher alertPublisher;

    public DuressActivator(DuressSessionRegistry registry, SilentAlertPublisher alertPublisher) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.alertPublisher = Objects.requireNonNull(alertPublisher, "alertPublisher");
    }

    public DuressContext activate(String sessionId, ActivationTrigger trigger) {
        boolean alreadyActive = registry.resolve(sessionId).isDuress();
        DuressContext context = registry.activate(sessionId, trigger);
        if (!alreadyActive) {
            try {
                alertPublisher.publish(SilentAlert.of(context));
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.ERROR, "Silent alert could not be delivered", e);
            }
        }
        return context;
    }
}
